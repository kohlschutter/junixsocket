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
package org.newsclub.net.unix;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;

import org.eclipse.jdt.annotation.NonNull;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Service-provider class for junixsocket selectors and selectable channels.
 */
final class AFGenericSelectorProvider extends AFSelectorProvider<AFGenericSocketAddress> {
  private static final AFGenericSelectorProvider INSTANCE = new AFGenericSelectorProvider();

  @SuppressWarnings("null")
  static final AFAddressFamily<@NonNull AFGenericSocketAddress> AF_GENERIC = AFAddressFamily
      .registerAddressFamilyImpl("generic", AFGenericSocketAddress.addressFamily(),
          new AFAddressFamilyConfig<AFGenericSocketAddress>() {

            @Override
            protected Class<? extends AFSocket<AFGenericSocketAddress>> socketClass() {
              return AFGenericSocket.class;
            }

            @Override
            protected AFSocket.Constructor<AFGenericSocketAddress> socketConstructor() {
              return AFGenericSocket::new;
            }

            @Override
            protected Class<? extends AFServerSocket<AFGenericSocketAddress>> serverSocketClass() {
              return AFGenericServerSocket.class;
            }

            @Override
            protected AFServerSocket.Constructor<AFGenericSocketAddress> serverSocketConstructor() {
              return AFGenericServerSocket::new;
            }

            @Override
            protected Class<? extends AFSocketChannel<AFGenericSocketAddress>> socketChannelClass() {
              return AFGenericSocketChannel.class;
            }

            @Override
            protected Class<? extends AFServerSocketChannel<AFGenericSocketAddress>> serverSocketChannelClass() {
              return AFGenericServerSocketChannel.class;
            }

            @Override
            protected Class<? extends AFDatagramSocket<AFGenericSocketAddress>> datagramSocketClass() {
              return AFGenericDatagramSocket.class;
            }

            @Override
            protected AFDatagramSocket.Constructor<AFGenericSocketAddress> datagramSocketConstructor() {
              return AFGenericDatagramSocket::new;
            }

            @Override
            protected Class<? extends AFDatagramChannel<AFGenericSocketAddress>> datagramChannelClass() {
              return AFGenericDatagramChannel.class;
            }
          });

  private AFGenericSelectorProvider() {
    super();
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  public static AFGenericSelectorProvider getInstance() {
    return INSTANCE;
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  public static AFGenericSelectorProvider provider() {
    return getInstance();
  }

  /**
   * Constructs a new socket pair from two sockets.
   *
   * @param s1 Some socket, the first one.
   * @param s2 Some socket, the second one.
   * @return The pair.
   */
  @Override
  protected <P extends AFSomeSocket> AFSocketPair<P> newSocketPair(P s1, P s2) {
    return new AFGenericSocketPair<>(s1, s2);
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFGenericSocketPair<AFGenericSocketChannel> openSocketChannelPair() throws IOException {
    return (AFGenericSocketPair<AFGenericSocketChannel>) super.openSocketChannelPair();
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFGenericSocketPair<AFGenericDatagramChannel> openDatagramChannelPair()
      throws IOException {
    return (AFGenericSocketPair<AFGenericDatagramChannel>) super.openDatagramChannelPair();
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFGenericSocketPair<AFGenericDatagramChannel> openDatagramChannelPair(AFSocketType type)
      throws IOException {
    return (AFGenericSocketPair<AFGenericDatagramChannel>) super.openDatagramChannelPair(type);
  }

  @Override
  protected AFGenericSocket newSocket() throws IOException {
    return AFGenericSocket.newInstance();
  }

  @Override
  public AFGenericDatagramChannel openDatagramChannel() throws IOException {
    return AFGenericDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFGenericDatagramChannel openDatagramChannel(AFSocketType type) throws IOException {
    return AFGenericDatagramSocket.newInstance(type).getChannel();
  }

  @Override
  public AFGenericDatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
    return (AFGenericDatagramChannel) super.openDatagramChannel(family);
  }

  @Override
  public AFGenericServerSocketChannel openServerSocketChannel() throws IOException {
    return AFGenericServerSocket.newInstance().getChannel();
  }

  @Override
  public AFGenericServerSocketChannel openServerSocketChannel(SocketAddress sa) throws IOException {
    return AFGenericServerSocket.bindOn(AFGenericSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  public AFGenericSocketChannel openSocketChannel() throws IOException {
    return (AFGenericSocketChannel) super.openSocketChannel();
  }

  @Override
  public AFGenericSocketChannel openSocketChannel(SocketAddress sa) throws IOException {
    return AFGenericSocket.connectTo(AFGenericSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  protected ProtocolFamily protocolFamily() {
    return AFGenericProtocolFamily.GENERIC;
  }

  @Override
  protected AFAddressFamily<@NonNull AFGenericSocketAddress> addressFamily() {
    return AF_GENERIC;
  }
}
