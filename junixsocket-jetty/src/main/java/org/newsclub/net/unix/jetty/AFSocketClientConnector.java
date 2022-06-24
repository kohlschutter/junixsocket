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
package org.newsclub.net.unix.jetty;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;

import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.ClientConnector.Configurator;
import org.eclipse.jetty.server.Connector;
import org.newsclub.net.unix.AFSocketAddress;

/**
 * A {@link Connector} implementation for junixsocket server socket channels (Unix domains etc.)
 * 
 * Based upon jetty's ClientConnector.
 * 
 * This implementation should work with jetty version 10.0.8 or newer.
 * 
 * @author Christian Kohlschütter
 */
public final class AFSocketClientConnector {
  private AFSocketClientConnector() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Returns a new {@link ClientConnector} configured to use given {@link AFSocketAddress} for
   * communication with junixsocket sockets.
   * 
   * @param addr The socket address.
   * @return The client connector.
   */
  public static ClientConnector withSocketAddress(AFSocketAddress addr) {
    return new ClientConnector(configuratorFor(addr));
  }

  private static Configurator configuratorFor(AFSocketAddress addr) {
    return new Configurator() {
      @Override
      public ChannelWithAddress newChannelWithAddress(ClientConnector clientConnector,
          SocketAddress address, Map<String, Object> context) throws IOException {
        SocketChannel socketChannel = addr.getAddressFamily().newSocketChannel();
        return new ChannelWithAddress(socketChannel, addr);
      }
    };
  }
}
