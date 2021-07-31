/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
import java.util.concurrent.atomic.AtomicBoolean;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Implementation of an AF_UNIX domain socket.
 * 
 * @author Christian Kohlschütter
 */
public final class AFUNIXSocket extends Socket implements AFUNIXSomeSocket, AFUNIXSocketExtensions {
  @SuppressWarnings("PMD.MutableStaticState")
  static String loadedLibrary; // set by NativeLibraryLoader

  private static Integer capabilities = null;

  private AFUNIXSocketImpl impl;

  private final AFUNIXSocketFactory socketFactory;
  private final Closeables closeables = new Closeables();
  private final AtomicBoolean created = new AtomicBoolean(false);
  private final AFUNIXSocketChannel channel = new AFUNIXSocketChannel(this);

  private AFUNIXSocket(final AFUNIXSocketImpl impl, AFUNIXSocketFactory factory)
      throws SocketException {
    super(impl);
    this.socketFactory = factory;
  }

  /**
   * Creates a new, unbound {@link AFUNIXSocket}.
   * 
   * This "default" implementation is a bit "lenient" with respect to the specification.
   * 
   * In particular, we ignore calls to {@link Socket#getTcpNoDelay()} and
   * {@link Socket#setTcpNoDelay(boolean)}.
   * 
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket newInstance() throws IOException {
    return newInstance0(null, null);
  }

  static AFUNIXSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    if (!fdObj.valid()) {
      throw new SocketException("Invalid file descriptor");
    }
    int status = NativeUnixSocket.socketStatus(fdObj);
    if (status == NativeUnixSocket.SOCKETSTATUS_INVALID) {
      throw new SocketException("Not a valid socket");
    }

    AFUNIXSocket socket = newInstance0(fdObj, (AFUNIXSocketFactory) null);
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

  static AFUNIXSocket newInstance(AFUNIXSocketFactory factory) throws SocketException {
    return newInstance0(null, factory);
  }

  private static AFUNIXSocket newInstance0(FileDescriptor fdObj, AFUNIXSocketFactory factory)
      throws SocketException {
    final AFUNIXSocketImpl impl = new AFUNIXSocketImpl.Lenient(fdObj);
    AFUNIXSocket instance = new AFUNIXSocket(impl, factory);
    instance.impl = impl;

    return instance;
  }

  /**
   * Creates a new, unbound, "strict" {@link AFUNIXSocket}.
   * 
   * This call uses an implementation that tries to be closer to the specification than
   * {@link #newInstance()}, at least for some cases.
   * 
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket newStrictInstance() throws IOException {
    final AFUNIXSocketImpl impl = new AFUNIXSocketImpl();
    AFUNIXSocket instance = new AFUNIXSocket(impl, null);
    instance.impl = impl;
    return instance;
  }

  /**
   * Creates a new {@link AFUNIXSocket} and connects it to the given {@link AFUNIXSocketAddress}.
   * 
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket connectTo(AFUNIXSocketAddress addr) throws IOException {
    AFUNIXSocket socket = newInstance();
    socket.connect(addr);
    return socket;
  }

  /**
   * Not supported, since it's not necessary for client sockets.
   * 
   * @see AFUNIXServerSocket
   */
  @Override
  public void bind(SocketAddress bindpoint) throws IOException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (isBound()) {
      throw new SocketException("Already bound");
    }
    AFUNIXSocketAddress.preprocessSocketAddress(bindpoint, socketFactory);
    throw new SocketException("Use AFUNIXServerSocket#bind or #bindOn");
  }

  @Override
  public boolean isBound() {
    return super.isBound() || impl.isBound();
  }

  @Override
  public boolean isConnected() {
    return super.isConnected() || impl.isConnected();
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    connect(endpoint, 0);
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    connect0(endpoint, timeout);
  }

  boolean connect0(SocketAddress endpoint, int timeout) throws IOException {
    if (endpoint == null) {
      throw new IllegalArgumentException("connect: The address can't be null");
    }
    if (timeout < 0) {
      throw new IllegalArgumentException("connect: timeout can't be negative");
    }
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    AFUNIXSocketAddress address = AFUNIXSocketAddress.preprocessSocketAddress(endpoint,
        socketFactory);

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

  void internalDummyConnect() throws IOException {
    if (!isConnected()) {
      super.connect(AFUNIXSocketAddress.INTERNAL_DUMMY_CONNECT, 0);
    }
  }

  void internalDummyBind() throws IOException {
    if (!isBound()) {
      super.bind(AFUNIXSocketAddress.INTERNAL_DUMMY_BIND);
    }
  }

  @Override
  public String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode()) + toStringSuffix();
  }

  String toStringSuffix() {
    if (impl.getFD().valid()) {
      return "[local=" + getLocalSocketAddress() + ";remote=" + getRemoteSocketAddress() + "]";
    } else {
      return "[invalid]";
    }
  }

  /**
   * Returns <code>true</code> iff {@link AFUNIXSocket}s are supported by the current Java VM.
   * 
   * To support {@link AFUNIXSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>.
   * 
   * @return {@code true} iff supported.
   */
  public static boolean isSupported() {
    return NativeUnixSocket.isLoaded();
  }

  /**
   * Returns the version of the junixsocket library, as a string, for debugging purposes.
   * 
   * NOTE: Do not rely on the format of the version identifier, use socket capabilities instead.
   * 
   * @return String The version identfier, or {@code null} if it could not be determined.
   * @see #supports(AFUNIXSocketCapability)
   */
  public static String getVersion() {
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
  public static String getLoadedLibrary() {
    return loadedLibrary;
  }

  @Override
  public AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    if (isClosed() || !isConnected()) {
      throw new SocketException("Not connected");
    }
    return impl.getPeerCredentials();
  }

  @Override
  public boolean isClosed() {
    return super.isClosed() || (isConnected() && !impl.getFD().valid());
  }

  @Override
  public int getAncillaryReceiveBufferSize() {
    return impl.getAncillaryReceiveBufferSize();
  }

  @Override
  public void setAncillaryReceiveBufferSize(int size) {
    impl.setAncillaryReceiveBufferSize(size);
  }

  @Override
  public void ensureAncillaryReceiveBufferSize(int minSize) {
    impl.ensureAncillaryReceiveBufferSize(minSize);
  }

  @Override
  public FileDescriptor[] getReceivedFileDescriptors() throws IOException {
    return impl.getReceivedFileDescriptors();
  }

  @Override
  public void clearReceivedFileDescriptors() {
    impl.clearReceivedFileDescriptors();
  }

  @Override
  public void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    if (fdescs != null && fdescs.length > 0 && !isConnected()) {
      throw new SocketException("Not connected");
    }
    impl.setOutboundFileDescriptors(fdescs);
  }

  @Override
  public boolean hasOutboundFileDescriptors() {
    return impl.hasOutboundFileDescriptors();
  }

  private static synchronized int getCapabilities() {
    if (capabilities == null) {
      if (!isSupported()) {
        capabilities = 0;
      } else {
        capabilities = NativeUnixSocket.capabilities();
      }
    }
    return capabilities.intValue();
  }

  /**
   * Checks if the current environment (system platform, native library, etc.) supports a given
   * junixsocket capability.
   * 
   * @param capability The capability.
   * @return true if supported.
   */
  public static boolean supports(AFUNIXSocketCapability capability) {
    return (getCapabilities() & capability.getBitmask()) != 0;
  }

  @Override
  public synchronized void close() throws IOException {
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
  public void addCloseable(Closeable closeable) {
    closeables.add(closeable);
  }

  /**
   * Unregisters a previously registered {@link Closeable}.
   * 
   * @param closeable The closeable.
   */
  public void removeCloseable(Closeable closeable) {
    closeables.remove(closeable);
  }

  /**
   * Very basic self-test function.
   * 
   * Prints "supported" and "capabilities" status to System.out.
   * 
   * @param args ignored.
   */
  public static void main(String[] args) {
    // If you want to run this directly from within Eclipse, see AFUNIXSocketTest#testMain.
    System.out.print(AFUNIXSocket.class.getName() + ".isSupported(): ");
    System.out.flush();
    System.out.println(AFUNIXSocket.isSupported());

    for (AFUNIXSocketCapability cap : AFUNIXSocketCapability.values()) {
      System.out.print(cap + ": ");
      System.out.flush();
      System.out.println(AFUNIXSocket.supports(cap));
    }
  }

  AFUNIXSocketImpl getAFImpl() {
    if (created.compareAndSet(false, true)) {
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
  public AFUNIXSocketChannel getChannel() {
    return channel;
  }

  @Override
  public synchronized AFUNIXSocketAddress getRemoteSocketAddress() {
    if (!isConnected()) {
      return null;
    }
    return impl.getRemoteSocketAddress();
  }

  @Override
  public AFUNIXSocketAddress getLocalSocketAddress() {
    if (isClosed()) {
      return null;
    }
    return impl.getLocalSocketAddress();
  }

  @Override
  public FileDescriptor getFileDescriptor() throws IOException {
    return impl.getFileDescriptor();
  }
}
