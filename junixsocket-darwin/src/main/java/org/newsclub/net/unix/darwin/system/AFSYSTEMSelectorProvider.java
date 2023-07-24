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
package org.newsclub.net.unix.darwin.system;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;

import org.eclipse.jdt.annotation.NonNull;
import org.newsclub.net.unix.AFAddressFamily;
import org.newsclub.net.unix.AFAddressFamilyConfig;
import org.newsclub.net.unix.AFDatagramChannel;
import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSYSTEMSocketAddress;
import org.newsclub.net.unix.AFSelectorProvider;
import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketChannel;
import org.newsclub.net.unix.AFSocketPair;
import org.newsclub.net.unix.AFSomeSocket;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Service-provider class for junixsocket selectors and selectable channels.
 */
public final class AFSYSTEMSelectorProvider extends AFSelectorProvider<AFSYSTEMSocketAddress> {
  private static final AFSYSTEMSelectorProvider INSTANCE = new AFSYSTEMSelectorProvider();

  @SuppressWarnings("null")
  static final AFAddressFamily<@NonNull AFSYSTEMSocketAddress> AF_SYSTEM = AFAddressFamily
      .registerAddressFamilyImpl("system", AFSYSTEMSocketAddress.addressFamily(),
          new AFAddressFamilyConfig<AFSYSTEMSocketAddress>() {

            @Override
            protected Class<? extends AFSocket<AFSYSTEMSocketAddress>> socketClass() {
              return AFSYSTEMSocket.class;
            }

            @Override
            protected AFSocket.Constructor<AFSYSTEMSocketAddress> socketConstructor() {
              return AFSYSTEMSocket::new;
            }

            @Override
            protected Class<? extends AFServerSocket<AFSYSTEMSocketAddress>> serverSocketClass() {
              return AFSYSTEMServerSocket.class;
            }

            @Override
            protected AFServerSocket.Constructor<AFSYSTEMSocketAddress> serverSocketConstructor() {
              return AFSYSTEMServerSocket::new;
            }

            @Override
            protected Class<? extends AFSocketChannel<AFSYSTEMSocketAddress>> socketChannelClass() {
              return AFSYSTEMSocketChannel.class;
            }

            @Override
            protected Class<? extends AFServerSocketChannel<AFSYSTEMSocketAddress>> serverSocketChannelClass() {
              return AFSYSTEMServerSocketChannel.class;
            }

            @Override
            protected Class<? extends AFDatagramSocket<AFSYSTEMSocketAddress>> datagramSocketClass() {
              return AFSYSTEMDatagramSocket.class;
            }

            @Override
            protected AFDatagramSocket.Constructor<AFSYSTEMSocketAddress> datagramSocketConstructor() {
              return AFSYSTEMDatagramSocket::new;
            }

            @Override
            protected Class<? extends AFDatagramChannel<AFSYSTEMSocketAddress>> datagramChannelClass() {
              return AFSYSTEMDatagramChannel.class;
            }
          });

  private AFSYSTEMSelectorProvider() {
    super();
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static AFSYSTEMSelectorProvider getInstance() {
    return INSTANCE;
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  public static AFSYSTEMSelectorProvider provider() {
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
    return new AFSYSTEMSocketPair<>(s1, s2);
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFSYSTEMSocketPair<AFSYSTEMSocketChannel> openSocketChannelPair() throws IOException {
    return (AFSYSTEMSocketPair<AFSYSTEMSocketChannel>) super.openSocketChannelPair();
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFSYSTEMSocketPair<AFSYSTEMDatagramChannel> openDatagramChannelPair() throws IOException {
    return (AFSYSTEMSocketPair<AFSYSTEMDatagramChannel>) super.openDatagramChannelPair();
  }

  @Override
  protected AFSYSTEMSocket newSocket() throws IOException {
    return AFSYSTEMSocket.newInstance();
  }

  @Override
  public AFSYSTEMDatagramChannel openDatagramChannel() throws IOException {
    return AFSYSTEMDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFSYSTEMDatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
    return (AFSYSTEMDatagramChannel) super.openDatagramChannel(family);
  }

  @Override
  public AFSYSTEMServerSocketChannel openServerSocketChannel() throws IOException {
    return AFSYSTEMServerSocket.newInstance().getChannel();
  }

  @Override
  public AFSYSTEMServerSocketChannel openServerSocketChannel(SocketAddress sa) throws IOException {
    return AFSYSTEMServerSocket.bindOn(AFSYSTEMSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  public AFSYSTEMSocketChannel openSocketChannel() throws IOException {
    return (AFSYSTEMSocketChannel) super.openSocketChannel();
  }

  @Override
  public AFSYSTEMSocketChannel openSocketChannel(SocketAddress sa) throws IOException {
    return AFSYSTEMSocket.connectTo(AFSYSTEMSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  protected ProtocolFamily protocolFamily() {
    return AFSYSTEMProtocolFamily.SYSTEM;
  }

  @Override
  protected AFAddressFamily<@NonNull AFSYSTEMSocketAddress> addressFamily() {
    return AF_SYSTEM;
  }
}
