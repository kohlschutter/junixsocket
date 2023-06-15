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

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.file.Path;

/**
 * The server part of an AF_UNIX domain socket.
 *
 * @author Christian Kohlschütter
 */
public class AFUNIXServerSocket extends AFServerSocket<AFUNIXSocketAddress> {
  /**
   * Constructs a new, unconnected instance.
   *
   * @throws IOException if the operation fails.
   */
  protected AFUNIXServerSocket() throws IOException {
    super();
  }

  /**
   * Constructs a new instance, optionally associated with the given file descriptor.
   *
   * @param fdObj The file descriptor, or {@code null}.
   * @throws IOException if the operation fails.
   */
  AFUNIXServerSocket(FileDescriptor fdObj) throws IOException {
    super(fdObj);
  }

  @Override
  protected AFUNIXServerSocketChannel newChannel() {
    return new AFUNIXServerSocketChannel(this);
  }

  @Override
  public AFUNIXServerSocketChannel getChannel() {
    return (AFUNIXServerSocketChannel) super.getChannel();
  }

  /**
   * Returns a new, unbound AF_UNIX {@link ServerSocket}.
   *
   * @return The new, unbound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket newInstance() throws IOException {
    return (AFUNIXServerSocket) AFServerSocket.newInstance(AFUNIXServerSocket::new);
  }

  static AFUNIXServerSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    return (AFUNIXServerSocket) AFServerSocket.newInstance(AFUNIXServerSocket::new, fdObj,
        localPort, remotePort);
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given
   * {@link AFUNIXSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final AFUNIXSocketAddress addr) throws IOException {
    return (AFUNIXServerSocket) AFServerSocket.bindOn(AFUNIXServerSocket::new, addr);
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given {@link AFSocketAddress}.
   *
   * @param addr The socket file to bind to.
   * @param deleteOnClose If {@code true}, the socket file (if the address points to a file) will be
   *          deleted upon {@link #close}.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final AFUNIXSocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return (AFUNIXServerSocket) AFServerSocket.bindOn(AFUNIXServerSocket::new, addr, deleteOnClose);
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given path.
   *
   * @param path The path to bind to.
   * @param deleteOnClose If {@code true}, the socket file will be deleted upon {@link #close}.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final File path, boolean deleteOnClose)
      throws IOException {
    return bindOn(path.toPath(), deleteOnClose);
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given path.
   *
   * @param path The path to bind to.
   * @param deleteOnClose If {@code true}, the socket file will be deleted upon {@link #close}.
   * @return The new, bound {@link AFServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final Path path, boolean deleteOnClose)
      throws IOException {
    return (AFUNIXServerSocket) AFServerSocket.bindOn(AFUNIXServerSocket::new, AFUNIXSocketAddress
        .of(path), deleteOnClose);
  }

  /**
   * Returns a new, <em>unbound</em> AF_UNIX {@link ServerSocket} that will always bind to the given
   * address, regardless of any socket address used in a call to <code>bind</code>.
   *
   * @param forceAddr The address to use.
   * @return The new, yet unbound {@link AFServerSocket}.
   * @throws IOException if an exception occurs.
   */
  public static AFUNIXServerSocket forceBindOn(final AFUNIXSocketAddress forceAddr)
      throws IOException {
    return (AFUNIXServerSocket) AFServerSocket.forceBindOn(AFUNIXServerSocket::new, forceAddr);
  }

  @Override
  protected AFSocketImpl<AFUNIXSocketAddress> newImpl(FileDescriptor fdObj) throws SocketException {
    return new AFUNIXSocketImpl(fdObj);
  }

  /**
   * Returns a new {@link AFSocket} instance.
   *
   * @return The new instance.
   * @throws IOException on error.
   */
  @Override
  protected AFUNIXSocket newSocketInstance() throws IOException {
    return AFUNIXSocket.newInstance();
  }

  @Override
  public AFUNIXSocket accept() throws IOException {
    return (AFUNIXSocket) super.accept();
  }
}
