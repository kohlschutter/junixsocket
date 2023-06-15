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
package org.newsclub.net.unix.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;

import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;

/**
 * A base implementation for a simple, multi-threaded socket server using {@link AFSocket}s.
 *
 * @author Christian Kohlschütter
 * @param <A> The supported address type.
 */
public abstract class AFSocketServer<A extends AFSocketAddress> extends
    SocketServer<A, AFSocket<? extends A>, AFServerSocket<? extends A>> {
  /**
   * Creates a server using the given, bound {@link ServerSocket}.
   *
   * @param serverSocket The server socket to use (must be bound).
   */
  public AFSocketServer(AFServerSocket<? extends A> serverSocket) {
    super(serverSocket);
  }

  /**
   * Creates a server using the given {@link SocketAddress}.
   *
   * @param listenAddress The address to bind the socket on.
   */
  public AFSocketServer(A listenAddress) {
    super(listenAddress);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected AFServerSocket<A> newServerSocket() throws IOException {
    return (AFServerSocket<A>) getListenAddress().getAddressFamily().newServerSocket();
  }
}
