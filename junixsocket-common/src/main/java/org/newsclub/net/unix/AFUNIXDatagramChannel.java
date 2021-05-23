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
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;

/**
 * A {@link DatagramChannel} implementation that works with AF_UNIX Unix domain sockets.
 * 
 * @author Christian Kohlschütter
 */
public final class AFUNIXDatagramChannel extends DatagramChannelShim implements
    AFUNIXSocketExtensions {
  AFUNIXDatagramChannel(AFUNIXDatagramSocket socket) {
    super(AFUNIXSelectorProvider.getInstance(), socket);
  }

  AFUNIXDatagramSocket getSocket() {
    return socket;
  }

  @Override
  public MembershipKey join(InetAddress group, NetworkInterface interf) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public MembershipKey join(InetAddress group, NetworkInterface interf, InetAddress source)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DatagramChannel bind(SocketAddress local) throws IOException {
    socket.bind(local);
    return this;
  }

  @Override
  public DatagramSocket socket() {
    return socket;
  }

  @Override
  public boolean isConnected() {
    return socket.isConnected();
  }

  @Override
  public DatagramChannel connect(SocketAddress remote) throws IOException {
    socket.connect(remote);
    return this;
  }

  @Override
  public DatagramChannel disconnect() throws IOException {
    socket.disconnect();
    return this;
  }

  @Override
  public SocketAddress getRemoteAddress() throws IOException {
    return socket.getRemoteSocketAddress();
  }

  @Override
  public SocketAddress getLocalAddress() throws IOException {
    return socket.getLocalSocketAddress();
  }

  @Override
  public SocketAddress receive(ByteBuffer dst) throws IOException {
    return socket.getAFImpl().receive(dst);
  }

  @Override
  public int send(ByteBuffer src, SocketAddress target) throws IOException {
    return socket.getAFImpl().send(src, target);
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    return socket.getAFImpl().read(dst);
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    // FIXME support more than one buffer for scatter-gather access
    return read(dsts[offset]);
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    return socket.getAFImpl().write(src);
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    // FIXME support more than one buffer for scatter-gather access
    return write(srcs[offset]);
  }

  @Override
  protected void implCloseSelectableChannel() throws IOException {
    getSocket().close();
  }

  @Override
  protected void implConfigureBlocking(boolean block) throws IOException {
    System.out.println("configureBlocking " + block);
  }

  @Override
  public FileDescriptor[] getReceivedFileDescriptors() throws IOException {
    return socket.getReceivedFileDescriptors();
  }

  @Override
  public void clearReceivedFileDescriptors() {
    socket.clearReceivedFileDescriptors();
  }

  @Override
  public void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    if (fdescs != null && fdescs.length > 0 && !isConnected()) {
      throw new SocketException("Not connected");
    }
    socket.setOutboundFileDescriptors(fdescs);
  }

  @Override
  public boolean hasOutboundFileDescriptors() {
    return socket.hasOutboundFileDescriptors();
  }

  @Override
  public AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return socket.getPeerCredentials();
  }

  @Override
  public int getAncillaryReceiveBufferSize() {
    return socket.getAncillaryReceiveBufferSize();
  }

  @Override
  public void setAncillaryReceiveBufferSize(int size) {
    socket.setAncillaryReceiveBufferSize(size);
  }

  @Override
  public void ensureAncillaryReceiveBufferSize(int minSize) {
    socket.ensureAncillaryReceiveBufferSize(minSize);
  }
}
