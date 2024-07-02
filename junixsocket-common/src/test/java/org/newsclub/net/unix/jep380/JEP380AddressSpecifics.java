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
package org.newsclub.net.unix.jep380;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Path;

import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AddressSpecifics;
import org.newsclub.net.unix.CloseablePair;

import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

public final class JEP380AddressSpecifics implements AddressSpecifics<SocketAddress> {
  public static final AddressSpecifics<SocketAddress> INSTANCE = new JEP380AddressSpecifics();

  private final ProtocolFamily pf;
  private final Method addressOfMethod;
  private final Method socketChannelOpenMethod;
  private final Method serverSocketChannelOpenMethod;
  private final Method datagramChannelOpenMethod;

  private final SelectorProvider sp;

  private JEP380AddressSpecifics() {
    this.pf = unixProtocolFamilyIfAvailable();
    this.addressOfMethod = tryResolve("java.net.UnixDomainSocketAddress", "of", Path.class);
    this.socketChannelOpenMethod = tryResolve("java.nio.channels.SocketChannel", "open",
        ProtocolFamily.class);
    this.serverSocketChannelOpenMethod = tryResolve("java.nio.channels.ServerSocketChannel", "open",
        ProtocolFamily.class);
    this.datagramChannelOpenMethod = tryResolve("java.nio.channels.DatagramChannel", "open",
        ProtocolFamily.class);

    this.sp = new SelectorProvider() {
      private final SelectorProvider upstream = SelectorProvider.provider();

      @Override
      public SocketChannel openSocketChannel() throws IOException {
        return newSocketChannel();
      }

      @Override
      public ServerSocketChannel openServerSocketChannel() throws IOException {
        return newServerSocketChannel();
      }

      @Override
      public AbstractSelector openSelector() throws IOException {
        return upstream.openSelector();
      }

      @Override
      public Pipe openPipe() throws IOException {
        return upstream.openPipe();
      }

      @Override
      public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        return upstream.openDatagramChannel(family);
      }

      @Override
      public DatagramChannel openDatagramChannel() throws IOException {
        return newDatagramChannel();
      }
    };
  }

  private static Method tryResolve(String className, String methodName, Class<?>... argType) {
    try {
      Class<?> udsa = Class.forName(className);
      return udsa.getMethod(methodName, argType);
    } catch (Exception e) {
      return null;
    }
  }

  private ProtocolFamily unixProtocolFamily() {
    if (pf == null) {
      throw new UnsupportedOperationException("StandardProtocolFamily.UNIX is unavailable");
    }
    return pf;
  }

  public static ProtocolFamily unixProtocolFamilyIfAvailable() {
    for (ProtocolFamily pf : StandardProtocolFamily.values()) {
      if ("UNIX".equals(pf.name())) {
        return pf;
      }
    }
    return null;
  }

  private SocketAddress addressOfPath(Path p) {
    if (addressOfMethod == null) {
      throw new UnsupportedOperationException("UnixDomainSocketAddress.of is unavailable");
    }
    try {
      return (SocketAddress) addressOfMethod.invoke(null, p);
    } catch (Exception e) {
      throw new UnsupportedOperationException("UnixDomainSocketAddress.of is unavailable", e);
    }
  }

  public SocketAddress wildcardBindAddress() throws IOException {
    AFUNIXSocketAddress tmpAddr = AFUNIXSocketAddress.ofNewTempFile();
    return addressOfPath(tmpAddr.getFile().toPath());
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
    throw new TestAbortedNotAnIssueException("unsupported by Java SDK Unix Domain sockets");
  }

  @Override
  public DatagramSocket newDatagramSocket() throws IOException {
    throw new TestAbortedNotAnIssueException("unsupported by Java SDK Unix Domain sockets");
  }

  @Override
  public ServerSocket newServerSocket() throws IOException {
    throw new TestAbortedNotAnIssueException("unsupported by Java SDK Unix Domain sockets");
  }

  @Override
  public SocketAddress newTempAddressForDatagram() throws IOException {
    return newTempAddress();
  }

  @Override
  public SocketAddress unwrap(InetAddress addr, int port) throws SocketException {
    throw new TestAbortedNotAnIssueException("unsupported by Java SDK Unix Domain sockets");
  }

  @Override
  public SelectorProvider selectorProvider() {
    return sp;
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
    throw new TestAbortedNotAnIssueException("unsupported by Java SDK Unix Domain sockets");
  }

  @Override
  public Socket connectTo(SocketAddress addr) throws IOException {
    throw new TestAbortedNotAnIssueException("unsupported by Java SDK Unix Domain sockets");
  }

  @Override
  public ServerSocket newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    throw new TestAbortedNotAnIssueException("unsupported by Java SDK Unix Domain sockets");
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
  public SocketChannel newSocketChannel() throws IOException {
    try {
      return (SocketChannel) socketChannelOpenMethod.invoke(null, unixProtocolFamily());
    } catch (Exception e) {
      throw new UnsupportedOperationException(e);
    }
  }

  @Override
  public DatagramChannel newDatagramChannel() throws IOException {
    try {
      return (DatagramChannel) datagramChannelOpenMethod.invoke(null, unixProtocolFamily());
    } catch (Exception e) {
      throw new UnsupportedOperationException(e);
    }
  }

  @Override
  public ServerSocketChannel newServerSocketChannel() throws IOException {
    try {
      return (ServerSocketChannel) serverSocketChannelOpenMethod.invoke(null, unixProtocolFamily());
    } catch (Exception e) {
      throw new UnsupportedOperationException(e);
    }
  }
}
