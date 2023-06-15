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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSelectorProvider;
import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.newsclub.net.unix.AddressSpecifics;
import org.newsclub.net.unix.AddressUnavailableSocketException;
import org.newsclub.net.unix.CloseablePair;
import org.newsclub.net.unix.InvalidSocketException;

import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

public final class AFVSOCKAddressSpecifics implements AddressSpecifics<AFVSOCKSocketAddress> {
  public static final AddressSpecifics<AFVSOCKSocketAddress> INSTANCE =
      new AFVSOCKAddressSpecifics();

  /**
   * Older kernels are unable to communicate locally when CID == 2 (VMADDR_CID_HOST).
   */
  static final String KERNEL_TOO_OLD =
      "Kernel may be too old or not configured for full VSOCK support";

  /**
   * Access denied...
   */
  static final String ACCESS_DENIED = "Access to VSOCK resources (e.g., /dev/vsock) were denied";

  private AFVSOCKAddressSpecifics() {
  }

  @Override
  public AFSocketAddress newTempAddress() throws IOException {
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
  public AFSocketAddress newTempAddressForDatagram() throws IOException {
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
    try {
      return AFVSOCKSocketPair.open();
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, "");
    }
  }

  @Override
  public CloseablePair<? extends DatagramChannel> newDatagramSocketPair() throws IOException {
    try {
      return AFVSOCKSocketPair.openDatagram();
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, "");
    }
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr) throws IOException {
    try {
      return AFVSOCKServerSocket.bindOn((AFVSOCKSocketAddress) addr);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, addr);
    }
  }

  private static SocketException handleSocketException(SocketException e, String msg)
      throws SocketException, IOException {

    final String shortMsg;
    if (e instanceof AddressUnavailableSocketException) {
      shortMsg = ACCESS_DENIED;
    } else if (e instanceof InvalidSocketException) {
      shortMsg = KERNEL_TOO_OLD;
    } else {
      return e;
    }

    throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
        msg == null || msg.isEmpty() ? shortMsg : shortMsg + ": " + msg, e);
  }

  private static SocketException handleSocketException(SocketException e, SocketAddress addr)
      throws IOException {
    if (!(addr instanceof AFVSOCKSocketAddress)) {
      return e;
    }
    AFVSOCKSocketAddress sa = (AFVSOCKSocketAddress) addr;
    switch (sa.getVSOCKCID()) {
      case AFVSOCKSocketAddress.VMADDR_CID_HOST:
        return handleSocketException(e, "Cannot connect to addresses with CID=" + sa.getVSOCKCID());
      case AFVSOCKSocketAddress.VMADDR_CID_LOCAL:
        return handleSocketException(e, "Cannot connect to addresses with CID=" + sa.getVSOCKCID()
            + "; try \"modprobe vsock_loopback\"");
      default:
        return e;
    }
  }

  @Override
  public void bindServerSocket(ServerSocket serverSocket, SocketAddress bindpoint)
      throws IOException {
    try {
      serverSocket.bind(bindpoint);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, bindpoint);
    }
  }

  @Override
  public void bindServerSocket(ServerSocket serverSocket, SocketAddress bindpoint, int backlog)
      throws IOException {
    try {
      serverSocket.bind(bindpoint, backlog);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, bindpoint);
    }
  }

  @Override
  public void bindServerSocket(ServerSocketChannel serverSocketChannel, SocketAddress bindpoint)
      throws IOException {
    try {
      serverSocketChannel.bind(bindpoint);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, bindpoint);
    }
  }

  @Override
  public void bindServerSocket(ServerSocketChannel serverSocketChannel, SocketAddress bindpoint,
      int backlog) throws IOException {
    try {
      serverSocketChannel.bind(bindpoint, backlog);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, bindpoint);
    }
  }

  @Override
  public Socket connectTo(SocketAddress addr) throws IOException {
    AFVSOCKSocketAddress sa = (AFVSOCKSocketAddress) addr;
    try {
      return AFVSOCKSocket.connectTo(sa);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, sa);
    }
  }

  @Override
  public void connectSocket(Socket sock, SocketAddress addr) throws IOException {
    try {
      sock.connect(addr);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, addr);
    }
  }

  @Override
  public boolean connectSocket(SocketChannel sock, SocketAddress addr) throws IOException {
    try {
      return sock.connect(addr);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, addr);
    }
  }

  @Override
  public AFServerSocket<?> newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    try {
      return AFVSOCKServerSocket.bindOn((AFVSOCKSocketAddress) addr, deleteOnClose);
    } catch (InvalidSocketException e) {
      throw handleSocketException(e, addr);
    }
  }

  @Override
  public CloseablePair<? extends Socket> newInterconnectedSockets() throws IOException {
    CloseablePair<? extends SocketChannel> sp = newSocketPair();
    return new CloseablePair<>(sp.getFirst().socket(), sp.getSecond().socket());
  }
}
