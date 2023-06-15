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
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Objects;

import javax.net.SocketFactory;

/**
 * The base for a SocketFactory that connects to UNIX sockets.
 *
 * Typically, the "hostname" is used as a reference to a socketFile on the file system. The actual
 * mapping is left to the implementor.
 *
 * @see AFUNIXSocketFactory
 * @param <A> The supported address type.
 */
public abstract class AFSocketFactory<A extends AFSocketAddress> extends SocketFactory implements
    AFSocketAddressFromHostname<A> {

  /**
   * Creates a new socket factory instance.
   */
  protected AFSocketFactory() {
    super();
  }

  /**
   * Checks whether the given {@link InetAddress} is supported by this socket factory. If not, calls
   * to createSocket will cause a {@link SocketException}.
   *
   * By default, this only checks the hostname part of the address via
   * {@link #isHostnameSupported(String)}.
   *
   * @param address The address to check.
   * @return {@code true} if supported.
   */
  protected final boolean isInetAddressSupported(InetAddress address) {
    return address != null && isHostnameSupported(address.getHostName());
  }

  @Override
  public abstract Socket createSocket() throws SocketException;

  /**
   * Creates a new {@link AFSocket}, connected to the given address.
   *
   * @param addr The address to connect to.
   * @return The socket instance.
   * @throws IOException on error.
   */
  protected abstract Socket connectTo(A addr) throws IOException;

  @SuppressWarnings("unchecked")
  private Socket connectTo(SocketAddress addr) throws IOException {
    if (addr instanceof AFSocketAddress) {
      return connectTo((A) addr);
    } else {
      Socket sock = new Socket();
      sock.connect(addr);
      return sock;
    }
  }

  @Override
  public final Socket createSocket(String host, int port) throws IOException {
    if (!isHostnameSupported(host)) {
      throw new SocketException("Unsupported hostname");
    }
    if (port < 0) {
      throw new IllegalArgumentException("Illegal port");
    }

    SocketAddress socketAddress = addressFromHost(host, port);
    return connectTo(socketAddress);
  }

  @Override
  public final Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException {
    if (!isHostnameSupported(host)) {
      throw new SocketException("Unsupported hostname");
    }
    if (localPort < 0) {
      throw new IllegalArgumentException("Illegal local port");
    }
    // NOTE: we simply ignore localHost and localPort
    return createSocket(host, port);
  }

  @Override
  public final Socket createSocket(InetAddress address, int port) throws IOException {
    if (!isInetAddressSupported(address)) {
      throw new SocketException("Unsupported address");
    }
    String hostname = address.getHostName();
    if (!isHostnameSupported(hostname)) {
      throw new SocketException("Unsupported hostname");
    }
    return createSocket(hostname, port);
  }

  @Override
  public final Socket createSocket(InetAddress address, int port, InetAddress localAddress,
      int localPort) throws IOException {
    if (!isInetAddressSupported(address)) {
      throw new SocketException("Unsupported address");
    }
    if (localPort < 0) {
      throw new IllegalArgumentException("Illegal local port");
    }
    // NOTE: we simply ignore localAddress and localPort
    return createSocket(address, port);
  }

  /**
   * A socket factory that always connects to a fixed socket address, no matter what.
   */
  public static final class FixedAddressSocketFactory extends AFSocketFactory<AFSocketAddress> {
    private final SocketAddress forceAddr;

    /**
     * Creates a {@link FixedAddressSocketFactory}.
     *
     * @param address The address to use for all connections.
     */
    public FixedAddressSocketFactory(SocketAddress address) {
      super();
      this.forceAddr = Objects.requireNonNull(address);
    }

    @Override
    public boolean isHostnameSupported(String host) {
      return true;
    }

    @Override
    public SocketAddress addressFromHost(String host, int port) throws SocketException {
      return forceAddr;
    }

    @Override
    public Socket createSocket() throws SocketException {
      try {
        if (forceAddr instanceof AFSocketAddress) {
          AFSocket<?> socket = ((AFSocketAddress) forceAddr).getAddressFamily().newSocket();
          socket.forceConnectAddress(forceAddr);
          return socket;
        } else {
          return new Socket() {
            @Override
            public void connect(SocketAddress endpoint) throws IOException {
              super.connect(forceAddr);
            }

            @Override
            public void connect(SocketAddress endpoint, int timeout) throws IOException {
              super.connect(forceAddr, timeout);
            }
          };
        }
      } catch (SocketException e) {
        throw e;
      } catch (IOException e) {
        throw (SocketException) new SocketException().initCause(e);
      }
    }

    @Override
    protected Socket connectTo(AFSocketAddress addr) throws IOException {
      Socket sock = createSocket();
      sock.connect(forceAddr);
      return sock;
    }
  }
}
