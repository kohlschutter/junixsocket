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
package org.newsclub.net.unix.domainjava;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AddressSpecifics;
import org.newsclub.net.unix.CloseablePair;
import org.opentest4j.TestAbortedException;

public final class JavaUnixDomainAddressSpecifics implements AddressSpecifics<SocketAddress> {
  public static final AddressSpecifics<SocketAddress> INSTANCE =
      new JavaUnixDomainAddressSpecifics();

  private JavaUnixDomainAddressSpecifics() {
  }

  public static SocketAddress wildcardBindAddress() throws IOException {
    AFUNIXSocketAddress tmpAddr = AFUNIXSocketAddress.ofNewTempFile();
    return UnixDomainSocketAddress.of(tmpAddr.getFile().toPath());
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
    throw new TestAbortedException("Unsupported operation");
  }

  @Override
  public DatagramSocket newDatagramSocket() throws IOException {
    throw new TestAbortedException("Unsupported operation");
  }

  @Override
  public SocketChannel newSocketChannel() throws IOException {
    return SocketChannel.open(StandardProtocolFamily.UNIX);
  }

  @Override
  public DatagramChannel newDatagramChannel() throws IOException {
    return DatagramChannel.open(StandardProtocolFamily.UNIX);
  }

  @Override
  public ServerSocket newServerSocket() throws IOException {
    throw new TestAbortedException("Unsupported operation");
  }

  @Override
  public SocketAddress newTempAddressForDatagram() throws IOException {
    return newTempAddress();
  }

  @Override
  public SocketAddress unwrap(InetAddress addr, int port) throws SocketException {
    throw new TestAbortedException("Unsupported operation");
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
    DatagramChannel ds1 = newDatagramChannel();
    ds1.bind(newTempAddress());
    DatagramChannel ds2 = newDatagramChannel();
    ds2.bind(newTempAddress());

    ds1.connect(ds2.getLocalAddress());
    ds2.connect(ds1.getLocalAddress());
    return new CloseablePair<>(ds1, ds2, () -> {
      ds1.close();
      ds2.close();
    });
  }

  @Override
  public ServerSocket newServerSocketBindOn(SocketAddress addr) throws IOException {
    throw new TestAbortedException("Unsupported operation");
  }

  @Override
  public Socket connectTo(SocketAddress addr) throws IOException {
    throw new TestAbortedException("Unsupported operation");
  }

  @Override
  public ServerSocket newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    throw new TestAbortedException("Unsupported operation");
  }

  @Override
  public String addressFamilyString() {
    return "Java UNIX domain socket";
  }

  @Override
  public String summaryImportantMessage(String message) {
    return message;
  }

  @Override
  public ServerSocketChannel newServerSocketChannel() throws IOException {
    return ServerSocketChannel.open(StandardProtocolFamily.UNIX);
  }
}
