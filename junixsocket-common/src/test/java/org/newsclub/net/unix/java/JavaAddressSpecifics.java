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
package org.newsclub.net.unix.java;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import org.newsclub.net.unix.AddressSpecifics;
import org.newsclub.net.unix.CloseablePair;
import org.opentest4j.TestAbortedException;

public final class JavaAddressSpecifics implements AddressSpecifics<InetSocketAddress> {
  public static final AddressSpecifics<InetSocketAddress> INSTANCE = new JavaAddressSpecifics();

  private JavaAddressSpecifics() {
  }

  public static SocketAddress wildcardBindAddress() throws IOException {
    SocketAddress bindAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    try (ServerSocket sock = new ServerSocket()) {
      sock.bind(bindAddr);
    } catch (BindException e) {
      throw new TestAbortedException("Cannot bind to " + bindAddr, e);
    }
    return bindAddr;
  }

  @Override
  public SocketAddress newTempAddress() throws IOException {
    return wildcardBindAddress();
  }

  @Override
  public Socket newStrictSocket() throws IOException {
    return newSocket();
  }

  @Override
  public Socket newSocket() throws IOException {
    return new Socket();
  }

  @Override
  public DatagramSocket newDatagramSocket() throws IOException {
    return new DatagramSocket();
  }

  @Override
  public DatagramChannel newDatagramChannel() throws IOException {
    return selectorProvider().openDatagramChannel();
  }

  @Override
  public ServerSocket newServerSocket() throws IOException {
    return new ServerSocket();
  }

  @Override
  public SocketAddress newTempAddressForDatagram() throws IOException {
    return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
  }

  @Override
  public SocketAddress unwrap(InetAddress addr, int port) throws SocketException {
    return new InetSocketAddress(addr, port);
  }

  @Override
  public SelectorProvider selectorProvider() {
    return SelectorProvider.provider();
  }

  @Override
  public CloseablePair<? extends SocketChannel> newSocketPair() throws IOException {
    CloseablePair<? extends Socket> p1 = newInterconnectedSockets();
    return new CloseablePair<>(p1.getFirst().getChannel(), p1.getSecond().getChannel(), p1);
  }

  @Override
  public CloseablePair<? extends DatagramChannel> newDatagramSocketPair() throws IOException {
    DatagramSocket ds1 = new DatagramSocket(0);
    DatagramSocket ds2 = new DatagramSocket(0);
    ds1.connect(ds2.getLocalSocketAddress());
    ds2.connect(ds1.getLocalSocketAddress());
    return new CloseablePair<>(ds1.getChannel(), ds2.getChannel(), () -> {
      ds1.close();
      ds2.close();
    });
  }

  @Override
  public ServerSocket newServerSocketBindOn(SocketAddress addr) throws IOException {
    InetSocketAddress inetAddr = (InetSocketAddress) addr;
    if (!inetAddr.getAddress().isAnyLocalAddress()) {
      throw new IllegalArgumentException("Not a local address: " + inetAddr);
    }
    return new ServerSocket(inetAddr.getPort());
  }

  @Override
  public Socket connectTo(SocketAddress addr) throws IOException {
    InetSocketAddress isa = (InetSocketAddress) addr;
    return new Socket(isa.getAddress(), isa.getPort());
  }

  @Override
  public ServerSocket newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    InetSocketAddress inetAddr = (InetSocketAddress) addr;
    if (!inetAddr.getAddress().isAnyLocalAddress()) {
      throw new IllegalArgumentException("Not a local address: " + inetAddr);
    }
    return new ServerSocket(inetAddr.getPort());
  }
}
