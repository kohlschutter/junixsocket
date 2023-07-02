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
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A {@link DatagramChannel} implementation that works with junixsocket.
 *
 * @author Christian Kohlschütter
 * @param <A> The supported address type.
 */
public abstract class AFDatagramChannel<A extends AFSocketAddress> extends DatagramChannel
    implements AFSomeSocket, AFSocketExtensions {
  private final AFDatagramSocket<A> afSocket;

  /**
   * Creates a new {@link AFDatagramChannel} instance.
   *
   * @param selectorProvider The corresponding {@link SelectorProvider}.
   * @param socket The corresponding {@link Socket}.
   */
  protected AFDatagramChannel(AFSelectorProvider<A> selectorProvider, AFDatagramSocket<A> socket) {
    super(selectorProvider);
    this.afSocket = socket;
  }

  /**
   * Returns the corresponding {@link Socket}.
   *
   * @return The socket.
   */
  protected final AFDatagramSocket<A> getAFSocket() {
    return afSocket;
  }

  // CPD-OFF

  @Override
  public final MembershipKey join(InetAddress group, NetworkInterface interf) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public final MembershipKey join(InetAddress group, NetworkInterface interf, InetAddress source)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public final AFDatagramChannel<A> bind(SocketAddress local) throws IOException {
    afSocket.bind(local);
    return this;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public final AFDatagramSocket<A> socket() {
    return afSocket;
  }

  /**
   * Returns the binding state of the socket.
   *
   * @return true if the socket successfully bound to an address
   */
  public final boolean isBound() {
    return afSocket.isBound();
  }

  @Override
  public final boolean isConnected() {
    return afSocket.isConnected();
  }

  @Override
  public final AFDatagramChannel<A> connect(SocketAddress remote) throws IOException {
    afSocket.connect(remote);
    return this;
  }

  @Override
  public final AFDatagramChannel<A> disconnect() throws IOException {
    afSocket.disconnect();
    return this;
  }

  @Override
  public final @Nullable A getRemoteAddress() throws IOException {
    return getRemoteSocketAddress();
  }

  @Override
  public final @Nullable A getRemoteSocketAddress() {
    return afSocket.getRemoteSocketAddress();
  }

  @Override
  public final @Nullable A getLocalAddress() throws IOException {
    return getLocalSocketAddress();
  }

  @Override
  public final @Nullable A getLocalSocketAddress() {
    return afSocket.getLocalSocketAddress();
  }

  @Override
  public final A receive(ByteBuffer dst) throws IOException {
    return afSocket.getAFImpl().receive(dst);
  }

  @Override
  public final int send(ByteBuffer src, SocketAddress target) throws IOException {
    return afSocket.getAFImpl().send(src, target);
  }

  @Override
  public final int read(ByteBuffer dst) throws IOException {
    return afSocket.getAFImpl().read(dst, null);
  }

  @Override
  public final long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    // FIXME support more than one buffer for scatter-gather access
    return read(dsts[offset]);
  }

  @Override
  public final int write(ByteBuffer src) throws IOException {
    return afSocket.getAFImpl().write(src);
  }

  @Override
  public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    // FIXME support more than one buffer for scatter-gather access
    return write(srcs[offset]);
  }

  @Override
  protected final void implCloseSelectableChannel() throws IOException {
    getAFSocket().close();
  }

  @Override
  protected final void implConfigureBlocking(boolean block) throws IOException {
    getAFCore().implConfigureBlocking(block);
  }

  @Override
  public final int getAncillaryReceiveBufferSize() {
    return afSocket.getAncillaryReceiveBufferSize();
  }

  @Override
  public final void setAncillaryReceiveBufferSize(int size) {
    afSocket.setAncillaryReceiveBufferSize(size);
  }

  @Override
  public final void ensureAncillaryReceiveBufferSize(int minSize) {
    afSocket.ensureAncillaryReceiveBufferSize(minSize);
  }

  @Override
  public final <T> AFDatagramChannel<A> setOption(SocketOption<T> name, T value)
      throws IOException {
    if (name instanceof AFSocketOption<?>) {
      getAFCore().setOption((AFSocketOption<T>) name, value);
      return this;
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      throw new UnsupportedOperationException("unsupported option");
    } else {
      afSocket.getAFImpl().setOption(optionId, value);
    }
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> T getOption(SocketOption<T> name) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      return getAFCore().getOption((AFSocketOption<T>) name);
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      throw new UnsupportedOperationException("unsupported option");
    } else {
      return (T) afSocket.getAFImpl().getOption(optionId);
    }
  }

  @Override
  public final Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }

  final AFSocketCore getAFCore() {
    return afSocket.getAFImpl().getCore();
  }

  @Override
  public final FileDescriptor getFileDescriptor() throws IOException {
    return afSocket.getFileDescriptor();
  }

  /**
   * Checks if this {@link DatagramSocket}'s bound filename should be removed upon {@link #close()}.
   *
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   *
   * @return {@code true} if an attempt is made to delete the socket file upon {@link #close()}.
   */
  public final boolean isDeleteOnClose() {
    return afSocket.isDeleteOnClose();
  }

  /**
   * Enables/disables deleting this {@link DatagramSocket}'s bound filename upon {@link #close()}.
   *
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   *
   * @param b Enabled if {@code true}.
   */
  public final void setDeleteOnClose(boolean b) {
    afSocket.setDeleteOnClose(b);
  }
}
