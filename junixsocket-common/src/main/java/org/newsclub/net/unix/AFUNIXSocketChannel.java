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
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AFUNIXSocketChannel extends SocketChannel implements AFUNIXSomeSocket,
    AFUNIXSocketExtensions {
  private final AFUNIXSocket afSocket;
  private final AtomicBoolean connectPending = new AtomicBoolean(false);

  AFUNIXSocketChannel(AFUNIXSocket socket) {
    super(AFUNIXSelectorProvider.getInstance());
    this.afSocket = socket;
  }

  public static AFUNIXSocketChannel open() throws IOException {
    return AFUNIXSelectorProvider.provider().openSocketChannel();
  }

  public static AFUNIXSocketChannel open(SocketAddress remote) throws IOException {
    @SuppressWarnings("resource")
    AFUNIXSocketChannel sc = open();
    try {
      sc.connect(remote);
    } catch (Throwable x) { // NOPMD
      try {
        sc.close();
      } catch (Throwable suppressed) { // NOPMD
        x.addSuppressed(suppressed);
      }
      throw x;
    }
    assert sc.isConnected();
    return sc;
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
  public <T> AFUNIXSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
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
  public AFUNIXSocketChannel bind(SocketAddress local) throws IOException {
    afSocket.bind(local);
    return this;
  }

  @Override
  public AFUNIXSocketChannel shutdownInput() throws IOException {
    afSocket.getAFImpl().shutdownInput();
    return this;
  }

  @Override
  public AFUNIXSocketChannel shutdownOutput() throws IOException {
    afSocket.getAFImpl().shutdownOutput();
    return this;
  }

  @Override
  public AFUNIXSocket socket() {
    return afSocket;
  }

  @Override
  public boolean isConnected() {
    boolean connected = afSocket.isConnected();
    if (connected) {
      connectPending.set(false);
    }
    return connected;
  }

  @Override
  public boolean isConnectionPending() {
    return connectPending.get();
  }

  @Override
  public boolean connect(SocketAddress remote) throws IOException {
    boolean connected = afSocket.connect0(remote, 0);
    if (!connected) {
      connectPending.set(true);
    }
    return connected;
  }

  @Override
  public boolean finishConnect() throws IOException {
    if (isConnected()) {
      return true;
    } else if (!isConnectionPending()) {
      return false;
    }

    boolean connected = NativeUnixSocket.finishConnect(afSocket.getFileDescriptor())
        || isConnected();
    if (connected) {
      connectPending.set(false);
    }
    return connected;
  }

  @Override
  public AFUNIXSocketAddress getRemoteAddress() throws IOException {
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
  public AFUNIXSocketAddress getLocalAddress() throws IOException {
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

  @Override
  public FileDescriptor getFileDescriptor() throws IOException {
    return afSocket.getFileDescriptor();
  }

  @Override
  public String toString() {
    return super.toString() + afSocket.toStringSuffix();
  }

}
