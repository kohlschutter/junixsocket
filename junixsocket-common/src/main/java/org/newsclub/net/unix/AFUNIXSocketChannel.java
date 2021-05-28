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
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Set;

public final class AFUNIXSocketChannel extends SocketChannel implements AFUNIXSocketExtensions {
  private final AFUNIXSocket afSocket;

  AFUNIXSocketChannel(AFUNIXSocket socket) {
    super(AFUNIXSelectorProvider.getInstance());
    this.afSocket = socket;
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
  public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      throw new UnsupportedOperationException("unsupported option");
    } else {
      afSocket.getAFImpl().setOption(optionId.intValue(), value);
    }
    return this;
  }

  @Override
  public Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }

  @Override
  public SocketChannel bind(SocketAddress local) throws IOException {
    afSocket.bind(local);
    return this;
  }

  @Override
  public SocketChannel shutdownInput() throws IOException {
    afSocket.getAFImpl().shutdownInput();
    return this;
  }

  @Override
  public SocketChannel shutdownOutput() throws IOException {
    afSocket.getAFImpl().shutdownOutput();
    return this;
  }

  @Override
  public Socket socket() {
    return afSocket;
  }

  @Override
  public boolean isConnected() {
    return afSocket.isConnected();
  }

  @Override
  public boolean isConnectionPending() {
    return false;
  }

  @Override
  public boolean connect(SocketAddress remote) throws IOException {
    return afSocket.connect0(remote, 0);
  }

  @Override
  public boolean finishConnect() throws IOException {
    if (isConnected()) {
      return true;
    }
    return isConnected();
  }

  @Override
  public SocketAddress getRemoteAddress() throws IOException {
    return afSocket.getRemoteSocketAddress();
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
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    // FIXME support more than one buffer for scatter-gather access
    return write(srcs[offset]);
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    return afSocket.getAFImpl().write(src);
  }

  @Override
  public SocketAddress getLocalAddress() throws IOException {
    return afSocket.getLocalSocketAddress();
  }

  @Override
  protected void implCloseSelectableChannel() throws IOException {
    afSocket.close();
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

  AFUNIXSocketCore getAFCore() {
    return afSocket.getAFImpl().getCore();
  }
}
