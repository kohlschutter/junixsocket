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

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;

import org.eclipse.jdt.annotation.NonNull;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Service-provider class for junixsocket selectors and selectable channels.
 */
public final class AFUNIXSelectorProvider extends AFSelectorProvider<AFUNIXSocketAddress> {
  private static final AFUNIXSelectorProvider INSTANCE = new AFUNIXSelectorProvider();

  @SuppressWarnings("null")
  static final AFAddressFamily<@NonNull AFUNIXSocketAddress> AF_UNIX = //
      AFAddressFamily.registerAddressFamilyImpl("un", AFUNIXSocketAddress.AF_UNIX, //

          new AFAddressFamilyConfig<AFUNIXSocketAddress>() {
            @Override
            public Class<? extends AFSocket<AFUNIXSocketAddress>> socketClass() {
              return AFUNIXSocket.class;
            }

            @Override
            public AFSocket.Constructor<AFUNIXSocketAddress> socketConstructor() {
              return AFUNIXSocket::new;
            }

            @Override
            public Class<? extends AFServerSocket<AFUNIXSocketAddress>> serverSocketClass() {
              return AFUNIXServerSocket.class;
            }

            @Override
            public AFServerSocket.Constructor<AFUNIXSocketAddress> serverSocketConstructor() {
              return AFUNIXServerSocket::new;
            }

            @Override
            public Class<? extends AFSocketChannel<AFUNIXSocketAddress>> socketChannelClass() {
              return AFUNIXSocketChannel.class;
            }

            @Override
            public Class<? extends AFServerSocketChannel<AFUNIXSocketAddress>> serverSocketChannelClass() {
              return AFUNIXServerSocketChannel.class;
            }

            @Override
            public Class<? extends AFDatagramSocket<AFUNIXSocketAddress>> datagramSocketClass() {
              return AFUNIXDatagramSocket.class;
            }

            @Override
            public AFDatagramSocket.Constructor<AFUNIXSocketAddress> datagramSocketConstructor() {
              return AFUNIXDatagramSocket::new;
            }

            @Override
            public Class<? extends AFDatagramChannel<AFUNIXSocketAddress>> datagramChannelClass() {
              return AFUNIXDatagramChannel.class;
            }
          });

  private AFUNIXSelectorProvider() {
    super();
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  @SuppressFBWarnings("MS_EXPOSE_REP")
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

  @Override
  protected <P extends AFSomeSocket> AFSocketPair<P> newSocketPair(P s1, P s2) {
    return new AFUNIXSocketPair<>(s1, s2);
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFUNIXSocketPair<AFUNIXSocketChannel> openSocketChannelPair() throws IOException {
    return (AFUNIXSocketPair<AFUNIXSocketChannel>) super.openSocketChannelPair();
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFUNIXSocketPair<AFUNIXDatagramChannel> openDatagramChannelPair() throws IOException {
    return (AFUNIXSocketPair<AFUNIXDatagramChannel>) super.openDatagramChannelPair();
  }

  @Override
  protected AFUNIXSocket newSocket() throws IOException {
    return AFUNIXSocket.newInstance();
  }

  @Override
  public AFUNIXDatagramChannel openDatagramChannel() throws IOException {
    return AFUNIXDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFUNIXDatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
    return (AFUNIXDatagramChannel) super.openDatagramChannel(family);
  }

  @Override
  public AFUNIXServerSocketChannel openServerSocketChannel() throws IOException {
    return AFUNIXServerSocket.newInstance().getChannel();
  }

  @Override
  public AFUNIXServerSocketChannel openServerSocketChannel(SocketAddress sa) throws IOException {
    return AFUNIXServerSocket.bindOn(AFUNIXSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  public AFUNIXSocketChannel openSocketChannel() throws IOException {
    return (AFUNIXSocketChannel) super.openSocketChannel();
  }

  @Override
  public AFUNIXSocketChannel openSocketChannel(SocketAddress sa) throws IOException {
    return AFUNIXSocket.connectTo(AFUNIXSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  protected ProtocolFamily protocolFamily() {
    return AFUNIXProtocolFamily.UNIX;
  }

  @Override
  protected AFAddressFamily<@NonNull AFUNIXSocketAddress> addressFamily() {
    return AFUNIXSocketAddress.AF_UNIX;
  }
}
