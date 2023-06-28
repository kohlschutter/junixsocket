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
package org.newsclub.net.unix.tipc;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;

import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFSocketImpl;
import org.newsclub.net.unix.AFTIPCSocketAddress;

/**
 * The server part of an {@code AF_TIPC} socket.
 *
 * @author Christian Kohlschütter
 */
public class AFTIPCServerSocket extends AFServerSocket<AFTIPCSocketAddress> {
  AFTIPCServerSocket(FileDescriptor fdObj) throws IOException {
    super(fdObj);
  }

  @Override
  protected AFServerSocketChannel<AFTIPCSocketAddress> newChannel() {
    return new AFTIPCServerSocketChannel(this);
  }

  @Override
  public AFTIPCServerSocketChannel getChannel() {
    return (AFTIPCServerSocketChannel) super.getChannel();
  }

  /**
   * Returns a new, unbound AF_TIPC {@link ServerSocket}.
   *
   * @return The new, unbound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFTIPCServerSocket newInstance() throws IOException {
    return (AFTIPCServerSocket) AFServerSocket.newInstance(AFTIPCServerSocket::new);
  }

  static AFTIPCServerSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    return (AFTIPCServerSocket) AFServerSocket.newInstance(AFTIPCServerSocket::new, fdObj,
        localPort, remotePort);
  }

  /**
   * Returns a new AF_TIPC {@link ServerSocket} that is bound to the given
   * {@link AFTIPCSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFTIPCServerSocket bindOn(final AFTIPCSocketAddress addr) throws IOException {
    return (AFTIPCServerSocket) AFServerSocket.bindOn(AFTIPCServerSocket::new, addr);
  }

  /**
   * Returns a new AF_TIPC {@link ServerSocket} that is bound to the given {@link AFSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @param deleteOnClose If {@code true}, the socket file (if the address points to a file) will be
   *          deleted upon {@link #close}.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFTIPCServerSocket bindOn(final AFTIPCSocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return (AFTIPCServerSocket) AFServerSocket.bindOn(AFTIPCServerSocket::new, addr, deleteOnClose);
  }

  /**
   * Returns a new, <em>unbound</em> AF_TIPC {@link ServerSocket} that will always bind to the given
   * address, regardless of any socket address used in a call to <code>bind</code>.
   *
   * @param forceAddr The address to use.
   * @return The new, yet unbound {@link AFServerSocket}.
   * @throws IOException if an exception occurs.
   */
  public static AFTIPCServerSocket forceBindOn(final AFTIPCSocketAddress forceAddr)
      throws IOException {
    return (AFTIPCServerSocket) AFServerSocket.forceBindOn(AFTIPCServerSocket::new, forceAddr);
  }

  @Override
  protected AFSocketImpl<AFTIPCSocketAddress> newImpl(FileDescriptor fdObj) throws SocketException {
    return new AFTIPCSocketImpl(fdObj);
  }

  @Override
  protected AFSocket<AFTIPCSocketAddress> newSocketInstance() throws IOException {
    return AFTIPCSocket.newInstance();
  }

  @Override
  public AFTIPCSocket accept() throws IOException {
    return (AFTIPCSocket) super.accept();
  }
}
