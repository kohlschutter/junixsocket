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
package org.newsclub.net.unix.darwin.system;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;

import org.newsclub.net.unix.AFSYSTEMSocketAddress;
import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFSocketImpl;

/**
 * The server part of an {@code AF_SYSTEM} socket.
 *
 * @author Christian Kohlschütter
 */
public class AFSYSTEMServerSocket extends AFServerSocket<AFSYSTEMSocketAddress> {
  AFSYSTEMServerSocket(FileDescriptor fdObj) throws IOException {
    super(fdObj);
  }

  @Override
  protected AFServerSocketChannel<AFSYSTEMSocketAddress> newChannel() {
    return new AFSYSTEMServerSocketChannel(this);
  }

  @Override
  public AFSYSTEMServerSocketChannel getChannel() {
    return (AFSYSTEMServerSocketChannel) super.getChannel();
  }

  /**
   * Returns a new, unbound AF_SYSTEM {@link ServerSocket}.
   *
   * @return The new, unbound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFSYSTEMServerSocket newInstance() throws IOException {
    return (AFSYSTEMServerSocket) AFServerSocket.newInstance(AFSYSTEMServerSocket::new);
  }

  static AFSYSTEMServerSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    return (AFSYSTEMServerSocket) AFServerSocket.newInstance(AFSYSTEMServerSocket::new, fdObj,
        localPort, remotePort);
  }

  /**
   * Returns a new AF_SYSTEM {@link ServerSocket} that is bound to the given
   * {@link AFSYSTEMSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFSYSTEMServerSocket bindOn(final AFSYSTEMSocketAddress addr) throws IOException {
    return (AFSYSTEMServerSocket) AFServerSocket.bindOn(AFSYSTEMServerSocket::new, addr);
  }

  /**
   * Returns a new AF_SYSTEM {@link ServerSocket} that is bound to the given
   * {@link AFSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @param deleteOnClose If {@code true}, the socket file (if the address points to a file) will be
   *          deleted upon {@link #close}.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFSYSTEMServerSocket bindOn(final AFSYSTEMSocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return (AFSYSTEMServerSocket) AFServerSocket.bindOn(AFSYSTEMServerSocket::new, addr,
        deleteOnClose);
  }

  /**
   * Returns a new, <em>unbound</em> AF_SYSTEM {@link ServerSocket} that will always bind to the
   * given address, regardless of any socket address used in a call to <code>bind</code>.
   *
   * @param forceAddr The address to use.
   * @return The new, yet unbound {@link AFServerSocket}.
   * @throws IOException if an exception occurs.
   */
  public static AFSYSTEMServerSocket forceBindOn(final AFSYSTEMSocketAddress forceAddr)
      throws IOException {
    return (AFSYSTEMServerSocket) AFServerSocket.forceBindOn(AFSYSTEMServerSocket::new, forceAddr);
  }

  @Override
  protected AFSocketImpl<AFSYSTEMSocketAddress> newImpl(FileDescriptor fdObj)
      throws SocketException {
    return new AFSYSTEMSocketImpl(fdObj);
  }

  @Override
  protected AFSocket<AFSYSTEMSocketAddress> newSocketInstance() throws IOException {
    return AFSYSTEMSocket.newInstance();
  }

  @Override
  public AFSYSTEMSocket accept() throws IOException {
    return (AFSYSTEMSocket) super.accept();
  }
}
