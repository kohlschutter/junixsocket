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
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Service-provider class for junixsocket selectors and selectable channels.
 *
 * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
 */
public abstract class AFSelectorProvider<A extends AFSocketAddress> extends SelectorProvider {

  /**
   * Constructs a new {@link AFSelectorProvider}.
   */
  protected AFSelectorProvider() {
    super();
  }

  /**
   * Constructs a new pipe.
   *
   * @param selectable {@code true} if the pipe should be selectable.
   * @return The pipe.
   * @throws IOException on error.
   */
  private AFPipe newPipe(boolean selectable) throws IOException {
    return new AFPipe(this, selectable);
  }

  /**
   * Constructs a new socket pair from two sockets.
   *
   * @param <Y> The type of the pair.
   * @param s1 Some socket, the first one.
   * @param s2 Some socket, the second one.
   * @return The pair.
   */
  protected abstract <Y extends AFSomeSocket> AFSocketPair<Y> newSocketPair(Y s1, Y s2);

  /**
   * Constructs a new socket.
   *
   * @return The socket instance.
   * @throws IOException on error.
   */
  protected abstract AFSocket<A> newSocket() throws IOException;

  /**
   * Returns the protocol family supported by this implementation.
   *
   * @return The protocol family.
   */
  protected abstract ProtocolFamily protocolFamily();

  /**
   * Returns the address family supported by this implementation.
   *
   * @return The address family.
   */
  protected abstract AFAddressFamily<@NonNull A> addressFamily();

  /**
   * Returns the domain ID for the supported protocol, as specified by {@link NativeUnixSocket}.
   *
   * @return The domain ID.
   */
  protected final int domainId() {
    return addressFamily().getDomain();
  }

  /**
   * Opens a socket pair of interconnected channels.
   *
   * @return The new channel pair.
   * @throws IOException on error.
   */
  @SuppressWarnings("resource")
  public AFSocketPair<? extends AFSocketChannel<A>> openSocketChannelPair() throws IOException {
    AFSocketChannel<A> s1 = openSocketChannel();
    AFSocketChannel<A> s2 = openSocketChannel();

    NativeUnixSocket.socketPair(domainId(), NativeUnixSocket.SOCK_STREAM, s1.getAFCore().fd, s2
        .getAFCore().fd);

    s1.socket().internalDummyConnect();
    s2.socket().internalDummyConnect();

    return newSocketPair(s1, s2);
  }

  /**
   * Opens a socket pair of interconnected datagram channels.
   *
   * @return The new channel pair.
   * @throws IOException on error.
   */
  @SuppressWarnings("resource")
  public AFSocketPair<? extends AFDatagramChannel<A>> openDatagramChannelPair() throws IOException {
    ProtocolFamily pf = protocolFamily();
    AFDatagramChannel<A> s1 = openDatagramChannel(pf);
    AFDatagramChannel<A> s2 = openDatagramChannel(pf);

    NativeUnixSocket.socketPair(domainId(), NativeUnixSocket.SOCK_STREAM, s1.getAFCore().fd, s2
        .getAFCore().fd);

    s1.socket().internalDummyBind();
    s2.socket().internalDummyBind();
    s1.socket().internalDummyConnect();
    s2.socket().internalDummyConnect();

    return newSocketPair(s1, s2);
  }

  @Override
  public abstract AFDatagramChannel<A> openDatagramChannel() throws IOException;

  @Override
  public AFDatagramChannel<A> openDatagramChannel(ProtocolFamily family) throws IOException {
    if (!protocolFamily().name().equals(family.name())) {
      throw new UnsupportedOperationException("Unsupported protocol family");
    }
    return openDatagramChannel();
  }

  @Override
  public final AFPipe openPipe() throws IOException {
    return newPipe(false);
  }

  /**
   * Opens a pipe with support for selectors.
   *
   * @return The new pipe
   * @throws IOException on error.
   */
  final AFPipe openSelectablePipe() throws IOException {
    return newPipe(true);
  }

  @Override
  public final AbstractSelector openSelector() throws IOException {
    return new AFSelector(this);
  }

  @Override
  public abstract AFServerSocketChannel<A> openServerSocketChannel() throws IOException;

  /**
   * Opens a server-socket channel bound on the given {@link SocketAddress}.
   *
   * @param sa The socket address to bind on.
   * @return The new channel
   * @throws IOException on error.
   */
  public abstract AFServerSocketChannel<A> openServerSocketChannel(SocketAddress sa)
      throws IOException;

  @Override
  public AFSocketChannel<A> openSocketChannel() throws IOException {
    return newSocket().getChannel();
  }

  /**
   * Opens a socket channel connected to the given {@link SocketAddress}.
   *
   * @param sa The socket address to connect to.
   * @return The new channel
   * @throws IOException on error.
   */
  public abstract AFSocketChannel<A> openSocketChannel(SocketAddress sa) throws IOException;
}
