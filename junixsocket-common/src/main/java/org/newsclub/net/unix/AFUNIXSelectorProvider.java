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
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.AbstractMap;
import java.util.Map;

public final class AFUNIXSelectorProvider extends SelectorProvider {
  private static final AFUNIXSelectorProvider INSTANCE = new AFUNIXSelectorProvider();

  private AFUNIXSelectorProvider() {
    super();
  }

  public static AFUNIXSelectorProvider getInstance() {
    return INSTANCE;
  }

  public static AFUNIXSelectorProvider provider() {
    return getInstance();
  }

  @SuppressWarnings("resource")
  public Map.Entry<AFUNIXSocketChannel, AFUNIXSocketChannel> openSocketChannelPair()
      throws IOException {
    AFUNIXSocket s1 = AFUNIXSocket.newInstance();
    AFUNIXSocket s2 = AFUNIXSocket.newInstance();

    NativeUnixSocket.socketPair(NativeUnixSocket.SOCK_STREAM, s1.getAFImpl().getCore().fd, s2
        .getAFImpl().getCore().fd);

    s1.internalDummyConnect();
    s2.internalDummyConnect();

    return new AbstractMap.SimpleImmutableEntry<AFUNIXSocketChannel, AFUNIXSocketChannel>(s1
        .getChannel(), s2.getChannel());
  }

  @Override
  public AFUNIXDatagramChannel openDatagramChannel() throws IOException {
    return AFUNIXDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFUNIXDatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
    if (!AFUNIXProtocolFamily.UNIX.name().equals(family.name())) {
      throw new UnsupportedOperationException("Unsupported protocol family");
    }
    return openDatagramChannel();
  }

  @Override
  public AFUNIXPipe openPipe() throws IOException {
    return new AFUNIXPipe(this);
  }

  @Override
  public AbstractSelector openSelector() throws IOException {
    return new AFUNIXSelector(this);
  }

  @Override
  public AFUNIXServerSocketChannel openServerSocketChannel() throws IOException {
    return AFUNIXServerSocket.newInstance().getChannel();
  }

  public AFUNIXServerSocketChannel openServerSocketChannel(SocketAddress sa) throws IOException {
    return AFUNIXServerSocket.bindOn(AFUNIXSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  public AFUNIXSocketChannel openSocketChannel() throws IOException {
    return AFUNIXSocket.newInstance().getChannel();
  }

  public AFUNIXSocketChannel openSocketChannel(SocketAddress sa) throws IOException {
    return AFUNIXSocket.connectTo(AFUNIXSocketAddress.unwrap(sa)).getChannel();
  }
}
