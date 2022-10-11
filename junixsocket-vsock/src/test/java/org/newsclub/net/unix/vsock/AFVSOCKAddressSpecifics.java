/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.newsclub.net.unix.AddressSpecifics;
import org.newsclub.net.unix.CloseablePair;

public final class AFVSOCKAddressSpecifics implements AddressSpecifics<AFVSOCKSocketAddress> {
  public static final AddressSpecifics<AFVSOCKSocketAddress> INSTANCE =
      new AFVSOCKAddressSpecifics();

  private AFVSOCKAddressSpecifics() {
  }

  @Override
  public AFSocketAddress initServerSocketBindAddress() throws IOException {
    return AFVSOCKSocketAddress.ofAnyLocalPort();
  }

  @Override
  public AFSocket<?> newSocket() throws IOException {
    return AFVSOCKSocket.newInstance();
  }

  @Override
  public AFSocket<?> newStrictSocket() throws IOException {
    return AFVSOCKSocket.newStrictInstance();
  }

  @Override
  public AFDatagramSocket<?> newDatagramSocket() throws IOException {
    return AFVSOCKDatagramSocket.newInstance();
  }

  @Override
  public DatagramChannel newDatagramChannel() throws IOException {
    return AFVSOCKDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFServerSocket<?> newServerSocket() throws IOException {
    return AFVSOCKServerSocket.newInstance();
  }

  @Override
  public AFSocketAddress newTempAddress() throws IOException {
    return AFVSOCKSocketAddress.ofAnyPort();
  }

  @Override
  public AFSocketAddress unwrap(InetAddress addr, int port) throws SocketException {
    return AFVSOCKSocketAddress.unwrap(addr, port);
  }

  @Override
  public AFSelectorProvider<?> selectorProvider() {
    return AFVSOCKSelectorProvider.provider();
  }

  @Override
  public CloseablePair<? extends SocketChannel> newSocketPair() throws IOException {
    return AFVSOCKSocketPair.open();
  }

  @Override
  public CloseablePair<? extends DatagramChannel> newDatagramSocketPair() throws IOException {
    return AFVSOCKSocketPair.openDatagram();
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr) throws IOException {
    return AFVSOCKServerSocket.bindOn((AFVSOCKSocketAddress) addr);
  }

  @Override
  public Socket connectTo(SocketAddress socket) throws IOException {
    return AFVSOCKSocket.connectTo((AFVSOCKSocketAddress) socket);
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return AFVSOCKServerSocket.bindOn((AFVSOCKSocketAddress) addr, deleteOnClose);
  }

  @Override
  public CloseablePair<? extends Socket> newInterconnectedSockets() throws IOException {
    CloseablePair<? extends SocketChannel> sp = newSocketPair();
    return new CloseablePair<>(sp.getFirst().socket(), sp.getSecond().socket());
  }

}
