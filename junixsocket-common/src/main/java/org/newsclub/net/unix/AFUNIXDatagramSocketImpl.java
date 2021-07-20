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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class AFUNIXDatagramSocketImpl extends DatagramSocketImpl {
  private final AFUNIXSocketCore core;
  final AncillaryDataSupport ancillaryDataSupport = new AncillaryDataSupport();
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean bound = new AtomicBoolean(false);

  private final AtomicInteger socketTimeout = new AtomicInteger(0);
  private int remotePort = 0;

  AFUNIXDatagramSocketImpl(FileDescriptor fd) throws IOException {
    super();
    this.core = new AFUNIXSocketCore(this, fd, ancillaryDataSupport);
    this.fd = core.fd;
  }

  @Override
  protected void create() throws SocketException {
    if (isClosed()) {
      throw new SocketException("Already closed");
    } else if (fd.valid()) {
      return;
    }
    try {
      NativeUnixSocket.createSocket(fd, NativeUnixSocket.SOCK_DGRAM);
    } catch (SocketException e) {
      throw e;
    } catch (IOException e) {
      throw (SocketException) new SocketException(e.getMessage()).initCause(e);
    }
  }

  @Override
  protected void close() {
    core.runCleaner();
  }

  @Override
  protected void connect(InetAddress address, int port) throws SocketException {
    // not used; see connect(AFUNIXSocketAddress)
  }

  void connect(AFUNIXSocketAddress socketAddress) throws IOException {
    if (socketAddress == AFUNIXSocketAddress.INTERNAL_DUMMY_CONNECT) { // NOPMD
      return;
    }
    NativeUnixSocket.connect(socketAddress.getBytes(), fd, -1);
    this.remotePort = socketAddress.getPort();
  }

  @Override
  protected void disconnect() {
    try {
      NativeUnixSocket.disconnect(fd);
      connected.set(false);
      this.remotePort = 0;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  AFUNIXSocketCore getCore() {
    return core;
  }

  @Override
  protected FileDescriptor getFileDescriptor() {
    return core.fd;
  }

  boolean isClosed() {
    return core.isClosed();
  }

  @Override
  protected void bind(int lport, InetAddress laddr) throws SocketException {
    // not used; see bind(AFUNIXSocketAddress)
  }

  void bind(AFUNIXSocketAddress socketAddress) throws SocketException {
    if (socketAddress == AFUNIXSocketAddress.INTERNAL_DUMMY_BIND) { // NOPMD
      return;
    }
    try {
      NativeUnixSocket.bind(socketAddress.getBytes(), fd, 0);
      this.localPort = socketAddress.getPort();
    } catch (SocketException e) {
      throw e;
    } catch (IOException e) {
      throw (SocketException) new SocketException(e.getMessage()).initCause(e);
    }
  }

  @Override
  protected void receive(DatagramPacket p) throws IOException {
    recv(p, 0);
  }

  private void recv(DatagramPacket p, int options) throws IOException {
    int len = p.getLength();
    FileDescriptor fdesc = core.validFdOrException();

    ByteBuffer datagramPacketBuffer = core.getThreadLocalDirectByteBuffer(len);
    len = Math.min(len, datagramPacketBuffer.capacity());

    ByteBuffer socketAddressBuffer = AFUNIXSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
    int count = NativeUnixSocket.receive(fdesc, datagramPacketBuffer, 0, len, socketAddressBuffer,
        options, ancillaryDataSupport, socketTimeout.get());
    if (count > len || count < 0) {
      throw new IllegalStateException();
    }
    datagramPacketBuffer.limit(count);
    datagramPacketBuffer.rewind();
    datagramPacketBuffer.get(p.getData(), p.getOffset(), count);

    p.setLength(count);

    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofInternal(socketAddressBuffer);
    p.setAddress(addr == null ? null : addr.getInetAddress());
    p.setPort(remotePort);
  }

  @Override
  protected void send(DatagramPacket p) throws IOException {
    InetAddress addr = p.getAddress();
    ByteBuffer sendToBuf = null;
    if (addr != null) {
      byte[] addrBytes = AFUNIXInetAddress.unwrapAddress(addr); // NOTE port is not unchecked
      if (addrBytes != null) {
        sendToBuf = AFUNIXSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
        NativeUnixSocket.bytesToSockAddrUn(sendToBuf, addrBytes);
      }
    }
    FileDescriptor fdesc = core.validFdOrException();

    int len = p.getLength();
    ByteBuffer datagramPacketBuffer = core.getThreadLocalDirectByteBuffer(len);
    datagramPacketBuffer.clear();
    datagramPacketBuffer.put(p.getData(), p.getOffset(), p.getLength());
    NativeUnixSocket.send(fdesc, datagramPacketBuffer, 0, len, sendToBuf,
        NativeUnixSocket.OPT_NON_BLOCKING, ancillaryDataSupport);
  }

  @Override
  protected int peek(InetAddress i) throws IOException {
    throw new SocketException("Unsupported operation");
  }

  @Override
  protected int peekData(DatagramPacket p) throws IOException {
    recv(p, NativeUnixSocket.OPT_PEEK);
    return 0;
  }

  @Override
  protected byte getTTL() throws IOException {
    return (byte) (getTimeToLive() & 0xFF);
  }

  @Override
  protected void setTTL(byte ttl) throws IOException {
    // ignored
  }

  @Override
  protected int getTimeToLive() throws IOException {
    return 0;
  }

  @Override
  protected void setTimeToLive(int ttl) throws IOException {
    // ignored
  }

  @Override
  protected void join(InetAddress inetaddr) throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  protected void leave(InetAddress inetaddr) throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  protected void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  protected void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException {
    throw new SocketException("Unsupported");
  }

  @Override
  public Object getOption(int optID) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    FileDescriptor fdesc = core.validFdOrException();
    return AFUNIXSocketImpl.getOptionDefault(fdesc, optID, socketTimeout);
  }

  @Override
  public void setOption(int optID, Object value) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    FileDescriptor fdesc = core.validFdOrException();
    AFUNIXSocketImpl.setOptionDefault(fdesc, optID, value, socketTimeout);
  }

  AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return NativeUnixSocket.peerCredentials(fd, new AFUNIXSocketCredentials());
  }

  AFUNIXSocketAddress receive(ByteBuffer dst) throws IOException {
    return core.receive(dst);
  }

  int send(ByteBuffer src, SocketAddress target) throws IOException {
    return core.write(src, target, 0);
  }

  int read(ByteBuffer dst, ByteBuffer socketAddressBuffer) throws IOException {
    return core.read(dst, socketAddressBuffer, 0);
  }

  int write(ByteBuffer src) throws IOException {
    return core.write(src);
  }

  boolean isConnected() {
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

  boolean isBound() {
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

  void updatePorts(int local, int remote) {
    this.localPort = local;
    this.remotePort = remote;
  }

  AFUNIXSocketAddress getLocalSocketAddress() {
    return AFUNIXSocketAddress.getSocketAddress(getFileDescriptor(), false, localPort);
  }

  AFUNIXSocketAddress getRemoteSocketAddress() {
    return AFUNIXSocketAddress.getSocketAddress(getFileDescriptor(), true, remotePort);
  }
}
