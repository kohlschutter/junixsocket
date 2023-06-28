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
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFSocketAddress;

/**
 * A base implementation for a simple, multi-threaded socket server.
 *
 * This class supports both AF_UNIX and "regular" sockets.
 *
 * @author Christian Kohlschütter
 * @deprecated Use {@link SocketServer} or {@link AFSocketServer}
 */
@Deprecated
public abstract class AFUNIXSocketServer extends SocketServer<SocketAddress, Socket, ServerSocket> {
  /**
   * Creates a server using the given, bound {@link ServerSocket}.
   *
   * @param serverSocket The server socket to use (must be bound).
   */
  public AFUNIXSocketServer(ServerSocket serverSocket) {
    super(serverSocket);
  }

  /**
   * Creates a server using the given {@link SocketAddress}.
   *
   * @param listenAddress The address to bind the socket on.
   */
  public AFUNIXSocketServer(SocketAddress listenAddress) {
    super(listenAddress);
  }

  /**
   * Starts the server and waits until it is ready or had to shop due to an error.
   *
   * @param duration The duration wait.
   * @param unit The duration's time unit.
   * @return {@code true} if the server is ready to serve requests.
   * @throws InterruptedException If the wait was interrupted.
   * @deprecated @see #startAndWaitToBecomeReady(long, TimeUnit)
   */
  @Deprecated
  public boolean startAndWait(long duration, TimeUnit unit) throws InterruptedException {
    return startAndWaitToBecomeReady(duration, unit);
  }

  @Override
  protected ServerSocket newServerSocket() throws IOException {
    SocketAddress listenAddress = getListenAddress();
    if (listenAddress instanceof AFSocketAddress) {
      return ((AFSocketAddress) listenAddress).getAddressFamily().newServerSocket();
    } else {
      return new ServerSocket();
    }
  }
}
