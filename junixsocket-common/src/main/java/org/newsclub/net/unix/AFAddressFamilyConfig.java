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

import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * The implementation-specifics for a given address family implementation.
 *
 * @param <A> The supported address type.
 * @author Christian Kohlschütter
 * @see AFSocketAddressConfig
 */
public abstract class AFAddressFamilyConfig<A extends AFSocketAddress> {

  /**
   * Constructor.
   */
  protected AFAddressFamilyConfig() {
  }

  /**
   * Returns the implementation's {@link Socket} class.
   *
   * @return The implementation's {@link Socket} class.
   */
  protected abstract Class<? extends AFSocket<A>> socketClass();

  /**
   * Returns the implementation's {@link Socket} constructor.
   *
   * @return The implementation's {@link Socket} constructor.
   */
  protected abstract AFSocket.Constructor<A> socketConstructor();

  /**
   * Returns the implementation's {@link ServerSocket} class.
   *
   * @return The implementation's {@link ServerSocket} class.
   */
  protected abstract Class<? extends AFServerSocket<A>> serverSocketClass();

  /**
   * Returns the implementation's {@link ServerSocket} constructor.
   *
   * @return The implementation's {@link ServerSocket} constructor.
   */
  protected abstract AFServerSocket.Constructor<A> serverSocketConstructor();

  /**
   * Returns the implementation's {@link SocketChannel} class.
   *
   * @return The implementation's {@link SocketChannel} class..
   */
  protected abstract Class<? extends AFSocketChannel<A>> socketChannelClass();

  /**
   * Returns the implementation's {@link ServerSocketChannel} class.
   *
   * @return The implementation's {@link ServerSocketChannel} class.
   */
  protected abstract Class<? extends AFServerSocketChannel<A>> serverSocketChannelClass();

  /**
   * Returns the implementation's {@link DatagramSocket} class.
   *
   * @return The implementation's {@link DatagramSocket} class.
   */
  protected abstract Class<? extends AFDatagramSocket<A>> datagramSocketClass();

  /**
   * Returns the implementation's {@link DatagramSocket} constructor.
   *
   * @return The implementation's {@link DatagramSocket} constructor.
   */
  protected abstract AFDatagramSocket.Constructor<A> datagramSocketConstructor();

  /**
   * Returns the implementation's {@link DatagramChannel} class.
   *
   * @return The implementation's {@link DatagramChannel} class.
   */
  protected abstract Class<? extends AFDatagramChannel<A>> datagramChannelClass();
}
