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
package org.newsclub.net.unix.vsock;

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
import org.newsclub.net.unix.AFVSOCKSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Service-provider class for junixsocket selectors and selectable channels.
 */
public final class AFVSOCKSelectorProvider extends AFSelectorProvider<AFVSOCKSocketAddress> {
  private static final AFVSOCKSelectorProvider INSTANCE = new AFVSOCKSelectorProvider();
  @SuppressWarnings("null")
  static final AFAddressFamily<@NonNull AFVSOCKSocketAddress> AF_VSOCK = AFAddressFamily
      .registerAddressFamilyImpl("vsock", AFVSOCKSocketAddress.addressFamily(),
          new AFAddressFamilyConfig<AFVSOCKSocketAddress>() {

            @Override
            protected Class<? extends AFSocket<AFVSOCKSocketAddress>> socketClass() {
              return AFVSOCKSocket.class;
            }

            @Override
            protected AFSocket.Constructor<AFVSOCKSocketAddress> socketConstructor() {
              return AFVSOCKSocket::new;
            }

            @Override
            protected Class<? extends AFServerSocket<AFVSOCKSocketAddress>> serverSocketClass() {
              return AFVSOCKServerSocket.class;
            }

            @Override
            protected AFServerSocket.Constructor<AFVSOCKSocketAddress> serverSocketConstructor() {
              return AFVSOCKServerSocket::new;
            }

            @Override
            protected Class<? extends AFSocketChannel<AFVSOCKSocketAddress>> socketChannelClass() {
              return AFVSOCKSocketChannel.class;
            }

            @Override
            protected Class<? extends AFServerSocketChannel<AFVSOCKSocketAddress>> serverSocketChannelClass() {
              return AFVSOCKServerSocketChannel.class;
            }

            @Override
            protected Class<? extends AFDatagramSocket<AFVSOCKSocketAddress>> datagramSocketClass() {
              return AFVSOCKDatagramSocket.class;
            }

            @Override
            protected AFDatagramSocket.Constructor<AFVSOCKSocketAddress> datagramSocketConstructor() {
              return AFVSOCKDatagramSocket::new;
            }

            @Override
            protected Class<? extends AFDatagramChannel<AFVSOCKSocketAddress>> datagramChannelClass() {
              return AFVSOCKDatagramChannel.class;
            }
          });

  private AFVSOCKSelectorProvider() {
    super();
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static AFVSOCKSelectorProvider getInstance() {
    return INSTANCE;
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  public static AFVSOCKSelectorProvider provider() {
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
    return new AFVSOCKSocketPair<>(s1, s2);
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFVSOCKSocketPair<AFVSOCKSocketChannel> openSocketChannelPair() throws IOException {
    return (AFVSOCKSocketPair<AFVSOCKSocketChannel>) super.openSocketChannelPair();
  }

  @SuppressWarnings("unchecked")
  @Override
  public AFVSOCKSocketPair<AFVSOCKDatagramChannel> openDatagramChannelPair() throws IOException {
    return (AFVSOCKSocketPair<AFVSOCKDatagramChannel>) super.openDatagramChannelPair();
  }

  @Override
  protected AFVSOCKSocket newSocket() throws IOException {
    return AFVSOCKSocket.newInstance();
  }

  @Override
  public AFVSOCKDatagramChannel openDatagramChannel() throws IOException {
    return AFVSOCKDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFVSOCKDatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
    return (AFVSOCKDatagramChannel) super.openDatagramChannel(family);
  }

  @Override
  public AFVSOCKServerSocketChannel openServerSocketChannel() throws IOException {
    return AFVSOCKServerSocket.newInstance().getChannel();
  }

  @Override
  public AFVSOCKServerSocketChannel openServerSocketChannel(SocketAddress sa) throws IOException {
    return AFVSOCKServerSocket.bindOn(AFVSOCKSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  public AFVSOCKSocketChannel openSocketChannel() throws IOException {
    return (AFVSOCKSocketChannel) super.openSocketChannel();
  }

  @Override
  public AFVSOCKSocketChannel openSocketChannel(SocketAddress sa) throws IOException {
    return AFVSOCKSocket.connectTo(AFVSOCKSocketAddress.unwrap(sa)).getChannel();
  }

  @Override
  protected ProtocolFamily protocolFamily() {
    return AFVSOCKProtocolFamily.VSOCK;
  }

  @Override
  protected AFAddressFamily<@NonNull AFVSOCKSocketAddress> addressFamily() {
    return AF_VSOCK;
  }
}
