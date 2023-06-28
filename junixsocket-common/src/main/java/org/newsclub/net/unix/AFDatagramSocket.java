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
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.Nullable;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A {@link DatagramSocket} implementation that works with junixsocket.
 *
 * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
 * @author Christian Kohlschütter
 */
public abstract class AFDatagramSocket<A extends AFSocketAddress> extends DatagramSocketShim
    implements AFSomeSocket, AFSocketExtensions {
  private static final InetSocketAddress WILDCARD_ADDRESS = new InetSocketAddress(0);

  private final AFDatagramSocketImpl<A> impl;
  private final AncillaryDataSupport ancillaryDataSupport;
  private final AtomicBoolean created = new AtomicBoolean(false);
  private final AtomicBoolean deleteOnClose = new AtomicBoolean(true);

  @SuppressWarnings("this-escape")
  private final AFDatagramChannel<A> channel = newChannel();

  /**
   * Creates a new {@link AFDatagramSocket} instance.
   *
   * @param impl The corresponding {@link SocketImpl} class.
   * @throws IOException on error.
   */
  protected AFDatagramSocket(final AFDatagramSocketImpl<A> impl) throws IOException {
    super(impl);
    this.impl = impl;
    this.ancillaryDataSupport = impl.ancillaryDataSupport;
  }

  /**
   * Creates a new {@link DatagramChannel} that is associated with this socket.
   *
   * @return The channel.
   */
  protected abstract AFDatagramChannel<A> newChannel();

  /**
   * Returns the {@code AncillaryDataSupport} instance.
   *
   * @return The instance.
   */
  final AncillaryDataSupport getAncillaryDataSupport() {
    return ancillaryDataSupport;
  }

  /**
   * A reference to the constructor of an {@link AFDatagramSocket} subclass.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   */
  @FunctionalInterface
  public interface Constructor<A extends AFSocketAddress> {
    /**
     * Constructs a new {@link DatagramSocket} instance.
     *
     * @param fd The file descriptor.
     * @return The new instance.
     * @throws IOException on error.
     */
    AFDatagramSocket<A> newSocket(FileDescriptor fd) throws IOException;
  }

  /**
   * Returns the {@link AFSocketAddress} type supported by this socket.
   *
   * @return The supported {@link AFSocketAddress}.
   */
  protected final Class<? extends AFSocketAddress> socketAddressClass() {
    return impl.getAddressFamily().getSocketAddressClass();
  }

  /**
   * Returns a new {@link AFDatagramSocket} instance.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @param constructor The supplying constructor.
   * @return The new instance.
   * @throws IOException on error.
   */
  protected static final <A extends AFSocketAddress> AFDatagramSocket<A> newInstance(
      Constructor<A> constructor) throws IOException {
    return constructor.newSocket(null);
  }

  /**
   * Creates a new {@link AFDatagramSocket}.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @param constructor The supplying constructor.
   * @param fdObj The file descriptor.
   * @param localPort The local port.
   * @param remotePort The remote port.
   * @return The new instance.
   * @throws IOException on error.
   */
  protected static final <A extends AFSocketAddress> AFDatagramSocket<A> newInstance(
      Constructor<A> constructor, FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    if (fdObj == null) {
      return newInstance(constructor);
    }
    if (!fdObj.valid()) {
      throw new SocketException("Invalid file descriptor");
    }

    int status = NativeUnixSocket.socketStatus(fdObj);
    if (status == NativeUnixSocket.SOCKETSTATUS_INVALID) {
      throw new SocketException("Not a valid socket");
    }

    AFDatagramSocket<A> socket = constructor.newSocket(fdObj);
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

    return socket;
  }

  @Override
  public final void connect(InetAddress address, int port) {
    throw new IllegalArgumentException("Cannot connect to InetAddress");
  }

  /**
   * Reads the next received packet without actually removing it from the queue.
   *
   * In other words, once a packet is received, calling this method multiple times in a row will not
   * have further effects on the packet contents.
   *
   * This call still blocks until at least one packet has been received and added to the queue.
   *
   * @param p The packet.
   * @throws IOException on error.
   */
  public final void peek(DatagramPacket p) throws IOException {
    synchronized (p) {
      if (isClosed()) {
        throw new SocketException("Socket is closed");
      }
      getAFImpl().peekData(p);
    }
  }

  @Override
  public final void send(DatagramPacket p) throws IOException {
    synchronized (p) {
      if (isClosed()) {
        throw new SocketException("Socket is closed");
      }
      if (!isBound()) {
        internalDummyBind();
      }
      getAFImpl().send(p);
    }
  }

  final void internalDummyConnect() throws SocketException {
    super.connect(AFSocketAddress.INTERNAL_DUMMY_DONT_CONNECT);
  }

  final void internalDummyBind() throws SocketException {
    bind(AFSocketAddress.INTERNAL_DUMMY_BIND);
  }

  @Override
  public final synchronized void connect(SocketAddress addr) throws SocketException {
    if (!isBound()) {
      internalDummyBind();
    }
    internalDummyConnect();
    try {
      getAFImpl().connect(AFSocketAddress.preprocessSocketAddress(socketAddressClass(), addr,
          null));
    } catch (SocketException e) {
      throw e;
    } catch (IOException e) {
      throw (SocketException) new SocketException(e.getMessage()).initCause(e);
    }
  }

  @Override
  public final synchronized @Nullable A getRemoteSocketAddress() {
    return getAFImpl().getRemoteSocketAddress();
  }

  @Override
  public final boolean isConnected() {
    return super.isConnected() || impl.isConnected();
  }

  @Override
  public final boolean isBound() {
    return super.isBound() || impl.isBound();
  }

  @Override
  public final void close() {
    // IMPORTANT This method must not be synchronized on "this",
    // otherwise we can't unblock a pending read
    if (isClosed()) {
      return;
    }
    getAFImpl().close();
    boolean wasBound = isBound();
    if (wasBound && deleteOnClose.get()) {
      InetAddress addr = getLocalAddress();
      if (AFInetAddress.isSupportedAddress(addr, addressFamily())) {
        try {
          AFSocketAddress socketAddress = AFSocketAddress.unwrap(addr, 0, addressFamily());
          if (socketAddress != null && socketAddress.hasFilename()) {
            if (!socketAddress.getFile().delete()) {
              // ignore
            }
          }
        } catch (IOException e) {
          // ignore
        }
      }
    }
    super.close();
  }

  @Override
  @SuppressWarnings("PMD.CognitiveComplexity")
  public final synchronized void bind(SocketAddress addr) throws SocketException {
    boolean isBound = isBound();
    if (isBound) {
      if (addr == AFSocketAddress.INTERNAL_DUMMY_BIND) { // NOPMD
        return;
      }
      // getAFImpl().bind(null); // try unbind (may not succeed)
    }
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (!isBound) {
      try {
        super.bind(AFSocketAddress.INTERNAL_DUMMY_BIND);
      } catch (AlreadyBoundException e) {
        // ignore
      } catch (SocketException e) {
        String message = e.getMessage();
        if (message != null && message.contains("already bound")) {
          // ignore (Java 14 or older)
        } else {
          throw e;
        }
      }
    }

    boolean isWildcardBind = WILDCARD_ADDRESS.equals(addr);

    AFSocketAddress epoint = (addr == null || isWildcardBind) ? null : AFSocketAddress
        .preprocessSocketAddress(socketAddressClass(), addr, null);
    if (epoint instanceof SentinelSocketAddress) {
      return;
    }

    try {
      getAFImpl().bind(epoint);
    } catch (SocketException e) {
      if (isWildcardBind) {
        // permit errors on wildcard bind
      } else {
        getAFImpl().close();
        throw e;
      }
    }
  }

  @Override
  public final @Nullable A getLocalSocketAddress() {
    if (isClosed()) {
      return null;
    }
    if (!isBound()) {
      return null;
    }
    return getAFImpl().getLocalSocketAddress();
  }

  /**
   * Checks if this {@link AFDatagramSocket}'s bound filename should be removed upon
   * {@link #close()}.
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
   * Enables/disables deleting this {@link AFDatagramSocket}'s bound filename upon {@link #close()}.
   *
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   *
   * @param b Enabled if {@code true}.
   */
  public final void setDeleteOnClose(boolean b) {
    deleteOnClose.set(b);
  }

  final AFDatagramSocketImpl<A> getAFImpl() {
    if (created.compareAndSet(false, true)) {
      try {
        getSoTimeout(); // trigger create via java.net.Socket
      } catch (SocketException e) {
        // ignore
      }
    }
    return impl;
  }

  final AFDatagramSocketImpl<A> getAFImpl(boolean create) {
    if (create) {
      return getAFImpl();
    } else {
      return impl;
    }
  }

  @Override
  public final int getAncillaryReceiveBufferSize() {
    return ancillaryDataSupport.getAncillaryReceiveBufferSize();
  }

  @Override
  public final void setAncillaryReceiveBufferSize(int size) {
    ancillaryDataSupport.setAncillaryReceiveBufferSize(size);
  }

  @Override
  public final void ensureAncillaryReceiveBufferSize(int minSize) {
    ancillaryDataSupport.ensureAncillaryReceiveBufferSize(minSize);
  }

  @Override
  public final boolean isClosed() {
    return super.isClosed() || getAFImpl().isClosed();
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public AFDatagramChannel<A> getChannel() {
    return channel;
  }

  @Override
  public final FileDescriptor getFileDescriptor() throws IOException {
    return getAFImpl().getFileDescriptor();
  }

  @Override
  public final void receive(DatagramPacket p) throws IOException {
    getAFImpl().receive(p);
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
   * Returns the internal helper instance for address-specific extensions.
   *
   * @return The helper instance.
   * @throws UnsupportedOperationException if such extensions are not supported for this address
   *           type.
   */
  protected AFSocketImplExtensions<A> getImplExtensions() {
    return getAFImpl(false).getImplExtensions();
  }

  /**
   * Returns the value of a junixsocket socket option.
   *
   * @param <T> The type of the socket option value.
   * @param name The socket option.
   * @return The value of the socket option.
   * @throws IOException on error.
   */
  @Override
  public <T> T getOption(AFSocketOption<T> name) throws IOException {
    return getAFImpl().getCore().getOption(name);
  }

  /**
   * Sets the value of a socket option.
   *
   * @param <T> The type of the socket option value.
   * @param name The socket option.
   * @param value The value of the socket option.
   * @return this DatagramSocket.
   * @throws IOException on error.
   */
  @Override
  public <T> DatagramSocket setOption(AFSocketOption<T> name, T value) throws IOException {
    getAFImpl().getCore().setOption(name, value);
    return this;
  }
}
