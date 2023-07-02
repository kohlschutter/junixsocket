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
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A selectable channel for stream-oriented connecting sockets.
 *
 * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
 * @author Christian Kohlschütter
 */
public abstract class AFSocketChannel<A extends AFSocketAddress> extends SocketChannel implements
    AFSomeSocket, AFSocketExtensions {
  private final @NonNull AFSocket<A> afSocket;
  private final AtomicBoolean connectPending = new AtomicBoolean(false);

  /**
   * Creates a new socket channel for the given socket, using the given {@link SelectorProvider}.
   *
   * @param socket The socket.
   * @param sp The {@link SelectorProvider}.
   */
  @SuppressWarnings("null")
  protected AFSocketChannel(AFSocket<A> socket, AFSelectorProvider<A> sp) {
    super(sp);
    this.afSocket = Objects.requireNonNull(socket);
  }

  /**
   * Returns the corresponding {@link AFSocket}.
   *
   * @return The corresponding socket.
   */
  protected final AFSocket<A> getAFSocket() {
    return afSocket;
  }

  /**
   * A reference to a method that provides an {@link AFSocket} instance.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   */
  @FunctionalInterface
  protected interface AFSocketSupplier<A extends AFSocketAddress> {
    /**
     * Returns a new {@link AFSocket} instance.
     *
     * @return The instance.
     * @throws IOException on error.
     */
    AFSocket<A> newInstance() throws IOException;
  }

  /**
   * Opens a socket channel.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @param supplier The AFSocketChannel constructor.
   *
   * @return The new channel
   * @throws IOException on error.
   */
  protected static final <A extends AFSocketAddress> AFSocketChannel<A> open(
      AFSocketSupplier<A> supplier) throws IOException {
    return supplier.newInstance().getChannel();
  }

  /**
   * Opens a socket channel, connecting to the given socket address.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @param remote The socket address to connect to.
   * @param supplier The AFSocketChannel constructor.
   * @return The new channel
   * @throws IOException on error.
   */
  protected static final <A extends AFSocketAddress> AFSocketChannel<A> open(
      AFSocketSupplier<A> supplier, SocketAddress remote) throws IOException {
    @SuppressWarnings("resource")
    AFSocketChannel<A> sc = open(supplier);
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
  public final <T> AFSocketChannel<A> setOption(SocketOption<T> name, T value) throws IOException {
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

  @Override
  public final Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }

  @Override
  public final AFSocketChannel<A> bind(SocketAddress local) throws IOException {
    afSocket.bind(local);
    return this;
  }

  @Override
  public final AFSocketChannel<A> shutdownInput() throws IOException {
    afSocket.getAFImpl().shutdownInput();
    return this;
  }

  @Override
  public final AFSocketChannel<A> shutdownOutput() throws IOException {
    afSocket.getAFImpl().shutdownOutput();
    return this;
  }

  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public final AFSocket<A> socket() {
    return afSocket;
  }

  @Override
  public final boolean isConnected() {
    boolean connected = afSocket.isConnected();
    if (connected) {
      connectPending.set(false);
    }
    return connected;
  }

  @Override
  public final boolean isConnectionPending() {
    return connectPending.get();
  }

  @Override
  public final boolean connect(SocketAddress remote) throws IOException {
    boolean connected = afSocket.connect0(remote, 0);
    if (!connected) {
      connectPending.set(true);
    }
    return connected;
  }

  @Override
  public final boolean finishConnect() throws IOException {
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
  public final A getRemoteAddress() throws IOException {
    return getRemoteSocketAddress();
  }

  @Override
  public final A getRemoteSocketAddress() {
    return afSocket.getRemoteSocketAddress();
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
  public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    // FIXME support more than one buffer for scatter-gather access
    return write(srcs[offset]);
  }

  @Override
  public final int write(ByteBuffer src) throws IOException {
    return afSocket.getAFImpl().write(src);
  }

  @Override
  public final A getLocalAddress() throws IOException {
    return getLocalSocketAddress();
  }

  @Override
  public final A getLocalSocketAddress() {
    return afSocket.getLocalSocketAddress();
  }

  @Override
  protected final void implCloseSelectableChannel() throws IOException {
    afSocket.close();
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

  final AFSocketCore getAFCore() {
    return afSocket.getAFImpl().getCore();
  }

  @Override
  public final FileDescriptor getFileDescriptor() throws IOException {
    return afSocket.getFileDescriptor();
  }

  @Override
  public final String toString() {
    return super.toString() + afSocket.toStringSuffix();
  }
}
