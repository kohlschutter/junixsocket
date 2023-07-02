/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

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
 * IMPORTANT: On some platforms (e.g., Solaris, Illumos) you may need to re-apply a read timeout
 * (e.g., using {@link Socket#setSoTimeout(int)}) after obtaining the socket.
 * </p>
 * <p>
 * Note that you may also lose Java port information for {@link AFSocketAddress} implementations
 * that do not encode this information directly (such as {@link AFUNIXSocketAddress} and
 * {@link AFTIPCSocketAddress}).
 * </p>
 *
 * @author Christian Kohlschütter
 */
public final class FileDescriptorCast implements FileDescriptorAccess {
  private static final Map<Class<?>, CastingProviderMap> PRIMARY_TYPE_PROVIDERS_MAP = Collections
      .synchronizedMap(new HashMap<>());

  private static final Function<FileDescriptor, FileInputStream> FD_IS_PROVIDER = System
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

        final CastingProvider<AFSocket<A>> cpSocket = (fdc, desiredType) -> AFSocket.newInstance(
            config.socketConstructor(), (AFSocketFactory<A>) null, fdc.getFileDescriptor(),
            fdc.localPort, fdc.remotePort);
        final CastingProvider<AFServerSocket<A>> cpServerSocket = (fdc,
            desiredType) -> AFServerSocket.newInstance(config.serverSocketConstructor(), fdc
                .getFileDescriptor(), fdc.localPort, fdc.remotePort);

        addProvider(socketClass, cpSocket);
        addProvider(config.serverSocketClass(), cpServerSocket);

        addProvider(config.socketChannelClass(), (fdc, desiredType) -> cpSocket.provideAs(fdc,
            AFSocket.class).getChannel());
        addProvider(config.serverSocketChannelClass(), (fdc, desiredType) -> cpServerSocket
            .provideAs(fdc, AFServerSocket.class).getChannel());
      }
    });

    registerCastingProviders(datagramSocketClass, new CastingProviderMap() {

      @SuppressWarnings("null")
      @Override
      protected void addProviders() {
        addProviders(GLOBAL_PROVIDERS);

        final CastingProvider<AFDatagramSocket<A>> cpDatagramSocket = (fdc,
            desiredType) -> AFDatagramSocket.newInstance(config.datagramSocketConstructor(), fdc
                .getFileDescriptor(), fdc.localPort, fdc.remotePort);

        addProvider(datagramSocketClass, cpDatagramSocket);

        addProvider(config.datagramChannelClass(), (fdc, desiredType) -> cpDatagramSocket.provideAs(
            fdc, AFDatagramSocket.class).getChannel());
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

  /**
   * Creates a {@link FileDescriptorCast} using the given file descriptor.
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
    AFUNIXSocketAddress.addressFamily().getClass(); // trigger registration
    AFTIPCSocketAddress.addressFamily().getClass(); // trigger registration
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
}
