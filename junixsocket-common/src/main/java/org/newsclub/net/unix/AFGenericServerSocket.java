/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
import java.net.SocketException;

/**
 * The server part of a "unknown-type" socket.
 *
 * @author Christian Kohlschütter
 */
final class AFGenericServerSocket extends AFServerSocket<AFGenericSocketAddress> {
  AFGenericServerSocket(FileDescriptor fdObj) throws IOException {
    super(fdObj);
  }

  @Override
  protected AFServerSocketChannel<AFGenericSocketAddress> newChannel() {
    return new AFGenericServerSocketChannel(this);
  }

  @Override
  public AFGenericServerSocketChannel getChannel() {
    return (AFGenericServerSocketChannel) super.getChannel();
  }

  /**
   * Returns a new, unbound unkown-type {@link ServerSocket}.
   *
   * @return The new, unbound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFGenericServerSocket newInstance() throws IOException {
    return (AFGenericServerSocket) AFServerSocket.newInstance(AFGenericServerSocket::new);
  }

  static AFGenericServerSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    return (AFGenericServerSocket) AFServerSocket.newInstance(AFGenericServerSocket::new, fdObj,
        localPort, remotePort);
  }

  /**
   * Returns a new unkown-type {@link ServerSocket} that is bound to the given
   * {@link AFGenericSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFGenericServerSocket bindOn(final AFGenericSocketAddress addr) throws IOException {
    return (AFGenericServerSocket) AFServerSocket.bindOn(AFGenericServerSocket::new, addr);
  }

  /**
   * Returns a new unkown-type {@link ServerSocket} that is bound to the given
   * {@link AFSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @param deleteOnClose If {@code true}, the socket file (if the address points to a file) will be
   *          deleted upon {@link #close}.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFGenericServerSocket bindOn(final AFGenericSocketAddress addr,
      boolean deleteOnClose) throws IOException {
    return (AFGenericServerSocket) AFServerSocket.bindOn(AFGenericServerSocket::new, addr,
        deleteOnClose);
  }

  /**
   * Returns a new, <em>unbound</em> unkown-type {@link ServerSocket} that will always bind to the
   * given address, regardless of any socket address used in a call to <code>bind</code>.
   *
   * @param forceAddr The address to use.
   * @return The new, yet unbound {@link AFServerSocket}.
   * @throws IOException if an exception occurs.
   */
  public static AFGenericServerSocket forceBindOn(final AFGenericSocketAddress forceAddr)
      throws IOException {
    return (AFGenericServerSocket) AFServerSocket.forceBindOn(AFGenericServerSocket::new,
        forceAddr);
  }

  @Override
  protected AFSocketImpl<AFGenericSocketAddress> newImpl(FileDescriptor fdObj)
      throws SocketException {
    return new AFGenericSocketImpl(fdObj);
  }

  @Override
  protected AFSocket<AFGenericSocketAddress> newSocketInstance() throws IOException {
    return AFGenericSocket.newInstance();
  }

  @Override
  public AFGenericSocket accept() throws IOException {
    return (AFGenericSocket) super.accept();
  }
}
