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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

public interface AddressSpecifics<A extends SocketAddress> {
  SocketAddress initServerSocketBindAddress() throws IOException;

  Socket newStrictSocket() throws IOException;

  Socket newSocket() throws IOException;

  DatagramSocket newDatagramSocket() throws IOException;

  ServerSocket newServerSocket() throws IOException;

  SocketAddress newTempAddress() throws IOException;

  SocketAddress unwrap(InetAddress addr, int port) throws SocketException;

  SelectorProvider selectorProvider();

  CloseablePair<? extends SocketChannel> newSocketPair() throws IOException;

  CloseablePair<? extends DatagramChannel> newDatagramSocketPair() throws IOException;

  ServerSocket newServerSocketBindOn(SocketAddress addr) throws IOException;

  Socket connectTo(SocketAddress socket) throws IOException;

  ServerSocket newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose) throws IOException;

  default CloseablePair<? extends Socket> newInterconnectedSockets() throws IOException {
    final SocketAddress address = newTempAddress();
    ServerSocket server = newServerSocketBindOn(address);
    Socket client = connectTo(address);
    final Socket socket = server.accept();
    return new CloseablePair<AFSocket<?>>((AFSocket<?>) client, (AFSocket<?>) socket, server);
  }

  DatagramChannel newDatagramChannel() throws IOException;
}