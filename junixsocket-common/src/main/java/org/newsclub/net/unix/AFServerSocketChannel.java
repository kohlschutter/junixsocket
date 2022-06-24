/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A selectable channel for stream-oriented listening sockets.
 * 
 * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
 * @author Christian Kohlschütter
 */
public abstract class AFServerSocketChannel<A extends AFSocketAddress> extends ServerSocketChannel
    implements FileDescriptorAccess {
  private final @NonNull AFServerSocket<A> afSocket;

  /**
   * Creates a new {@link AFServerSocketChannel} instance.
   * 
   * @param socket The corresponding {@link ServerSocket}.
   * @param sp The corresponding {@link SelectorProvider}.
   */
  @SuppressWarnings("null")
  protected AFServerSocketChannel(AFServerSocket<A> socket, AFSelectorProvider<A> sp) {
    super(sp);
    this.afSocket = Objects.requireNonNull(socket);
  }

  // CPD-OFF

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getOption(SocketOption<T> name) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      return getAFCore().getOption((AFSocketOption<T>) name);
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      throw new UnsupportedOperationException("unsupported option");
    } else {
      return (T) afSocket.getAFImpl().getOption(optionId.intValue());
    }
  }

  @Override
  public <T> AFServerSocketChannel<A> setOption(SocketOption<T> name, T value) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      getAFCore().setOption((AFSocketOption<T>) name, value);
      return this;
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      throw new UnsupportedOperationException("unsupported option");
    } else {
      afSocket.getAFImpl().setOption(optionId.intValue(), value);
    }
    return this;
  }

  @Override
  public final Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }

  // CPD-ON

  @Override
  public final AFServerSocketChannel<A> bind(SocketAddress local, int backlog) throws IOException {
    afSocket.bind(local, backlog);
    return this;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public final AFServerSocket<A> socket() {
    return afSocket;
  }

  @Override
  public AFSocketChannel<A> accept() throws IOException {
    AFSocket<A> socket = afSocket.accept1(false);
    return socket == null ? null : socket.getChannel();
  }

  @Override
  public final AFSocketAddress getLocalAddress() throws IOException {
    return afSocket.getLocalSocketAddress();
  }

  /**
   * Checks if the local socket address returned by {@link #getLocalAddress()} is still valid.
   * 
   * The address is no longer valid if the server socket has been closed, {@code null}, or another
   * server socket has been bound on that address.
   * 
   * @return {@code true} iff still valid.
   */
  public final boolean isLocalSocketAddressValid() {
    return afSocket.isLocalSocketAddressValid();
  }

  @Override
  protected final void implCloseSelectableChannel() throws IOException {
    afSocket.close();
  }

  @Override
  protected final void implConfigureBlocking(boolean block) throws IOException {
    getAFCore().implConfigureBlocking(block);
  }

  final AFSocketCore getAFCore() {
    return afSocket.getAFImpl().getCore();
  }

  @Override
  public final FileDescriptor getFileDescriptor() throws IOException {
    return afSocket.getFileDescriptor();
  }

  /**
   * Checks if this {@link AFServerSocketChannel}'s file should be removed upon {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @return {@code true} if an attempt is made to delete the socket file upon {@link #close()}.
   */
  public final boolean isDeleteOnClose() {
    return socket().isDeleteOnClose();
  }

  /**
   * Enables/disables deleting this {@link AFServerSocketChannel}'s file (or other resource type)
   * upon {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @param b Enabled if {@code true}.
   */
  public final void setDeleteOnClose(boolean b) {
    socket().setDeleteOnClose(b);
  }
}
