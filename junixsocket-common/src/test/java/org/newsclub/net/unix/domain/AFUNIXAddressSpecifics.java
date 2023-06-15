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
package org.newsclub.net.unix.domain;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;

import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSelectorProvider;
import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXDatagramSocket;
import org.newsclub.net.unix.AFUNIXSelectorProvider;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketPair;
import org.newsclub.net.unix.AddressSpecifics;
import org.newsclub.net.unix.CloseablePair;
import org.newsclub.net.unix.SocketTestBase;

public final class AFUNIXAddressSpecifics implements AddressSpecifics<AFUNIXSocketAddress> {
  public static final AddressSpecifics<AFUNIXSocketAddress> INSTANCE = new AFUNIXAddressSpecifics();

  private AFUNIXAddressSpecifics() {
  }

  @Override
  public AFSocketAddress newTempAddress() throws IOException {
    return AFUNIXSocketAddress.of(SocketTestBase.socketFile());
  }

  @Override
  public AFSocket<?> newSocket() throws IOException {
    return AFUNIXSocket.newInstance();
  }

  @Override
  public AFSocket<?> newStrictSocket() throws IOException {
    return AFUNIXSocket.newStrictInstance();
  }

  @Override
  public AFDatagramSocket<?> newDatagramSocket() throws IOException {
    return AFUNIXDatagramSocket.newInstance();
  }

  @Override
  public DatagramChannel newDatagramChannel() throws IOException {
    return AFUNIXDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFServerSocket<?> newServerSocket() throws IOException {
    return AFUNIXServerSocket.newInstance();
  }

  @Override
  public AFSocketAddress newTempAddressForDatagram() throws IOException {
    return AFUNIXSocketAddress.of(SocketTestBase.newTempFile());
  }

  @Override
  public AFSocketAddress unwrap(InetAddress addr, int port) throws SocketException {
    return AFUNIXSocketAddress.unwrap(addr, port);
  }

  @Override
  public AFSelectorProvider<?> selectorProvider() {
    return AFUNIXSelectorProvider.provider();
  }

  @Override
  public CloseablePair<? extends SocketChannel> newSocketPair() throws IOException {
    return AFUNIXSocketPair.open();
  }

  @Override
  public CloseablePair<? extends DatagramChannel> newDatagramSocketPair() throws IOException {
    return AFUNIXSocketPair.openDatagram();
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr) throws IOException {
    return AFUNIXServerSocket.bindOn((AFUNIXSocketAddress) addr);
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return AFUNIXServerSocket.bindOn((AFUNIXSocketAddress) addr, deleteOnClose);
  }

  @Override
  public Socket connectTo(SocketAddress socket) throws IOException {
    return AFUNIXSocket.connectTo((AFUNIXSocketAddress) socket);
  }
}
