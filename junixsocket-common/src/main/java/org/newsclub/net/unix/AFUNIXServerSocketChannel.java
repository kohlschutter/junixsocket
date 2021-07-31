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
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.util.Set;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

public final class AFUNIXServerSocketChannel extends ServerSocketChannel implements
    FileDescriptorAccess {
  private final AFUNIXServerSocket afSocket;

  AFUNIXServerSocketChannel(AFUNIXServerSocket socket) {
    super(AFUNIXSelectorProvider.getInstance());
    this.afSocket = socket;
  }

  public static AFUNIXServerSocketChannel open() throws IOException {
    return AFUNIXSelectorProvider.provider().openServerSocketChannel();
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
  public <T> AFUNIXServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
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
  public AFUNIXServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
    afSocket.bind(local, backlog);
    return this;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public AFUNIXServerSocket socket() {
    return afSocket;
  }

  @Override
  public AFUNIXSocketChannel accept() throws IOException {
    AFUNIXSocket socket = afSocket.accept();
    return socket == null ? null : socket.getChannel();
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

  AFUNIXSocketCore getAFCore() {
    return afSocket.getAFImpl().getCore();
  }

  @Override
  public FileDescriptor getFileDescriptor() throws IOException {
    return afSocket.getFileDescriptor();
  }
}
