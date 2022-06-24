/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.IllegalBlockingModeException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * The server part of a junixsocket socket.
 * 
 * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
 * @author Christian Kohlschütter
 */
public abstract class AFServerSocket<A extends AFSocketAddress> extends ServerSocket implements
    FileDescriptorAccess {
  private final AFSocketImpl<A> implementation;
  private @Nullable A boundEndpoint;
  private final Closeables closeables = new Closeables();
  private final AtomicBoolean created = new AtomicBoolean(false);
  private final AtomicBoolean deleteOnClose = new AtomicBoolean(true);
  private final AFServerSocketChannel<?> channel = newChannel();
  private @Nullable SocketAddressFilter bindFilter;

  /**
   * The constructor of the concrete subclass.
   * 
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   */
  public interface Constructor<A extends AFSocketAddress> {
    /**
     * Creates a new {@link AFServerSocket} instance.
     * 
     * @param fd The file descriptor.
     * @return The new instance.
     * @throws IOException on error.
     */
    @NonNull
    AFServerSocket<A> newInstance(FileDescriptor fd) throws IOException;
  }

  /**
   * Constructs a new, unconnected instance.
   * 
   * @throws IOException if the operation fails.
   */
  protected AFServerSocket() throws IOException {
    this(null);
  }

  /**
   * Constructs a new instance, optionally associated with the given file descriptor.
   * 
   * @param fdObj The file descriptor, or {@code null}.
   * @throws IOException if the operation fails.
   */
  protected AFServerSocket(FileDescriptor fdObj) throws IOException {
    super();
    this.implementation = newImpl(fdObj);
    NativeUnixSocket.initServerImpl(this, implementation);

    setReuseAddress(true);
  }

  /**
   * Creates a new AFServerSocketChannel for this socket.
   * 
   * @return The new instance.
   */
  protected abstract AFServerSocketChannel<?> newChannel();

  /**
   * Creates a new AFSocketImpl.
   * 
   * @param fdObj The file descriptor.
   * @return The new instance.
   * @throws IOException on error.
   */
  protected abstract AFSocketImpl<A> newImpl(FileDescriptor fdObj) throws IOException;

  /**
   * Creates a new AFServerSocket instance, using the given subclass constructor.
   * 
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @param instanceSupplier The subclass constructor.
   * @return The new instance.
   * @throws IOException on error.
   */
  protected static <A extends AFSocketAddress> AFServerSocket<A> newInstance(
      Constructor<A> instanceSupplier) throws IOException {
    return instanceSupplier.newInstance(null);
  }

  /**
   * Creates a new AFServerSocket instance, using the given subclass constructor.
   * 
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @param instanceSupplier The subclass constructor.
   * @param fdObj The file descriptor.
   * @param localPort The local port.
   * @param remotePort The remote port.
   * @return The new instance.
   * @throws IOException on error.
   */
  protected static <A extends AFSocketAddress> AFServerSocket<A> newInstance(
      Constructor<A> instanceSupplier, FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    if (fdObj == null) {
      return instanceSupplier.newInstance(null);
    }

    int status = NativeUnixSocket.socketStatus(fdObj);
    if (!fdObj.valid() || status == NativeUnixSocket.SOCKETSTATUS_INVALID) {
      throw new SocketException("Not a valid socket");
    }
    AFServerSocket<A> socket = instanceSupplier.newInstance(fdObj);
    socket.getAFImpl().updatePorts(localPort, remotePort);

    switch (status) {
      case NativeUnixSocket.SOCKETSTATUS_CONNECTED:
        throw new SocketException("Not a ServerSocket");
      case NativeUnixSocket.SOCKETSTATUS_BOUND:
        socket.bind(AFSocketAddress.INTERNAL_DUMMY_BIND);

        socket.setBoundEndpoint(AFSocketAddress.getSocketAddress(fdObj, false, localPort, socket
            .addressFamily()));
        break;
      case NativeUnixSocket.SOCKETSTATUS_UNKNOWN:
        break;
      default:
        throw new IllegalStateException("Invalid socketStatus response: " + status);
    }

    socket.getAFImpl().setSocketAddress(socket.getLocalSocketAddress());
    return socket;
  }

  /**
   * Returns a new {@link ServerSocket} that is bound to the given {@link AFSocketAddress}.
   * 
   * @param instanceSupplier The constructor of the concrete subclass.
   * @param addr The socket file to bind to.
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  protected static <A extends AFSocketAddress> AFServerSocket<A> bindOn(
      Constructor<A> instanceSupplier, final AFSocketAddress addr) throws IOException {
    AFServerSocket<A> socket = instanceSupplier.newInstance(null);
    socket.bind(addr);
    return socket;
  }

  /**
   * Returns a new {@link ServerSocket} that is bound to the given {@link AFSocketAddress}.
   * 
   * @param instanceSupplier The constructor of the concrete subclass.
   * @param addr The socket file to bind to.
   * @param deleteOnClose If {@code true}, the socket file (if the address points to a file) will be
   *          deleted upon {@link #close}.
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  protected static <A extends AFSocketAddress> AFServerSocket<A> bindOn(
      Constructor<A> instanceSupplier, final A addr, boolean deleteOnClose) throws IOException {
    AFServerSocket<A> socket = instanceSupplier.newInstance(null);
    socket.bind(addr);
    socket.setDeleteOnClose(deleteOnClose);
    return socket;
  }

  /**
   * Returns a new, <em>unbound</em> {@link ServerSocket} that will always bind to the given
   * address, regardless of any socket address used in a call to <code>bind</code>.
   * 
   * @param instanceSupplier The constructor of the concrete subclass.
   * @param forceAddr The address to use.
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @return The new, yet unbound {@link AFServerSocket}.
   * @throws IOException if an exception occurs.
   */
  protected static <A extends AFSocketAddress> AFServerSocket<A> forceBindOn(
      Constructor<A> instanceSupplier, final A forceAddr) throws IOException {
    AFServerSocket<A> socket = instanceSupplier.newInstance(null);
    return socket.forceBindAddress(forceAddr);
  }

  /**
   * Forces the address to be used for any subsequent call to {@link #bind(SocketAddress)} to be the
   * given one, regardless of what'll be passed to {@link #bind(SocketAddress, int)}, but doesn't
   * bind yet.
   * 
   * @param endpoint The forced endpoint address.
   * @return This {@link AFServerSocket}.
   */
  public final AFServerSocket<A> forceBindAddress(SocketAddress endpoint) {
    return bindHook((SocketAddress orig) -> {
      return orig == null ? null : endpoint;
    });
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void bind(SocketAddress endpoint, int backlog) throws IOException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    boolean bindErrorOk;
    if (bindFilter != null) {
      endpoint = bindFilter.apply(endpoint);
      bindErrorOk = endpoint != null && isBound();
    } else {
      bindErrorOk = false;
    }

    if (!(endpoint instanceof AFSocketAddress)) {
      throw new IllegalArgumentException("Can only bind to endpoints of type "
          + AFSocketAddress.class.getName() + ": " + endpoint);
    }
    A endpointCast;
    try {
      endpointCast = (A) endpoint;
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Can only bind to specific endpoints", e);
    }

    try {
      getAFImpl().bind(endpoint, getReuseAddress() ? NativeUnixSocket.BIND_OPT_REUSE : 0);
    } catch (SocketException e) {
      if (bindErrorOk) {
        // force-binding an address could mean double-binding the same address, that's OK.
        return;
      } else {
        throw e;
      }
    }
    setBoundEndpoint(endpointCast);

    if (endpoint == AFSocketAddress.INTERNAL_DUMMY_BIND) { // NOPMD
      return;
    }

    implementation.listen(backlog);
  }

  @Override
  public final boolean isBound() {
    return boundEndpoint != null;
  }

  @Override
  public final boolean isClosed() {
    return super.isClosed() || (isBound() && !implementation.getFD().valid());
  }

  @Override
  public AFSocket<A> accept() throws IOException {
    return accept1(true);
  }

  AFSocket<A> accept1(boolean throwOnFail) throws IOException {
    AFSocket<A> as = newSocketInstance();

    boolean success = implementation.accept0(as.getAFImpl(false));
    if (isClosed()) {
      // We may have connected to the socket to unblock it
      throw new SocketException("Socket is closed");
    }

    if (!success) {
      if (throwOnFail) {
        if (getChannel().isBlocking()) {
          // unexpected
          return null;
        } else {
          // non-blocking socket, nothing to accept
          throw new IllegalBlockingModeException();
        }
      } else {
        return null;
      }
    }

    as.getAFImpl(true); // trigger create
    as.connect(AFSocketAddress.INTERNAL_DUMMY_CONNECT);
    as.getAFImpl().updatePorts(getAFImpl().getLocalPort1(), getAFImpl().getRemotePort());

    return as;
  }

  /**
   * Returns a new {@link AFSocket} instance.
   * 
   * @return The new instance.
   * @throws IOException on error.
   */
  protected abstract AFSocket<A> newSocketInstance() throws IOException;

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + (isBound() ? boundEndpoint : "unbound") + "]";
  }

  @Override
  public synchronized void close() throws IOException {
    if (isClosed()) {
      return;
    }

    boolean localSocketAddressValid = isLocalSocketAddressValid();

    AFSocketAddress endpoint = boundEndpoint;

    IOException superException = null;
    try {
      super.close();
    } catch (IOException e) {
      superException = e;
    }
    if (implementation != null) {
      try {
        implementation.close();
      } catch (IOException e) {
        if (superException == null) {
          superException = e;
        } else {
          superException.addSuppressed(e);
        }
      }
    }

    IOException ex = null;
    try {
      closeables.close(superException);
    } finally {
      if (endpoint != null && endpoint.hasFilename() && localSocketAddressValid
          && isDeleteOnClose()) {
        File f = endpoint.getFile();
        if (!f.delete() && f.exists()) {
          ex = new IOException("Could not delete socket file after close: " + f);
        }
      }
    }
    if (ex != null) {
      throw ex;
    }
  }

  /**
   * Registers a {@link Closeable} that should be closed when this socket is closed.
   * 
   * @param closeable The closeable.
   */
  public final void addCloseable(Closeable closeable) {
    closeables.add(closeable);
  }

  /**
   * Unregisters a previously registered {@link Closeable}.
   * 
   * @param closeable The closeable.
   */
  public final void removeCloseable(Closeable closeable) {
    closeables.remove(closeable);
  }

  /**
   * Checks whether everything is setup to support junixsocket sockets.
   * 
   * @return {@code true} if supported.
   */
  public static boolean isSupported() {
    return NativeUnixSocket.isLoaded();
  }

  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public final @Nullable A getLocalSocketAddress() {
    if (boundEndpoint == null) {
      setBoundEndpoint(getAFImpl().getLocalSocketAddress());
    }
    return boundEndpoint;
  }

  /**
   * Checks if the local socket address returned by {@link #getLocalSocketAddress()} is still valid.
   * 
   * The address is no longer valid if the server socket has been closed, {@code null}, or another
   * server socket has been bound on that address.
   * 
   * @return {@code true} iff still valid.
   */
  public boolean isLocalSocketAddressValid() {
    if (isClosed()) {
      return false;
    }
    @Nullable
    A addr = getLocalSocketAddress();
    if (addr == null) {
      return false;
    }
    return addr.equals(getAFImpl().getLocalSocketAddress());
  }

  final void setBoundEndpoint(@Nullable A addr) {
    this.boundEndpoint = addr;
    int port;
    if (addr == null) {
      port = -1;
    } else {
      port = addr.getPort();
    }
    getAFImpl().updatePorts(port, -1);
  }

  @Override
  public final int getLocalPort() {
    if (boundEndpoint == null) {
      setBoundEndpoint(getAFImpl().getLocalSocketAddress());
    }
    if (boundEndpoint == null) {
      return -1;
    } else {
      return getAFImpl().getLocalPort1();
    }
  }

  /**
   * Checks if this {@link AFServerSocket}'s file should be removed upon {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @return {@code true} if an attempt is made to delete the socket file upon {@link #close()}.
   */
  public final boolean isDeleteOnClose() {
    return deleteOnClose.get();
  }

  /**
   * Enables/disables deleting this {@link AFServerSocket}'s file (or other resource type) upon
   * {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @param b Enabled if {@code true}.
   */
  public final void setDeleteOnClose(boolean b) {
    deleteOnClose.set(b);
  }

  final AFSocketImpl<A> getAFImpl() {
    if (created.compareAndSet(false, true)) {
      try {
        getSoTimeout(); // trigger create via java.net.Socket
      } catch (IOException e) {
        // ignore
      }
    }
    return implementation;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public AFServerSocketChannel<?> getChannel() {
    return channel;
  }

  @Override
  public final FileDescriptor getFileDescriptor() throws IOException {
    return implementation.getFileDescriptor();
  }

  /**
   * Returns the address family supported by this implementation.
   * 
   * @return The family.
   */
  protected final AFAddressFamily<A> addressFamily() {
    return getAFImpl().getAddressFamily();
  }

  /**
   * Sets the hook for any subsequent call to {@link #bind(SocketAddress)} and
   * {@link #bind(SocketAddress, int)} to be the given function.
   * 
   * The function can monitor calls or even alter the endpoint address.
   * 
   * @param hook The function that gets called for each {@code bind} call.
   * @return This instance.
   */
  public final AFServerSocket<A> bindHook(SocketAddressFilter hook) {
    this.bindFilter = hook;
    return this;
  }
}
