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

/**
 * Service-provider class for junixsocket selectors and selectable channels.
 */
public final class AFUNIXSelectorProvider extends SelectorProvider {
  private static final AFUNIXSelectorProvider INSTANCE = new AFUNIXSelectorProvider();

  private AFUNIXSelectorProvider() {
    super();
  }

  /**
   * Returns the singleton instance.
   * 
   * @return The instance.
   */
  public static AFUNIXSelectorProvider getInstance() {
    return INSTANCE;
  }

  /**
   * Returns the singleton instance.
   * 
   * @return The instance.
   */
  public static AFUNIXSelectorProvider provider() {
    return getInstance();
  }

  /**
   * Opens a socket pair of interconnected channels.
   * 
   * @return The new channel pair.
   * @throws IOException on error.
   */
  @SuppressWarnings("resource")
  public AFUNIXSocketPair<AFUNIXSocketChannel> openSocketChannelPair() throws IOException {
    AFUNIXSocketChannel s1 = openSocketChannel();
    AFUNIXSocketChannel s2 = openSocketChannel();

    NativeUnixSocket.socketPair(NativeUnixSocket.SOCK_STREAM, s1.getAFCore().fd, s2.getAFCore().fd);

    s1.socket().internalDummyConnect();
    s2.socket().internalDummyConnect();

    return new AFUNIXSocketPair<AFUNIXSocketChannel>(s1, s2);
  }

  /**
   * Opens a socket pair of interconnected datagram channels.
   * 
   * @return The new channel pair.
   * @throws IOException on error.
   */
  @SuppressWarnings("resource")
  public AFUNIXSocketPair<AFUNIXDatagramChannel> openDatagramChannelPair() throws IOException {
    AFUNIXDatagramChannel s1 = openDatagramChannel(AFUNIXProtocolFamily.UNIX);
    AFUNIXDatagramChannel s2 = openDatagramChannel(AFUNIXProtocolFamily.UNIX);

    NativeUnixSocket.socketPair(NativeUnixSocket.SOCK_STREAM, s1.getAFCore().fd, s2.getAFCore().fd);

    s1.socket().internalDummyBind();
    s2.socket().internalDummyBind();
    s1.socket().internalDummyConnect();
    s2.socket().internalDummyConnect();

    return new AFUNIXSocketPair<AFUNIXDatagramChannel>(s1, s2);
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
    return new AFUNIXPipe(this, false);
  }

  /**
   * Opens a pipe with support for selectors.
   * 
   * @return The new pipe
   * @throws IOException on error.
   */
  public AFUNIXPipe openSelectablePipe() throws IOException {
    return new AFUNIXPipe(this, true);
  }

  @Override
  public AbstractSelector openSelector() throws IOException {
    return new AFUNIXSelector(this);
  }

  @Override
  public AFUNIXServerSocketChannel openServerSocketChannel() throws IOException {
    return AFUNIXServerSocket.newInstance().getChannel();
  }

  /**
   * Opens a server-socket channel bound on the given {@link SocketAddress}.
   *
   * @param sa The socket address to bind on.
   * @return The new channel
   * @throws IOException on error.
   */
  public AFUNIXServerSocketChannel openServerSocketChannel(SocketAddress sa) throws IOException {
    return AFUNIXServerSocket.bindOn(AFUNIXSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  public AFUNIXSocketChannel openSocketChannel() throws IOException {
    return AFUNIXSocket.newInstance().getChannel();
  }

  /**
   * Opens a socket channel connected to the given {@link SocketAddress}.
   * 
   * @param sa The socket address to connect to.
   * @return The new channel
   * @throws IOException on error.
   */
  public AFUNIXSocketChannel openSocketChannel(SocketAddress sa) throws IOException {
    return AFUNIXSocket.connectTo(AFUNIXSocketAddress.unwrap(sa)).getChannel();
  }
}
