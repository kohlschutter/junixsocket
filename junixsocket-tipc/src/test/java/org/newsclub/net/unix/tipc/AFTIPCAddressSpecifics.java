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
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSelectorProvider;
import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketAddress.Scope;
import org.newsclub.net.unix.AddressSpecifics;
import org.newsclub.net.unix.CloseablePair;

import com.kohlschutter.util.ProcessUtil;

public final class AFTIPCAddressSpecifics implements AddressSpecifics<AFTIPCSocketAddress> {
  private static final int TIPC_TYPE = (int) (ProcessUtil.getPid() % Integer.MAX_VALUE) + 64;
  private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

  public static final AddressSpecifics<AFTIPCSocketAddress> INSTANCE = new AFTIPCAddressSpecifics();

  private AFTIPCAddressSpecifics() {
  }

  @Override
  public AFSocketAddress newTempAddress() throws IOException {
    return AFTIPCSocketAddress.ofService(Scope.SCOPE_NODE, TIPC_TYPE, INSTANCE_COUNTER
        .incrementAndGet());
  }

  @Override
  public AFSocket<?> newSocket() throws IOException {
    return AFTIPCSocket.newInstance();
  }

  @Override
  public AFSocket<?> newStrictSocket() throws IOException {
    return AFTIPCSocket.newStrictInstance();
  }

  @Override
  public AFDatagramSocket<?> newDatagramSocket() throws IOException {
    return AFTIPCDatagramSocket.newInstance();
  }

  @Override
  public DatagramChannel newDatagramChannel() throws IOException {
    return AFTIPCDatagramSocket.newInstance().getChannel();
  }

  @Override
  public AFServerSocket<?> newServerSocket() throws IOException {
    return AFTIPCServerSocket.newInstance();
  }

  @Override
  public AFSocketAddress newTempAddressForDatagram() throws IOException {
    return AFTIPCSocketAddress.ofService(Scope.SCOPE_NODE, TIPC_TYPE, INSTANCE_COUNTER
        .incrementAndGet());
  }

  @Override
  public AFSocketAddress unwrap(InetAddress addr, int port) throws SocketException {
    return AFTIPCSocketAddress.unwrap(addr, port);
  }

  @Override
  public AFSelectorProvider<?> selectorProvider() {
    return AFTIPCSelectorProvider.provider();
  }

  @Override
  public CloseablePair<? extends SocketChannel> newSocketPair() throws IOException {
    return AFTIPCSocketPair.open();
  }

  @Override
  public CloseablePair<? extends DatagramChannel> newDatagramSocketPair() throws IOException {
    return AFTIPCSocketPair.openDatagram();
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr) throws IOException {
    return AFTIPCServerSocket.bindOn((AFTIPCSocketAddress) addr);
  }

  @Override
  public Socket connectTo(SocketAddress socket) throws IOException {
    return AFTIPCSocket.connectTo((AFTIPCSocketAddress) socket);
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return AFTIPCServerSocket.bindOn((AFTIPCSocketAddress) addr, deleteOnClose);
  }

  @Override
  public CloseablePair<? extends Socket> newInterconnectedSockets() throws IOException {
    CloseablePair<? extends SocketChannel> sp = newSocketPair();
    return new CloseablePair<>(sp.getFirst().socket(), sp.getSecond().socket());
  }
}
