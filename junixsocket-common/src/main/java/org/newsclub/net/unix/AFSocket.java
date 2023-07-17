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

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * junixsocket's base implementation of a {@link Socket}.
 *
 * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public abstract class AFSocket<A extends AFSocketAddress> extends Socket implements AFSomeSocket,
    AFSocketExtensions {
  static final String PROP_LIBRARY_DISABLE_CAPABILITY_PREFIX =
      "org.newsclub.net.unix.library.disable.";

  private static final byte[] ZERO_BYTES = new byte[0];

  @SuppressWarnings("PMD.MutableStaticState")
  static String loadedLibrary; // set by NativeLibraryLoader

  private static Integer capabilitiesValue = null;

  private final AFSocketImpl<A> impl;

  private final AFSocketAddressFromHostname<A> afh;
  private final Closeables closeables = new Closeables();
  private final AtomicBoolean created = new AtomicBoolean(false);

  @SuppressWarnings("this-escape")
  private final AFSocketChannel<A> channel = newChannel();

  private @Nullable SocketAddressFilter connectFilter;

  /**
   * Creates a new {@link AFSocket} instance.
   *
   * @param impl The corresponding {@link SocketImpl} class.
   * @param afh The conversion helper to get a socket address from an encoded hostname.
   * @throws SocketException on error.
   */
  protected AFSocket(final AFSocketImpl<A> impl, AFSocketAddressFromHostname<A> afh)
      throws SocketException {
    super(impl);
    this.afh = afh;
    this.impl = impl;
  }

  /**
   * Returns the {@link AFSocketAddress} type supported by this socket.
   *
   * @return The supported {@link AFSocketAddress}.
   */
  protected final Class<? extends AFSocketAddress> socketAddressClass() {
    return getAFImpl(false).getAddressFamily().getSocketAddressClass();
  }

  /**
   * Creates a new {@link AFSocketChannel} for this socket.
   *
   * @return The new instance.
   */
  protected abstract AFSocketChannel<A> newChannel();

  /**
   * The reference to the constructor of an {@link AFSocket} subclass.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   */
  @FunctionalInterface
  public interface Constructor<A extends AFSocketAddress> {
    /**
     * Constructs a new {@link AFSocket} subclass instance.
     *
     * @param fdObj The file descriptor.
     * @param factory The socket factory instance.
     * @return The instance.
     * @throws SocketException on error.
     */
    @NonNull
    AFSocket<A> newInstance(FileDescriptor fdObj, AFSocketFactory<A> factory)
        throws SocketException;
  }

  static <A extends AFSocketAddress> AFSocket<A> newInstance(Constructor<A> constr,
      AFSocketFactory<A> sf, FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    if (!fdObj.valid()) {
      throw new SocketException("Invalid file descriptor");
    }
    int status = NativeUnixSocket.socketStatus(fdObj);
    if (status == NativeUnixSocket.SOCKETSTATUS_INVALID) {
      throw new SocketException("Not a valid socket");
    }

    AFSocket<A> socket = newInstance0(constr, fdObj, sf);
    socket.getAFImpl().updatePorts(localPort, remotePort);

    switch (status) {
      case NativeUnixSocket.SOCKETSTATUS_CONNECTED:
        socket.internalDummyConnect();
        break;
      case NativeUnixSocket.SOCKETSTATUS_BOUND:
        socket.internalDummyBind();
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
   * Creates a new, unbound {@link AFSocket}.
   *
   * This "default" implementation is a bit "lenient" with respect to the specification.
   *
   * In particular, we may ignore calls to {@link Socket#getTcpNoDelay()} and
   * {@link Socket#setTcpNoDelay(boolean)}.
   *
   * @param <A> The corresponding address type.
   * @param constr The implementation's {@link AFSocket} constructor
   * @param factory The corresponding socket factory, or {@code null}.
   * @return A new, unbound socket.
   * @throws SocketException if the operation fails.
   */
  protected static final <A extends AFSocketAddress> AFSocket<A> newInstance(Constructor<A> constr,
      AFSocketFactory<A> factory) throws SocketException {
    return newInstance0(constr, null, factory);
  }

  private static <A extends AFSocketAddress> @NonNull AFSocket<A> newInstance0(
      Constructor<A> constr, FileDescriptor fdObj, AFSocketFactory<A> factory)
      throws SocketException {
    return constr.newInstance(fdObj, factory);
  }

  /**
   * Creates a new {@link AFSocket} and connects it to the given {@link AFSocketAddress}.
   *
   * @param <A> The corresponding address type.
   * @param constr The implementation's {@link AFSocket} constructor
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  protected static final <A extends AFSocketAddress> @NonNull AFSocket<A> connectTo(
      Constructor<A> constr, A addr) throws IOException {
    AFSocket<A> socket = constr.newInstance(null, null);
    socket.connect(addr);
    return socket;
  }

  /**
   * Creates a new {@link AFSocket} and connects it to the given {@link AFSocketAddress} using the
   * default implementation suited for that address type.
   *
   * @param <A> The corresponding address type.
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static final <A extends AFSocketAddress> AFSocket<?> connectTo(@NonNull A addr)
      throws IOException {
    AFSocket<?> socket = addr.getAddressFamily().getSocketConstructor().newInstance(null, null);
    socket.connect(addr);
    return socket;
  }

  /**
   * Not supported, since it's not necessary for client sockets.
   *
   * @see AFServerSocket
   */
  @Override
  public final void bind(SocketAddress bindpoint) throws IOException {
    if (bindpoint == null) {
      throw new IllegalArgumentException();
    }
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (isBound()) {
      throw new SocketException("Already bound");
    }
    preprocessSocketAddress(bindpoint);
    throw new SocketException("Use AF*ServerSocket#bind or #bindOn");
  }

  @Override
  public final boolean isBound() {
    return impl.getFD().valid() && (super.isBound() || impl.isBound());
  }

  @Override
  public final boolean isConnected() {
    return impl.getFD().valid() && (super.isConnected() || impl.isConnected());
  }

  @Override
  public final void connect(SocketAddress endpoint) throws IOException {
    connect(endpoint, 0);
  }

  @Override
  public final void connect(SocketAddress endpoint, int timeout) throws IOException {
    connect0(endpoint, timeout);
  }

  private AFSocketAddress preprocessSocketAddress(SocketAddress endpoint) throws SocketException {
    if (endpoint == null) {
      throw new IllegalArgumentException("endpoint is null");
    } else if (endpoint instanceof SentinelSocketAddress) {
      return (AFSocketAddress) endpoint;
    } else {
      return AFSocketAddress.preprocessSocketAddress(socketAddressClass(), endpoint, afh);
    }
  }

  final boolean connect0(SocketAddress endpoint, int timeout) throws IOException {
    if (timeout < 0) {
      throw new IllegalArgumentException("connect: timeout can't be negative");
    }
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    if (connectFilter != null) {
      endpoint = connectFilter.apply(endpoint);
    }

    AFSocketAddress address = preprocessSocketAddress(endpoint);

    if (!isBound()) {
      internalDummyBind();
    }

    boolean success = getAFImpl().connect0(address, timeout);
    if (success) {
      int port = address.getPort();
      if (port > 0) {
        getAFImpl().updatePorts(getLocalPort(), port);
      }
    }
    internalDummyConnect();
    return success;
  }

  final void internalDummyConnect() throws IOException {
    if (!isConnected()) {
      super.connect(AFSocketAddress.INTERNAL_DUMMY_CONNECT, 0);
    }
  }

  final void internalDummyBind() throws IOException {
    if (!isBound()) {
      super.bind(AFSocketAddress.INTERNAL_DUMMY_BIND);
    }
  }

  @Override
  public final String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode()) + toStringSuffix();
  }

  final String toStringSuffix() {
    if (impl.getFD().valid()) {
      return "[local=" + getLocalSocketAddress() + ";remote=" + getRemoteSocketAddress() + "]";
    } else {
      return "[invalid]";
    }
  }

  /**
   * Returns <code>true</code> iff {@link AFSocket}s are supported by the current Java VM.
   *
   * To support {@link AFSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>.
   *
   * @return {@code true} iff supported.
   */
  public static boolean isSupported() {
    return NativeUnixSocket.isLoaded();
  }

  /**
   * Checks if {@link AFSocket}s are supported by the current Java VM.
   *
   * If not, an {@link UnsupportedOperationException} is thrown.
   *
   * @throws UnsupportedOperationException if not supported.
   */
  public static void ensureSupported() throws UnsupportedOperationException {
    NativeUnixSocket.ensureSupported();
  }

  /**
   * Returns the version of the junixsocket library, as a string, for debugging purposes.
   *
   * NOTE: Do not rely on the format of the version identifier, use socket capabilities instead.
   *
   * @return String The version identifier, or {@code null} if it could not be determined.
   * @see #supports(AFSocketCapability)
   */
  public static final String getVersion() {
    String v = BuildProperties.getBuildProperties().get("git.build.version");
    if (v != null && !v.startsWith("$")) {
      return v;
    }

    try {
      return NativeLibraryLoader.getJunixsocketVersion();
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Returns an identifier of the loaded native library, or {@code null} if the library hasn't been
   * loaded yet.
   *
   * The identifier is useful mainly for debugging purposes.
   *
   * @return The identifier of the loaded junixsocket-native library, or {@code null}.
   */
  public static final String getLoadedLibrary() {
    return loadedLibrary;
  }

  @Override
  public final boolean isClosed() {
    return super.isClosed() || (isConnected() && !impl.getFD().valid()) || impl.isClosed();
  }

  @Override
  public final int getAncillaryReceiveBufferSize() {
    return impl.getAncillaryReceiveBufferSize();
  }

  @Override
  public final void setAncillaryReceiveBufferSize(int size) {
    impl.setAncillaryReceiveBufferSize(size);
  }

  @Override
  public final void ensureAncillaryReceiveBufferSize(int minSize) {
    impl.ensureAncillaryReceiveBufferSize(minSize);
  }

  private static boolean isCapDisabled(AFSocketCapability cap) {
    return Boolean.parseBoolean(System.getProperty(PROP_LIBRARY_DISABLE_CAPABILITY_PREFIX + cap
        .name(), "false"));
  }

  private static int initCapabilities() {
    if (!isSupported()) {
      return 0;
    } else {
      int v = NativeUnixSocket.capabilities();

      if (System.getProperty("osv.version") != null) {
        // no fork, no redirect...
        v &= ~(AFSocketCapability.CAPABILITY_FD_AS_REDIRECT.getBitmask());
      }

      for (AFSocketCapability cap : AFSocketCapability.values()) {
        if (isCapDisabled(cap)) {
          v &= ~(cap.getBitmask());
        }
      }

      return v;
    }
  }

  private static synchronized int capabilities() {
    if (capabilitiesValue == null) {
      capabilitiesValue = initCapabilities();
    }
    return capabilitiesValue;
  }

  /**
   * Checks if the current environment (system platform, native library, etc.) supports a given
   * junixsocket capability.
   *
   * Deprecated. Please use {@link #supports(AFSocketCapability)} instead.
   *
   * NOTE: The result may or may not be cached from a previous call or from a check upon
   * initialization.
   *
   * @param capability The capability.
   * @return true if supported.
   * @see #supports(AFSocketCapability)
   */
  @Deprecated
  public static final boolean supports(AFUNIXSocketCapability capability) {
    return (capabilities() & capability.getBitmask()) != 0;
  }

  /**
   * Checks if the current environment (system platform, native library, etc.) supports a given
   * junixsocket capability.
   *
   * NOTE: The result may or may not be cached from a previous call or from a check upon
   * initialization.
   *
   * @param capability The capability.
   * @return true if supported.
   */
  public static final boolean supports(AFSocketCapability capability) {
    return (capabilities() & capability.getBitmask()) != 0;
  }

  /**
   * Checks if the current environment (system platform, native library, etc.) supports "unsafe"
   * operations (as controlled via the {@link AFSocketCapability#CAPABILITY_UNSAFE} capability).
   *
   * If supported, the method returns normally. If not supported, an {@link IOException} is thrown.
   *
   * @throws IOException if "unsafe" operations are not supported.
   * @see Unsafe
   */
  public static final void ensureUnsafeSupported() throws IOException {
    if (!AFSocket.supports(AFSocketCapability.CAPABILITY_UNSAFE)) {
      throw new IOException("Unsafe operations are not supported in this environment");
    }
  }

  @Override
  public final synchronized void close() throws IOException {
    IOException superException = null;
    try {
      super.close();
    } catch (IOException e) {
      superException = e;
    }
    closeables.close(superException);
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

  final AFSocketImpl<A> getAFImpl() {
    return getAFImpl(true);
  }

  final AFSocketImpl<A> getAFImpl(boolean createSocket) {
    if (createSocket && created.compareAndSet(false, true)) {
      try {
        getSoTimeout(); // trigger create via java.net.Socket
      } catch (SocketException e) {
        // ignore
      }
    }
    return impl;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public AFSocketChannel<A> getChannel() {
    return channel;
  }

  @SuppressWarnings("null")
  @Override
  public final synchronized A getRemoteSocketAddress() {
    if (!isConnected()) {
      return null;
    }
    return impl.getRemoteSocketAddress();
  }

  @SuppressWarnings("null")
  @Override
  public final A getLocalSocketAddress() {
    if (isClosed()) {
      return null;
    }
    return impl.getLocalSocketAddress();
  }

  @Override
  public final FileDescriptor getFileDescriptor() throws IOException {
    return impl.getFileDescriptor();
  }

  @Override
  public final AFInputStream getInputStream() throws IOException {
    return getAFImpl().getInputStream();
  }

  @Override
  public final AFOutputStream getOutputStream() throws IOException {
    return getAFImpl().getOutputStream();
  }

  /**
   * Returns the internal helper instance for address-specific extensions.
   *
   * @return The helper instance.
   * @throws UnsupportedOperationException if such extensions are not supported for this address
   *           type.
   */
  protected final AFSocketImplExtensions<A> getImplExtensions() {
    return getAFImpl(false).getImplExtensions();
  }

  /**
   * Forces the address to be used for any subsequent call to {@link #connect(SocketAddress)} to be
   * the given one, regardless of what'll be passed there.
   *
   * @param endpoint The forced endpoint address.
   * @return This instance.
   */
  public final AFSocket<A> forceConnectAddress(SocketAddress endpoint) {
    return connectHook((SocketAddress orig) -> {
      return orig == null ? null : endpoint;
    });
  }

  /**
   * Sets the hook for any subsequent call to {@link #connect(SocketAddress)} or
   * {@link #connect(SocketAddress, int)} to be the given function.
   *
   * The function can monitor events or even alter the target address.
   *
   * @param hook The function that gets called for each connect call.
   * @return This instance.
   */
  public final AFSocket<A> connectHook(SocketAddressFilter hook) {
    this.connectFilter = hook;
    return this;
  }

  /**
   * Probes the status of the socket connection.
   *
   * This usually involves checking for {@link #isConnected()}, and if assumed connected, also
   * sending a zero-length message to the remote.
   *
   * @return {@code true} if the connection is known to be closed, {@code false} if the connection
   *         is open/not closed or the condition is unknown.
   * @throws IOException on an unexpected error.
   */
  public boolean checkConnectionClosed() throws IOException {
    if (!isConnected()) {
      return true;
    }
    try {
      if (!AFSocket.supports(AFSocketCapability.CAPABILITY_ZERO_LENGTH_SEND)) {
        return false;
      }
      getOutputStream().write(ZERO_BYTES);
      return false;
    } catch (SocketClosedException e) {
      return true;
    } catch (IOException e) {
      if (!isConnected()) {
        return true;
      } else {
        throw e;
      }
    }
  }
}
