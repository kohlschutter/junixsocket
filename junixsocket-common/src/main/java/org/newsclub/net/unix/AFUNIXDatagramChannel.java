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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Set;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A {@link DatagramChannel} implementation that works with AF_UNIX Unix domain sockets.
 * 
 * @author Christian Kohlschütter
 */
public final class AFUNIXDatagramChannel extends DatagramChannel implements AFUNIXSomeSocket,
    AFUNIXSocketExtensions {
  private final AFUNIXDatagramSocket afSocket;

  AFUNIXDatagramChannel(AFUNIXDatagramSocket socket) {
    super(AFUNIXSelectorProvider.getInstance());
    this.afSocket = socket;
  }

  AFUNIXDatagramSocket getAFSocket() {
    return afSocket;
  }

  public static AFUNIXDatagramChannel open() throws IOException {
    return AFUNIXSelectorProvider.provider().openDatagramChannel();
  }

  public static AFUNIXDatagramChannel open(ProtocolFamily family) throws IOException {
    return AFUNIXSelectorProvider.provider().openDatagramChannel(family);
  }

  // CPD-OFF

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
  public AFUNIXDatagramChannel bind(SocketAddress local) throws IOException {
    afSocket.bind(local);
    return this;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public AFUNIXDatagramSocket socket() {
    return afSocket;
  }

  public boolean isBound() {
    return afSocket.isBound();
  }

  @Override
  public boolean isConnected() {
    return afSocket.isConnected();
  }

  @Override
  public AFUNIXDatagramChannel connect(SocketAddress remote) throws IOException {
    afSocket.connect(remote);
    return this;
  }

  @Override
  public AFUNIXDatagramChannel disconnect() throws IOException {
    afSocket.disconnect();
    return this;
  }

  @Override
  public AFUNIXSocketAddress getRemoteAddress() throws IOException {
    return afSocket.getRemoteSocketAddress();
  }

  @Override
  public AFUNIXSocketAddress getLocalAddress() throws IOException {
    return afSocket.getLocalSocketAddress();
  }

  @Override
  public AFUNIXSocketAddress receive(ByteBuffer dst) throws IOException {
    return afSocket.getAFImpl().receive(dst);
  }

  @Override
  public int send(ByteBuffer src, SocketAddress target) throws IOException {
    return afSocket.getAFImpl().send(src, target);
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    return afSocket.getAFImpl().read(dst, null);
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
    return afSocket.getAFImpl().write(src);
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
    getAFSocket().close();
  }

  @Override
  protected void implConfigureBlocking(boolean block) throws IOException {
    getAFCore().implConfigureBlocking(block);
  }

  @Override
  public FileDescriptor[] getReceivedFileDescriptors() throws IOException {
    return afSocket.getReceivedFileDescriptors();
  }

  @Override
  public void clearReceivedFileDescriptors() {
    afSocket.clearReceivedFileDescriptors();
  }

  @Override
  public void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    if (fdescs != null && fdescs.length > 0 && !isConnected()) {
      throw new SocketException("Not connected");
    }
    afSocket.setOutboundFileDescriptors(fdescs);
  }

  @Override
  public boolean hasOutboundFileDescriptors() {
    return afSocket.hasOutboundFileDescriptors();
  }

  @Override
  public AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return afSocket.getPeerCredentials();
  }

  @Override
  public int getAncillaryReceiveBufferSize() {
    return afSocket.getAncillaryReceiveBufferSize();
  }

  @Override
  public void setAncillaryReceiveBufferSize(int size) {
    afSocket.setAncillaryReceiveBufferSize(size);
  }

  @Override
  public void ensureAncillaryReceiveBufferSize(int minSize) {
    afSocket.ensureAncillaryReceiveBufferSize(minSize);
  }

  @Override
  public <T> AFUNIXDatagramChannel setOption(SocketOption<T> name, T value) throws IOException {
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      throw new UnsupportedOperationException("unsupported option");
    } else {
      afSocket.getAFImpl().setOption(optionId.intValue(), value);
    }
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getOption(SocketOption<T> name) throws IOException {
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      throw new UnsupportedOperationException("unsupported option");
    } else {
      return (T) afSocket.getAFImpl().getOption(optionId.intValue());
    }
  }

  @Override
  public Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }

  AFUNIXSocketCore getAFCore() {
    return afSocket.getAFImpl().getCore();
  }

  @Override
  public FileDescriptor getFileDescriptor() throws IOException {
    return afSocket.getFileDescriptor();
  }

  public boolean isDeleteOnClose() {
    return afSocket.isDeleteOnClose();
  }

  public void setDeleteOnClose(boolean b) {
    afSocket.setDeleteOnClose(b);
  }
}
