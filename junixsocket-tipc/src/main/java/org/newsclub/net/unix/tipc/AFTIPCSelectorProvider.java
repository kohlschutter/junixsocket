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
package org.newsclub.net.unix.tipc;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;

import org.eclipse.jdt.annotation.NonNull;
import org.newsclub.net.unix.AFAddressFamily;
import org.newsclub.net.unix.AFAddressFamilyConfig;
import org.newsclub.net.unix.AFDatagramChannel;
import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSelectorProvider;
import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketChannel;
import org.newsclub.net.unix.AFSocketPair;
import org.newsclub.net.unix.AFSomeSocket;
import org.newsclub.net.unix.AFTIPCSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Service-provider class for junixsocket selectors and selectable channels.
 */
public final class AFTIPCSelectorProvider extends AFSelectorProvider<AFTIPCSocketAddress> {
  private static final AFTIPCSelectorProvider INSTANCE = new AFTIPCSelectorProvider();
  @SuppressWarnings("null")
  static final AFAddressFamily<@NonNull AFTIPCSocketAddress> AF_TIPC = AFAddressFamily
      .registerAddressFamilyImpl("tipc", AFTIPCSocketAddress.addressFamily(),
          new AFAddressFamilyConfig<AFTIPCSocketAddress>() {

            @Override
            protected Class<? extends AFSocket<AFTIPCSocketAddress>> socketClass() {
              return AFTIPCSocket.class;
            }

            @Override
            protected AFSocket.Constructor<AFTIPCSocketAddress> socketConstructor() {
              return AFTIPCSocket::new;
            }

            @Override
            protected Class<? extends AFServerSocket<AFTIPCSocketAddress>> serverSocketClass() {
              return AFTIPCServerSocket.class;
            }

            @Override
            protected AFServerSocket.Constructor<AFTIPCSocketAddress> serverSocketConstructor() {
              return AFTIPCServerSocket::new;
            }

            @Override
            protected Class<? extends AFSocketChannel<AFTIPCSocketAddress>> socketChannelClass() {
              return AFTIPCSocketChannel.class;
            }

            @Override
            protected Class<? extends AFServerSocketChannel<AFTIPCSocketAddress>> serverSocketChannelClass() {
              return AFTIPCServerSocketChannel.class;
            }

            @Override
            protected Class<? extends AFDatagramSocket<AFTIPCSocketAddress>> datagramSocketClass() {
              return AFTIPCDatagramSocket.class;
            }

            @Override
            protected AFDatagramSocket.Constructor<AFTIPCSocketAddress> datagramSocketConstructor() {
              return AFTIPCDatagramSocket::new;
            }

            @Override
            protected Class<? extends AFDatagramChannel<AFTIPCSocketAddress>> datagramChannelClass() {
              return AFTIPCDatagramChannel.class;
            }
          });

  private AFTIPCSelectorProvider() {
    super();
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static AFTIPCSelectorProvider getInstance() {
    return INSTANCE;
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  public static AFTIPCSelectorProvider provider() {
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
    return new AFTIPCSocketPair<>(s1, s2);
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFTIPCSocketPair<AFTIPCSocketChannel> openSocketChannelPair() throws IOException {
    return (AFTIPCSocketPair<AFTIPCSocketChannel>) super.openSocketChannelPair();
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFTIPCSocketPair<AFTIPCDatagramChannel> openDatagramChannelPair() throws IOException {
    return (AFTIPCSocketPair<AFTIPCDatagramChannel>) super.openDatagramChannelPair();
  }

  @Override
  protected AFTIPCSocket newSocket() throws IOException {
    return AFTIPCSocket.newInstance();
  }

  @Override
  public AFTIPCDatagramChannel openDatagramChannel() throws IOException {
    return AFTIPCDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFTIPCDatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
    return (AFTIPCDatagramChannel) super.openDatagramChannel(family);
  }

  @Override
  public AFTIPCServerSocketChannel openServerSocketChannel() throws IOException {
    return AFTIPCServerSocket.newInstance().getChannel();
  }

  @Override
  public AFTIPCServerSocketChannel openServerSocketChannel(SocketAddress sa) throws IOException {
    return AFTIPCServerSocket.bindOn(AFTIPCSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  public AFTIPCSocketChannel openSocketChannel() throws IOException {
    return (AFTIPCSocketChannel) super.openSocketChannel();
  }

  @Override
  public AFTIPCSocketChannel openSocketChannel(SocketAddress sa) throws IOException {
    return AFTIPCSocket.connectTo(AFTIPCSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  protected ProtocolFamily protocolFamily() {
    return AFTIPCProtocolFamily.TIPC;
  }

  @Override
  protected AFAddressFamily<@NonNull AFTIPCSocketAddress> addressFamily() {
    return AF_TIPC;
  }
}
