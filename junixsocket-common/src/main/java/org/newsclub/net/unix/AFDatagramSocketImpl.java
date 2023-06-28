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
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link DatagramSocketImpl} implemented by junixsocket.
 *
 * @param <A> The associated address type.
 * @author Christian Kohlschütter
 */
public abstract class AFDatagramSocketImpl<A extends AFSocketAddress> extends
    DatagramSocketImplShim {
  private final AFSocketType socketType;
  private final AFSocketCore core;
  final AncillaryDataSupport ancillaryDataSupport = new AncillaryDataSupport();
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean bound = new AtomicBoolean(false);

  private final AtomicInteger socketTimeout = new AtomicInteger(0);
  private int remotePort = 0;
  private final AFAddressFamily<@NonNull A> addressFamily;
  private AFSocketImplExtensions<A> implExtensions = null;

  /**
   * Constructs a new {@link AFDatagramSocketImpl} using the given {@link FileDescriptor} (or null
   * to create a new one).
   *
   * @param addressFamily The address family.
   * @param fd The file descriptor, or {@code null}.
   * @param socketType The socket type.
   * @throws IOException on error.
   */
  @SuppressWarnings("this-escape")
  protected AFDatagramSocketImpl(AFAddressFamily<@NonNull A> addressFamily, FileDescriptor fd,
      AFSocketType socketType) throws IOException {
    super();
    this.addressFamily = addressFamily;
    // FIXME verify fd
    this.socketType = socketType;
    this.core = new AFSocketCore(this, fd, ancillaryDataSupport, getAddressFamily(), true);
    this.fd = core.fd;
  }

  @Override
  protected final void create() throws SocketException {
    if (isClosed()) {
      throw new SocketException("Already closed");
    } else if (fd.valid()) {
      return;
    }
    try {
      NativeUnixSocket.createSocket(fd, getAddressFamily().getDomain(), socketType.getId());
    } catch (SocketException e) {
      throw e;
    } catch (IOException e) {
      throw (SocketException) new SocketException(e.getMessage()).initCause(e);
    }
  }

  @Override
  protected final void close() {
    core.runCleaner();
  }

  @Override
  protected final void connect(InetAddress address, int port) throws SocketException {
    // not used; see connect(AFSocketAddress)
  }

  final void connect(AFSocketAddress socketAddress) throws IOException {
    if (socketAddress == AFSocketAddress.INTERNAL_DUMMY_CONNECT) { // NOPMD
      return;
    }
    ByteBuffer ab = socketAddress.getNativeAddressDirectBuffer();
    NativeUnixSocket.connect(ab, ab.limit(), fd, -1);
    this.remotePort = socketAddress.getPort();
  }

  @Override
  protected final void disconnect() {
    try {
      NativeUnixSocket.disconnect(fd);
      connected.set(false);
      this.remotePort = 0;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  final AFSocketCore getCore() {
    return core;
  }

  @Override
  protected final FileDescriptor getFileDescriptor() {
    return core.fd;
  }

  final boolean isClosed() {
    return core.isClosed();
  }

  @Override
  protected final void bind(int lport, InetAddress laddr) throws SocketException {
    // not used; see bind(AFSocketAddress)
  }

  final void bind(AFSocketAddress socketAddress) throws SocketException {
    if (socketAddress == AFSocketAddress.INTERNAL_DUMMY_BIND) { // NOPMD
      return;
    }
    try {
      ByteBuffer ab;
      if (socketAddress == null) {
        ab = AFSocketAddress.getNativeAddressDirectBuffer(0);
      } else {
        ab = socketAddress.getNativeAddressDirectBuffer();
      }
      NativeUnixSocket.bind(ab, ab.limit(), fd, NativeUnixSocket.OPT_DGRAM_MODE);
      if (socketAddress == null) {
        this.localPort = 0;
        this.bound.set(false);
      } else {
        this.localPort = socketAddress.getPort();
      }
    } catch (SocketException e) {
      throw e;
    } catch (IOException e) {
      throw (SocketException) new SocketException(e.getMessage()).initCause(e);
    }
  }

  @Override
  protected final void receive(DatagramPacket p) throws IOException {
    recv(p, 0);
  }

  private void recv(DatagramPacket p, int options) throws IOException {
    int len = p.getLength();
    FileDescriptor fdesc = core.validFdOrException();

    ByteBuffer datagramPacketBuffer = core.getThreadLocalDirectByteBuffer(len);
    len = Math.min(len, datagramPacketBuffer.capacity());

    options |= core.isBlocking() ? 0 : NativeUnixSocket.OPT_NON_BLOCKING;

    ByteBuffer socketAddressBuffer = AFSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
    int count = NativeUnixSocket.receive(fdesc, datagramPacketBuffer, 0, len, socketAddressBuffer,
        options, ancillaryDataSupport, socketTimeout.get());
    if (count > len) {
      throw new IllegalStateException("count > len: " + count + " > " + len);
    } else if (count == -1) {
      throw new SocketTimeoutException();
    } else if (count < 0) {
      throw new IllegalStateException("count: " + count + " < 0");
    }
    datagramPacketBuffer.limit(count);
    datagramPacketBuffer.rewind();
    datagramPacketBuffer.get(p.getData(), p.getOffset(), count);

    p.setLength(count);

    A addr = AFSocketAddress.ofInternal(socketAddressBuffer, getAddressFamily());
    p.setAddress(addr == null ? null : addr.getInetAddress());
    p.setPort(remotePort);
  }

  @Override
  protected final void send(DatagramPacket p) throws IOException {
    InetAddress addr = p.getAddress();
    ByteBuffer sendToBuf = null;
    int sendToBufLen = 0;
    if (addr != null) {
      byte[] addrBytes = AFInetAddress.unwrapAddress(addr, getAddressFamily());
      if (addrBytes != null) {
        sendToBuf = AFSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
        sendToBufLen = NativeUnixSocket.bytesToSockAddr(getAddressFamily().getDomain(), sendToBuf,
            addrBytes);
        sendToBuf.position(0);
        if (sendToBufLen == -1) {
          throw new SocketException("Unsupported domain");
        }
      }
    }
    FileDescriptor fdesc = core.validFdOrException();

    int len = p.getLength();

    ByteBuffer datagramPacketBuffer = core.getThreadLocalDirectByteBuffer(len);
    datagramPacketBuffer.clear();
    datagramPacketBuffer.put(p.getData(), p.getOffset(), p.getLength());
    datagramPacketBuffer.flip();

    NativeUnixSocket.send(fdesc, datagramPacketBuffer, 0, len, sendToBuf, sendToBufLen,
        /* NativeUnixSocket.OPT_NON_BLOCKING | */
        NativeUnixSocket.OPT_DGRAM_MODE, ancillaryDataSupport);
  }

  @Override
  protected final int peek(InetAddress i) throws IOException {
    throw new SocketException("Unsupported operation");
  }

  @Override
  protected final int peekData(DatagramPacket p) throws IOException {
    recv(p, NativeUnixSocket.OPT_PEEK);
    return 0;
  }

  @Override
  @Deprecated
  protected final byte getTTL() throws IOException {
    return (byte) (getTimeToLive() & 0xFF);
  }

  @Override
  @Deprecated
  protected final void setTTL(byte ttl) throws IOException {
    // ignored
  }

  @Override
  protected final int getTimeToLive() throws IOException {
    return 0;
  }

  @Override
  protected final void setTimeToLive(int ttl) throws IOException {
    // ignored
  }

  @Override
  protected final void join(InetAddress inetaddr) throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  protected final void leave(InetAddress inetaddr) throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  protected final void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf)
      throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  protected final void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf)
      throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  public Object getOption(int optID) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    FileDescriptor fdesc = core.validFdOrException();
    return AFSocketImpl.getOptionDefault(fdesc, optID, socketTimeout, getAddressFamily());
  }

  @Override
  public void setOption(int optID, Object value) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    FileDescriptor fdesc = core.validFdOrException();
    AFSocketImpl.setOptionDefault(fdesc, optID, value, socketTimeout);
  }

  @SuppressWarnings("unchecked")
  final A receive(ByteBuffer dst) throws IOException {
    try {
      return (A) core.receive(dst);
    } catch (SocketClosedException e) {
      throw (ClosedChannelException) new ClosedChannelException().initCause(e);
    }
  }

  final int send(ByteBuffer src, SocketAddress target) throws IOException {
    try {
      return core.write(src, target, 0);
    } catch (SocketClosedException e) {
      throw (ClosedChannelException) new ClosedChannelException().initCause(e);
    }
  }

  final int read(ByteBuffer dst, ByteBuffer socketAddressBuffer) throws IOException {
    try {
      return core.read(dst, socketAddressBuffer, 0);
    } catch (SocketClosedException e) {
      throw (ClosedChannelException) new ClosedChannelException().initCause(e);
    }
  }

  final int write(ByteBuffer src) throws IOException {
    try {
      return core.write(src);
    } catch (SocketClosedException e) {
      throw (ClosedChannelException) new ClosedChannelException().initCause(e);
    }
  }

  final boolean isConnected() {
    if (connected.get()) {
      return true;
    }
    if (isClosed()) {
      return false;
    }
    if (core.isConnected(false)) {
      connected.set(true);
      return true;
    }
    return false;
  }

  final boolean isBound() {
    if (bound.get()) {
      return true;
    }
    if (isClosed()) {
      return false;
    }
    if (core.isConnected(true)) {
      bound.set(true);
      return true;
    }
    return false;
  }

  final void updatePorts(int local, int remote) {
    this.localPort = local;
    this.remotePort = remote;
  }

  final @Nullable A getLocalSocketAddress() {
    return AFSocketAddress.getSocketAddress(getFileDescriptor(), false, localPort,
        getAddressFamily());
  }

  final @Nullable A getRemoteSocketAddress() {
    return AFSocketAddress.getSocketAddress(getFileDescriptor(), true, remotePort,
        getAddressFamily());
  }

  /**
   * Returns the address family supported by this implementation.
   *
   * @return The family.
   */
  protected final AFAddressFamily<@NonNull A> getAddressFamily() {
    return addressFamily;
  }

  /**
   * Returns the internal helper instance for address-specific extensions.
   *
   * @return The helper instance.
   * @throws UnsupportedOperationException if such extensions are not supported for this address
   *           type.
   */
  protected final synchronized AFSocketImplExtensions<A> getImplExtensions() {
    if (implExtensions == null) {
      implExtensions = addressFamily.initImplExtensions(ancillaryDataSupport);
    }
    return implExtensions;
  }
}
