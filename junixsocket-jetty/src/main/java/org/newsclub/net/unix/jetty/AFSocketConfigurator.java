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
package org.newsclub.net.unix.jetty;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;

import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.ClientConnector.Configurator;
import org.newsclub.net.unix.AFSocketAddress;

/**
 * A {@link ClientConnector.Configurator} for junixsocket {@link SocketChannel}s.
 *
 * @author Christian Kohlschütter
 */
@Deprecated
class AFSocketConfigurator extends Configurator {
  protected final AFSocketAddress addr;

  AFSocketConfigurator(AFSocketAddress addr) {
    super();
    this.addr = addr;
  }

  @Override
  public ChannelWithAddress newChannelWithAddress(ClientConnector clientConnector,
      SocketAddress address, Map<String, Object> context) throws IOException {
    SocketChannel socketChannel = addr.getAddressFamily().newSocketChannel();
    return new ChannelWithAddress(socketChannel, addr);
  }
}
