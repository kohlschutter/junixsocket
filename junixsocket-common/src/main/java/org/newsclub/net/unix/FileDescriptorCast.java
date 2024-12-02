/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Provides object-oriented access to file descriptors via {@link InputStream}, {@link Socket},
 * etc., depending on the file descriptor type.
 * <p>
 * Typical usage:
 * </p>
 * <pre><code>
 * FileDescriptor fd;
 *
 * // succeeds if fd refers to an AF_UNIX stream socket
 * AFUNIXSocket socket = FileDescriptorCast.using(fd).as(AFUNIXSocket.class);
 *
 * // succeeds if fd refers to an AF_UNIX datagram socket
 * AFUNIXDatagramChannel channel = FileDescriptorCast.using(fd).as(AFUNIXDatagramChannel.class);
 *
 * // always succeeds
 * InputStream in = FileDescriptorCast.using(fd).as(InputStream.class);
 * OutputStream in = FileDescriptorCast.using(fd).as(OutputStream.class);
 * </code></pre>
 * <p>
 * <b>Important notes</b>
 * <ol>
 * <li>On some platforms (e.g., Solaris, Illumos) you may need to re-apply a read timeout (e.g.,
 * using {@link Socket#setSoTimeout(int)}) after obtaining the socket.</li>
 * <li>You may lose Java port information for {@link AFSocketAddress} implementations that do not
 * encode this information directly (such as {@link AFUNIXSocketAddress} and
 * {@link AFTIPCSocketAddress}).</li>
 * <li>The "blocking" state of a socket may be forcibly changed to "blocking" when performing the
 * cast, especially when casting to {@link Socket}, {@link DatagramSocket} or {@link ServerSocket}
 * and any of their subclasses where "blocking" is the expected state.</li>
 * <li>When calling {@link #using(FileDescriptor)} for a {@link FileDescriptor} obtained from
 * another socket or other resource in the same JVM (i.e., not from another process), especially for
 * sockets provided by junixsocket itself, there is a chance that the garbage collector may clean up
 * the original socket at an opportune moment, thereby closing the resource underlying the shared
 * {@link FileDescriptor} prematurely.
 * <p>
 * This is considered an edge-case, and deliberately not handled automatically for performance and
 * portability reasons: We would have to do additional reference counting on all FileDescriptor
 * instances, either through patching {@code FileCleanable} or a shared data structure.
 * <p>
 * The issue can be prevented by keeping a reference to the original object, such as keeping it in
 * an enclosing try-with-resources block or as a member variable, for example. Alternatively, using
 * a "duplicate" file descriptor (via {@link #duplicating(FileDescriptor)}) circumvents this
 * problem, at the cost of using additional system resources.</li>
 * <li>As a consequence of the previous point: For {@link #using(FileDescriptor)}: when casting file
 * descriptors that belong to a junixsocket-controlled sockets, the target socket is configured in a
 * way such that garbage collection will not automatically close the target's underlying file
 * descriptor (but still potentially any file descriptors received from other processes via
 * ancillary messages).</li>
 * <li>The same restrictions as for {@link #using(FileDescriptor)} apply to
 * {@link #unsafeUsing(int)} as well.</li>
 * </ol>
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class FileDescriptorCast implements FileDescriptorAccess {
  private static final Map<Class<?>, CastingProviderMap> PRIMARY_TYPE_PROVIDERS_MAP = Collections
      .synchronizedMap(new HashMap<>());

  private static final AFFunction<FileDescriptor, FileInputStream> FD_IS_PROVIDER = System
      .getProperty("osv.version") != null ? LenientFileInputStream::new : FileInputStream::new;

  private static final CastingProviderMap GLOBAL_PROVIDERS_FINAL = new CastingProviderMap() {

    @Override
    protected void addProviders() {
      // FileDescriptor and Object cannot be overridden
      addProvider(FileDescriptor.class, new CastingProvider<FileDescriptor>() {
        @Override
        public FileDescriptor provideAs(FileDescriptorCast fdc,
            Class<? super FileDescriptor> desiredType) throws IOException {
          return fdc.getFileDescriptor();
        }
      });
    }
  };

  private static final CastingProviderMap GLOBAL_PROVIDERS = new CastingProviderMap() {
    @Override
    protected void addProviders() {
      addProvider(WritableByteChannel.class, new CastingProvider<WritableByteChannel>() {
        @SuppressWarnings("resource")
        @Override
        public WritableByteChannel provideAs(FileDescriptorCast fdc,
            Class<? super WritableByteChannel> desiredType) throws IOException {
          return new FileOutputStream(fdc.getFileDescriptor()).getChannel();
        }
      });
      addProvider(ReadableByteChannel.class, new CastingProvider<ReadableByteChannel>() {
        @Override
        public ReadableByteChannel provideAs(FileDescriptorCast fdc,
            Class<? super ReadableByteChannel> desiredType) throws IOException {
          return FD_IS_PROVIDER.apply(fdc.getFileDescriptor()).getChannel();
        }
      });

      addProvider(FileChannel.class, new CastingProvider<FileChannel>() {
        @Override
        public FileChannel provideAs(FileDescriptorCast fdc, Class<? super FileChannel> desiredType)
            throws IOException {
          return RAFChannelProvider.getFileChannel(fdc.getFileDescriptor());
        }
      });

      addProvider(FileChannelSupplier.ReadOnly.class, (fdc,
          desiredType) -> new FileChannelSupplier.ReadOnly(fdc.getFileDescriptor()));

      addProvider(FileChannelSupplier.WriteOnly.class, (fdc,
          desiredType) -> new FileChannelSupplier.WriteOnly(fdc.getFileDescriptor()));

      addProvider(FileChannelSupplier.ReadWrite.class, (fdc,
          desiredType) -> new FileChannelSupplier.ReadWrite(fdc.getFileDescriptor()));

      addProvider(FileOutputStream.class, new CastingProvider<FileOutputStream>() {
        @Override
        public FileOutputStream provideAs(FileDescriptorCast fdc,
            Class<? super FileOutputStream> desiredType) throws IOException {
          return new FileOutputStream(fdc.getFileDescriptor());
        }
      });

      addProvider(FileInputStream.class, new CastingProvider<FileInputStream>() {
        @Override
        public FileInputStream provideAs(FileDescriptorCast fdc,
            Class<? super FileInputStream> desiredType) throws IOException {
          return FD_IS_PROVIDER.apply(fdc.getFileDescriptor());
        }
      });
      addProvider(FileDescriptor.class, new CastingProvider<FileDescriptor>() {
        @Override
        public FileDescriptor provideAs(FileDescriptorCast fdc,
            Class<? super FileDescriptor> desiredType) throws IOException {
          return fdc.getFileDescriptor();
        }
      });
      addProvider(Integer.class, new CastingProvider<Integer>() {
        @Override
        public Integer provideAs(FileDescriptorCast fdc, Class<? super Integer> desiredType)
            throws IOException {
          FileDescriptor fd = fdc.getFileDescriptor();
          int val = fd.valid() ? NativeUnixSocket.getFD(fd) : -1;
          if (val == -1) {
            throw new IOException("Not a valid file descriptor");
          }
          return val;
        }
      });

      if (AFSocket.supports(AFSocketCapability.CAPABILITY_FD_AS_REDIRECT)) {
        addProvider(Redirect.class, new CastingProvider<Redirect>() {
          @Override
          public Redirect provideAs(FileDescriptorCast fdc, Class<? super Redirect> desiredType)
              throws IOException {

            Redirect red = NativeUnixSocket.initRedirect(fdc.getFileDescriptor());
            if (red == null) {
              throw new ClassCastException("Cannot access file descriptor as " + desiredType);
            }
            return red;
          }
        });
      }
    }
  };

  private static final int FD_IN = getFdIfPossible(FileDescriptor.in);
  private static final int FD_OUT = getFdIfPossible(FileDescriptor.out);
  private static final int FD_ERR = getFdIfPossible(FileDescriptor.err);

  static {
    registerGenericSocketSupport();
  }

  private final FileDescriptor fdObj;

  private int localPort = 0;
  private int remotePort = 0;

  private final CastingProviderMap cpm;

  private FileDescriptorCast(FileDescriptor fdObj, CastingProviderMap cpm) {
    this.fdObj = Objects.requireNonNull(fdObj);
    this.cpm = Objects.requireNonNull(cpm);
  }

  private static int getFdIfPossible(FileDescriptor fd) {
    if (!NativeUnixSocket.isLoaded()) {
      return -1;
    }
    try {
      if (!fd.valid()) {
        return -1;
      }
      return NativeUnixSocket.getFD(fd);
    } catch (IOException e) {
      return -1;
    }
  }

  private static void registerCastingProviders(Class<?> primaryType, CastingProviderMap cpm) {
    Objects.requireNonNull(primaryType);
    CastingProviderMap prev;
    if ((prev = PRIMARY_TYPE_PROVIDERS_MAP.put(primaryType, cpm)) != null) {
      PRIMARY_TYPE_PROVIDERS_MAP.put(primaryType, prev);
      throw new IllegalStateException("Already registered: " + primaryType);
    }
  }

  static <A extends AFSocketAddress> void registerCastingProviders(
      AFAddressFamilyConfig<A> config) {
    Class<? extends AFSocket<A>> socketClass = config.socketClass();
    Class<? extends AFDatagramSocket<A>> datagramSocketClass = config.datagramSocketClass();

    registerCastingProviders(socketClass, new CastingProviderMap() {

      @SuppressWarnings("null")
      @Override
      protected void addProviders() {
        addProviders(GLOBAL_PROVIDERS);

        final CastingProviderSocketOrChannel<AFSocket<A>> cpSocketOrChannel = (fdc, desiredType,
            isChannel) -> reconfigure(isChannel, AFSocket.newInstance(config.socketConstructor(),
                (AFSocketFactory<A>) null, fdc.getFileDescriptor(), fdc.localPort, fdc.remotePort));
        final CastingProviderSocketOrChannel<AFServerSocket<A>> cpServerSocketOrChannel = (fdc,
            desiredType, isChannel) -> reconfigure(isChannel, AFServerSocket.newInstance(config
                .serverSocketConstructor(), fdc.getFileDescriptor(), fdc.localPort,
                fdc.remotePort));

        registerGenericSocketProviders();

        addProvider(socketClass, (fdc, desiredType) -> cpSocketOrChannel.provideAs(fdc, desiredType,
            false));
        addProvider(config.serverSocketClass(), (fdc, desiredType) -> cpServerSocketOrChannel
            .provideAs(fdc, desiredType, false));
        addProvider(config.socketChannelClass(), (fdc, desiredType) -> cpSocketOrChannel.provideAs(
            fdc, AFSocket.class, true).getChannel());
        addProvider(config.serverSocketChannelClass(), (fdc, desiredType) -> cpServerSocketOrChannel
            .provideAs(fdc, AFServerSocket.class, true).getChannel());
      }
    });

    registerCastingProviders(datagramSocketClass, new CastingProviderMap() {

      @SuppressWarnings("null")
      @Override
      protected void addProviders() {
        addProviders(GLOBAL_PROVIDERS);

        final CastingProviderSocketOrChannel<AFDatagramSocket<A>> cpDatagramSocketOrChannel = (fdc,
            desiredType, isChannel) -> reconfigure(isChannel, AFDatagramSocket.newInstance(config
                .datagramSocketConstructor(), fdc.getFileDescriptor(), fdc.localPort,
                fdc.remotePort));

        registerGenericDatagramSocketProviders();

        addProvider(datagramSocketClass, (fdc, desiredType) -> cpDatagramSocketOrChannel.provideAs(
            fdc, desiredType, false));
        addProvider(config.datagramChannelClass(), (fdc, desiredType) -> cpDatagramSocketOrChannel
            .provideAs(fdc, AFDatagramSocket.class, true).getChannel());
      }
    });
  }

  private abstract static class CastingProviderMap {
    private final Map<Class<?>, CastingProvider<?>> providers = new HashMap<>();
    private final Set<Class<?>> classes = Collections.unmodifiableSet(providers.keySet());

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    protected CastingProviderMap() {
      addProviders();

      addProviders(GLOBAL_PROVIDERS_FINAL);
    }

    @SuppressWarnings("null")
    protected void registerGenericSocketProviders() {
      final CastingProviderSocketOrChannel<AFSocket<AFGenericSocketAddress>> cpSocketOrChannelGeneric =
          (fdc, desiredType, isChannel) -> reconfigure(isChannel, AFSocket.newInstance(
              AFGenericSocket::new, (AFSocketFactory<AFGenericSocketAddress>) null, fdc
                  .getFileDescriptor(), fdc.localPort, fdc.remotePort));
      final CastingProviderSocketOrChannel<AFServerSocket<AFGenericSocketAddress>> cpServerSocketOrChannelGeneric =
          (fdc, desiredType, isChannel) -> reconfigure(isChannel, AFServerSocket.newInstance(
              AFGenericServerSocket::new, fdc.getFileDescriptor(), fdc.localPort, fdc.remotePort));

      addProvider(AFGenericSocket.class, (fdc, desiredType) -> cpSocketOrChannelGeneric.provideAs(
          fdc, desiredType, false));
      addProvider(AFGenericServerSocket.class, (fdc, desiredType) -> cpServerSocketOrChannelGeneric
          .provideAs(fdc, desiredType, false));
      addProvider(AFGenericSocketChannel.class, (fdc, desiredType) -> cpSocketOrChannelGeneric
          .provideAs(fdc, AFSocket.class, true).getChannel());
      addProvider(AFGenericServerSocketChannel.class, (fdc,
          desiredType) -> cpServerSocketOrChannelGeneric.provideAs(fdc, AFServerSocket.class, true)
              .getChannel());
    }

    @SuppressWarnings("null")
    protected void registerGenericDatagramSocketProviders() {
      final CastingProviderSocketOrChannel<AFDatagramSocket<AFGenericSocketAddress>> cpDatagramSocketOrChannelGeneric =
          (fdc, desiredType, isChannel) -> reconfigure(isChannel, AFDatagramSocket.newInstance(
              AFGenericDatagramSocket::new, fdc.getFileDescriptor(), fdc.localPort,
              fdc.remotePort));

      addProvider(AFDatagramSocket.class, (fdc, desiredType) -> cpDatagramSocketOrChannelGeneric
          .provideAs(fdc, desiredType, false));
      addProvider(AFDatagramChannel.class, (fdc, desiredType) -> cpDatagramSocketOrChannelGeneric
          .provideAs(fdc, AFDatagramSocket.class, true).getChannel());
    }

    protected abstract void addProviders();

    protected final <T> void addProvider(Class<T> type, CastingProvider<?> cp) {
      Objects.requireNonNull(type);

      addProvider0(type, cp);
    }

    private void addProvider0(Class<?> type, CastingProvider<?> cp) {
      if (providers.put(type, cp) != cp) { // NOPMD
        for (Class<?> cl : type.getInterfaces()) {
          addProvider0(cl, cp);
        }
        Class<?> scl = type.getSuperclass();
        if (scl != null) {
          addProvider0(scl, cp);
        }
      }
    }

    protected final void addProviders(CastingProviderMap other) {
      if (other == null || other == this) { // NOPMD
        return;
      }
      this.providers.putAll(other.providers);
    }

    @SuppressWarnings("unchecked")
    public <T> CastingProvider<? extends T> get(Class<T> desiredType) {
      return (CastingProvider<? extends T>) providers.get(desiredType);
    }
  }

  @FunctionalInterface
  private interface CastingProvider<T> {
    T provideAs(FileDescriptorCast fdc, Class<? super T> desiredType) throws IOException;
  }

  @FunctionalInterface
  private interface CastingProviderSocketOrChannel<T> {
    T provideAs(FileDescriptorCast fdc, Class<? super T> desiredType, boolean isChannel)
        throws IOException;
  }

  /**
   * Creates a {@link FileDescriptorCast} using the given file descriptor.
   * <p>
   * Note that if any resource that also references this {@link FileDescriptor} is
   * garbage-collected, the cleanup for that object may close the referenced {@link FileDescriptor},
   * thereby resulting in premature connection losses, etc. See {@link #duplicating(FileDescriptor)}
   * for a solution to this problem.
   *
   * @param fdObj The file descriptor.
   * @return The {@link FileDescriptorCast} instance.
   * @throws IOException on error, especially if the given file descriptor is invalid or
   *           unsupported.
   */
  public static FileDescriptorCast using(FileDescriptor fdObj) throws IOException {
    if (!fdObj.valid()) {
      throw new IOException("Not a valid file descriptor");
    }

    Class<?> primaryType = NativeUnixSocket.isLoaded() ? NativeUnixSocket.primaryType(fdObj) : null;
    if (primaryType == null) {
      primaryType = FileDescriptor.class;
    }

    triggerInit();

    CastingProviderMap map = PRIMARY_TYPE_PROVIDERS_MAP.get(primaryType);
    return new FileDescriptorCast(fdObj, map == null ? GLOBAL_PROVIDERS : map);
  }

  /**
   * Creates a {@link FileDescriptorCast} using a <em>duplicate</em> of the given file descriptor.
   * <p>
   * Duplicating a file descriptor is performed at the system-level, which means an additional file
   * descriptor pointing to the same resource as the original is created by the operating system.
   * <p>
   * The advantage of using {@link #duplicating(FileDescriptor)} over {@link #using(FileDescriptor)}
   * is that neither implicit garbage collection nor an explicit call to {@link Closeable#close()}
   * on a resource owning the original {@link FileDescriptor} affects the availability of the
   * resource from the target of the cast.
   *
   * @param fdObj The file descriptor to duplicate.
   * @return The {@link FileDescriptorCast} instance.
   * @throws IOException on error, especially if the given file descriptor is invalid or
   *           unsupported, or if duplicating fails or is unsupported.
   */
  public static FileDescriptorCast duplicating(FileDescriptor fdObj) throws IOException {
    if (!fdObj.valid()) {
      throw new IOException("Not a valid file descriptor");
    }

    FileDescriptor duplicate = NativeUnixSocket.duplicate(fdObj, new FileDescriptor());
    if (duplicate == null) {
      throw new IOException("Could not duplicate file descriptor");
    }
    return using(duplicate);
  }

  /**
   * Creates a {@link FileDescriptorCast} using the given native file descriptor value.
   * <p>
   * This method is inherently unsafe as it may
   * <ol>
   * <li>make assumptions on the internal system representation of a file descriptor (which differs
   * between Windows and Unix, for example).</li>
   * <li>provide access to resources that are otherwise not accessible</li>
   * </ol>
   * <p>
   * Note that attempts are made to reuse {@link FileDescriptor#in}, {@link FileDescriptor#out}, and
   * {@link FileDescriptor#err}, respectively.
   *
   * @param fd The system-native file descriptor value.
   * @return The {@link FileDescriptorCast} instance.
   * @throws IOException on error, especially if the given file descriptor is invalid or
   *           unsupported, or when "unsafe" operations are unavailable or manually disabled for the
   *           current environment.
   */
  @Unsafe
  public static FileDescriptorCast unsafeUsing(int fd) throws IOException {
    AFSocket.ensureUnsafeSupported();

    FileDescriptor fdObj;
    if (fd == -1) {
      throw new IOException("Not a valid file descriptor");
    } else if (fd == FD_IN) {
      fdObj = FileDescriptor.in;
    } else if (fd == FD_OUT) {
      fdObj = FileDescriptor.out;
    } else if (fd == FD_ERR) {
      fdObj = FileDescriptor.err;
    } else {
      fdObj = null;
    }

    if (fdObj != null) {
      int check = getFdIfPossible(fdObj);
      if (fd == check) {
        return using(fdObj);
      }
    }

    fdObj = new FileDescriptor();
    NativeUnixSocket.initFD(fdObj, fd);

    return using(fdObj);
  }

  private static void triggerInit() {
    for (AFAddressFamily<?> family : new AFAddressFamily<?>[] {
        AFUNIXSocketAddress.addressFamily(), //
        AFTIPCSocketAddress.addressFamily(), //
        AFVSOCKSocketAddress.addressFamily(), //
        AFSYSTEMSocketAddress.addressFamily(), //
    }) {
      Objects.requireNonNull(family.getClass()); // trigger init
    }
  }

  /**
   * Registers the given port number as the "local port" for this file descriptor.
   *
   * Important: This only changes the state of this instance. The actual file descriptor is not
   * affected.
   *
   * @param port The port to assign to (must be &gt;= 0).
   * @return This instance.
   */
  public FileDescriptorCast withLocalPort(int port) {
    if (port < 0) {
      throw new IllegalArgumentException();
    }
    this.localPort = port;
    return this;
  }

  /**
   * Registers the given port number as the "remote port" for this file descriptor.
   *
   * Important: This only changes the state of this instance. The actual file descriptor is not
   * affected.
   *
   * @param port The port to assign to (must be &gt;= 0).
   * @return This instance.
   */
  public FileDescriptorCast withRemotePort(int port) {
    if (port < 0) {
      throw new IllegalArgumentException();
    }
    this.remotePort = port;
    return this;
  }

  /**
   * Casts this instance to the desired type.
   *
   * @param <K> The desired type.
   * @param desiredType The class of the desired type.
   * @return s An instance of the desired type.
   * @throws IOException if there was a problem while casting.
   * @throws ClassCastException if the cast cannot be legally made.
   * @see #availableTypes()
   * @see #isAvailable(Class)
   */
  @SuppressWarnings("PMD.ShortMethodName")
  public @NonNull <K> K as(Class<K> desiredType) throws IOException {
    Objects.requireNonNull(desiredType);

    CastingProvider<? extends K> provider = cpm.get(desiredType);
    if (provider != null) {
      K obj = desiredType.cast(provider.provideAs(this, desiredType));
      Objects.requireNonNull(obj);
      return obj;
    } else {
      throw new ClassCastException("Cannot access file descriptor as " + desiredType);
    }
  }

  /**
   * Checks if the instance can be cast as the given desired type (using {@link #as(Class)}).
   *
   * @param desiredType The class of the desired type.
   * @return {@code true} if the cast can be made.
   * @throws IOException on error.
   * @see #as(Class)
   */
  public boolean isAvailable(Class<?> desiredType) throws IOException {
    return cpm.providers.containsKey(desiredType);
  }

  /**
   * Returns a collection of available types this instance can be cast to (using
   * {@link #as(Class)}).
   *
   * @return The collection of available types.
   */
  public Set<Class<?>> availableTypes() {
    return cpm.classes;
  }

  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public FileDescriptor getFileDescriptor() {
    return fdObj;
  }

  private static final class LenientFileInputStream extends FileInputStream {
    private LenientFileInputStream(FileDescriptor fdObj) {
      super(fdObj);
    }

    @Override
    public int available() throws IOException {
      try {
        return super.available();
      } catch (IOException e) {
        String msg = e.getMessage();
        if ("Invalid seek".equals(msg)) {
          // OSv may not like FileInputStream#availabe() on pipe fds.
          return 0;
        }
        throw e;
      }
    }
  }

  /**
   * Add support for otherwise unsupported sockets.
   */
  private static void registerGenericSocketSupport() {
    registerCastingProviders(Socket.class, new CastingProviderMap() {

      @Override
      protected void addProviders() {
        addProviders(GLOBAL_PROVIDERS);

        registerGenericSocketProviders();
      }
    });

    registerCastingProviders(DatagramSocket.class, new CastingProviderMap() {
      @Override
      protected void addProviders() {
        addProviders(GLOBAL_PROVIDERS);

        registerGenericDatagramSocketProviders();
      }
    });
  }

  @SuppressWarnings("null")
  private static <S extends AFSocket<?>> S reconfigure(boolean isChannel, S socket)
      throws IOException {
    reconfigure(isChannel, socket.getChannel());
    socket.getAFImpl().getCore().disableCleanFd();
    return socket;
  }

  @SuppressWarnings("null")
  private static <S extends AFServerSocket<?>> S reconfigure(boolean isChannel, S socket)
      throws IOException {
    reconfigure(isChannel, socket.getChannel());
    socket.getAFImpl().getCore().disableCleanFd();
    return socket;
  }

  @SuppressWarnings("null")
  private static <S extends AFDatagramSocket<?>> S reconfigure(boolean isChannel, S socket)
      throws IOException {
    reconfigure(isChannel, socket.getChannel());
    socket.getAFImpl().getCore().disableCleanFd();
    return socket;
  }

  /**
   * Reconfigures the Java-side of the socket/socket channel such that its state is compatible with
   * the native socket's state. This is necessary to properly configure blocking/non-blocking state,
   * as that is cached on the Java side.
   * <p>
   * If {@code isChannel} is false, then we want to cast to a {@link Socket}, {@link DatagramSocket}
   * or {@link ServerSocket}, which means blocking I/O is desired. If the underlying native socket
   * is configured non-blocking, we need to reset the state to "blocking" accordingly.
   * <p>
   * If {@code isChannel} is true, then we want to cast to a {@link SocketChannel},
   * {@link DatagramChannel} or {@link ServerSocketChannel}, in which case the blocking state should
   * be preserved, if possible. It is then up to the user to check blocking state via
   * {@link AbstractSelectableChannel#isBlocking()} prior to using the socket.
   * <p>
   * Note that on Windows, it may be impossible to query the blocking state from an external socket,
   * so the state is always forcibly set to "blocking".
   *
   * @param <S> The type.
   * @param isChannel The desired cast type (socket=set to blocking, or channel=preserve state).
   * @param socketChannel The channel.
   * @throws IOException on error.
   */
  private static <@NonNull S extends AFSomeSocketChannel> void reconfigure(boolean isChannel,
      S socketChannel) throws IOException {
    if (isChannel) {
      reconfigureKeepBlockingState(socketChannel);
    } else {
      reconfigureSetBlocking(socketChannel);
    }
  }

  private static <@NonNull S extends AFSomeSocketChannel> void reconfigureKeepBlockingState(
      S socketChannel) throws IOException {
    int result = NativeUnixSocket.checkBlocking(socketChannel.getFileDescriptor());

    boolean blocking;
    switch (result) {
      case 0:
        blocking = false;
        break;
      case 1:
        blocking = true;
        break;
      case 2:
        // need to reconfigure/forcibly override any cached result -> set to blocking by default
        socketChannel.configureBlocking(false);
        socketChannel.configureBlocking(true);
        return;
      default:
        throw new OperationNotSupportedSocketException("Invalid blocking state");
    }

    socketChannel.configureBlocking(blocking);
  }

  private static <@NonNull S extends AFSomeSocketChannel> void reconfigureSetBlocking(
      S socketChannel) throws IOException {
    int result = NativeUnixSocket.checkBlocking(socketChannel.getFileDescriptor());

    switch (result) {
      case 0:
        // see below
        break;
      case 1:
        // already blocking, nothing to do
        return;
      case 2:
        // need to reconfigure/forcibly override any cached result -> set to blocking by default
        // see below
        break;
      default:
        throw new OperationNotSupportedSocketException("Invalid blocking state");
    }

    socketChannel.configureBlocking(false);
    socketChannel.configureBlocking(true);
  }
}
