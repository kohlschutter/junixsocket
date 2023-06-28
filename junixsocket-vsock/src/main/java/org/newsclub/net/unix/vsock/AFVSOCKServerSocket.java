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
package org.newsclub.net.unix.vsock;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;

import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFSocketImpl;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

/**
 * The server part of an {@code AF_VSOCK} socket.
 *
 * @author Christian Kohlschütter
 */
public class AFVSOCKServerSocket extends AFServerSocket<AFVSOCKSocketAddress> {
  AFVSOCKServerSocket(FileDescriptor fdObj) throws IOException {
    super(fdObj);
  }

  @Override
  protected AFServerSocketChannel<AFVSOCKSocketAddress> newChannel() {
    return new AFVSOCKServerSocketChannel(this);
  }

  @Override
  public AFVSOCKServerSocketChannel getChannel() {
    return (AFVSOCKServerSocketChannel) super.getChannel();
  }

  /**
   * Returns a new, unbound AF_VSOCK {@link ServerSocket}.
   *
   * @return The new, unbound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFVSOCKServerSocket newInstance() throws IOException {
    return (AFVSOCKServerSocket) AFServerSocket.newInstance(AFVSOCKServerSocket::new);
  }

  static AFVSOCKServerSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    return (AFVSOCKServerSocket) AFServerSocket.newInstance(AFVSOCKServerSocket::new, fdObj,
        localPort, remotePort);
  }

  /**
   * Returns a new AF_VSOCK {@link ServerSocket} that is bound to the given
   * {@link AFVSOCKSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFVSOCKServerSocket bindOn(final AFVSOCKSocketAddress addr) throws IOException {
    return (AFVSOCKServerSocket) AFServerSocket.bindOn(AFVSOCKServerSocket::new, addr);
  }

  /**
   * Returns a new AF_VSOCK {@link ServerSocket} that is bound to the given {@link AFSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @param deleteOnClose If {@code true}, the socket file (if the address points to a file) will be
   *          deleted upon {@link #close}.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFVSOCKServerSocket bindOn(final AFVSOCKSocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return (AFVSOCKServerSocket) AFServerSocket.bindOn(AFVSOCKServerSocket::new, addr,
        deleteOnClose);
  }

  /**
   * Returns a new, <em>unbound</em> AF_VSOCK {@link ServerSocket} that will always bind to the
   * given address, regardless of any socket address used in a call to <code>bind</code>.
   *
   * @param forceAddr The address to use.
   * @return The new, yet unbound {@link AFServerSocket}.
   * @throws IOException if an exception occurs.
   */
  public static AFVSOCKServerSocket forceBindOn(final AFVSOCKSocketAddress forceAddr)
      throws IOException {
    return (AFVSOCKServerSocket) AFServerSocket.forceBindOn(AFVSOCKServerSocket::new, forceAddr);
  }

  @Override
  protected AFSocketImpl<AFVSOCKSocketAddress> newImpl(FileDescriptor fdObj)
      throws SocketException {
    return new AFVSOCKSocketImpl(fdObj);
  }

  @Override
  protected AFSocket<AFVSOCKSocketAddress> newSocketInstance() throws IOException {
    return AFVSOCKSocket.newInstance();
  }

  @Override
  public AFVSOCKSocket accept() throws IOException {
    return (AFVSOCKSocket) super.accept();
  }
}
