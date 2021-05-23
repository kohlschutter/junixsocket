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
import java.net.DatagramPacket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

final class AFUNIXDatagramSocketImpl extends DatagramSocketImpl {
  @SuppressWarnings("PMD.UseDiamondOperator") // not in Java 7
  private static final ThreadLocal<ByteBuffer> DATAGRAMPACKET_BUFFER_TL =
      new ThreadLocal<ByteBuffer>();

  private final DatagramSocketState state;
  final AncillaryDataSupport ancillaryDataSupport = new AncillaryDataSupport();

  /**
   * When the {@link AFUNIXSocketImpl} becomes unreachable (but not yet closed), we must ensure that
   * the underlying socket and all related file descriptors are closed.
   *
   * @author Christian Kohlschütter
   */
  private static class DatagramSocketState extends SocketStateBase {
    private DatagramSocketState(AFUNIXDatagramSocketImpl observed, Closeable additionalCloseable)
        throws IOException {
      super(observed, additionalCloseable);
    }
  }

  AFUNIXDatagramSocketImpl() throws IOException {
    super();
    this.state = new DatagramSocketState(this, ancillaryDataSupport);
    this.fd = state.fd;
  }

  @Override
  protected void create() throws SocketException {
    if (isClosed()) {
      throw new SocketException("Already closed");
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
    state.runCleaner();
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
  }

  @Override
  protected void disconnect() {
    try {
      NativeUnixSocket.disconnect(fd);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected FileDescriptor getFileDescriptor() {
    return state.fd;
  }

  boolean isClosed() {
    return state.isClosed();
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
    FileDescriptor fdesc = state.validFdOrException();

    int count;

    ByteBuffer datagramPacketBuffer = DATAGRAMPACKET_BUFFER_TL.get();
    if (datagramPacketBuffer == null || len > datagramPacketBuffer.capacity()) {
      datagramPacketBuffer = ByteBuffer.allocateDirect(len);
      DATAGRAMPACKET_BUFFER_TL.set(datagramPacketBuffer);
    }
    ByteBuffer socketAddressBuffer = AFUNIXSocketAddress.SOCKETADDRESS_BUFFER_TL.get();

    count = NativeUnixSocket.receive(fdesc, datagramPacketBuffer, len, socketAddressBuffer, options,
        ancillaryDataSupport);
    if (count > len) {
      throw new IllegalStateException();
    }
    datagramPacketBuffer.limit(count);
    datagramPacketBuffer.rewind();
    datagramPacketBuffer.get(p.getData(), p.getOffset(), count);

    p.setLength(count);

    p.setAddress(AFUNIXSocketAddress.ofInternal(socketAddressBuffer).getInetAddress());
    p.setPort(0);
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
    FileDescriptor fdesc = state.validFdOrException();

    ByteBuffer datagramPacketBuffer = DATAGRAMPACKET_BUFFER_TL.get();
    int len = p.getLength();
    if (datagramPacketBuffer == null || len > datagramPacketBuffer.capacity()) {
      datagramPacketBuffer = ByteBuffer.allocateDirect(len);
      DATAGRAMPACKET_BUFFER_TL.set(datagramPacketBuffer);
    }
    datagramPacketBuffer.clear();
    datagramPacketBuffer.put(p.getData(), p.getOffset(), p.getLength());
    NativeUnixSocket.send(fdesc, datagramPacketBuffer, len, sendToBuf,
        NativeUnixSocket.OPT_NON_BLOCKING, ancillaryDataSupport);
  }

  @Override
  protected int peek(InetAddress i) throws IOException {
    throw new IOException("Unsupported operation");
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

    FileDescriptor fdesc = state.validFdOrException();
    return AFUNIXSocketImpl.getOptionDefault(fdesc, optID, null);
  }

  @Override
  public void setOption(int optID, Object value) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }

    FileDescriptor fdesc = state.validFdOrException();
    AFUNIXSocketImpl.setOptionDefault(fdesc, optID, value, null);
  }

  AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return NativeUnixSocket.peerCredentials(fd, new AFUNIXSocketCredentials());
  }

  SocketAddress receive(ByteBuffer dst) throws IOException {
    int options = 0;
    FileDescriptor fdesc = state.validFdOrException();
    ByteBuffer socketAddressBuffer = AFUNIXSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
    NativeUnixSocket.receive(fdesc, dst, dst.remaining(), socketAddressBuffer, options,
        ancillaryDataSupport);
    return AFUNIXSocketAddress.ofInternal(socketAddressBuffer);
  }

  public int read(ByteBuffer dst) throws IOException {
    int remaining = dst.remaining();
    if (remaining == 0) {
      return 0;
    }
    int options = 0;
    FileDescriptor fdesc = state.validFdOrException();
    int pos = dst.position();
    int count = NativeUnixSocket.receive(fdesc, dst, remaining, null, options,
        ancillaryDataSupport);
    if (count >= 0) {
      dst.position(pos + count);
    }
    return count;
  }

  int send(ByteBuffer src, SocketAddress target) throws IOException {
    FileDescriptor fdesc = state.validFdOrException();
    ByteBuffer addressTo;
    if (target == null) {
      addressTo = null;
    } else {
      addressTo = AFUNIXSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
      AFUNIXSocketAddress.unwrapAddressDirectBufferInternal(addressTo, target);
    }

    // accept "send buffer overflow" as packet loss
    // and don't retry (which would slow things down quite a bit)
    final int options = NativeUnixSocket.OPT_NON_BLOCKING;

    int written = NativeUnixSocket.send(fdesc, src, src.remaining(), addressTo, options,
        ancillaryDataSupport);
    if (written > 0) {
      src.position(src.position() + written);
    }

    return written;
  }

  public int write(ByteBuffer src) throws IOException {
    return send(src, null);
  }
}
