/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * The server part of an AF_UNIX domain socket.
 * 
 * @author Christian Kohlschütter
 */
public class AFUNIXServerSocket extends ServerSocket {
  private enum Mode {
    DEFAULT, RMI;
  };

  private final AFUNIXSocketImpl implementation;
  private AFUNIXSocketAddress boundEndpoint;

  /**
   * Constructs a new, unconnected instance.
   * 
   * @throws IOException if the operation fails.
   */
  AFUNIXServerSocket() throws IOException {
    this(Mode.DEFAULT);
  }

  AFUNIXServerSocket(Mode mode) throws IOException {
    super();
    setReuseAddress(true);

    this.implementation = (mode == Mode.RMI) ? new AFUNIXSocketImpl.ForRMI() : new AFUNIXSocketImpl();
    NativeUnixSocket.initServerImpl(this, implementation);

    NativeUnixSocket.setCreatedServer(this);
  }

  /**
   * Returns a new, unbound AF_UNIX {@link ServerSocket}.
   * 
   * @return The new, unbound {@link AFUNIXServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket newInstance() throws IOException {
    return new AFUNIXServerSocket(Mode.DEFAULT);
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given
   * {@link AFUNIXSocketAddress}.
   * 
   * @param addr The socket file to bind to.
   * @return The new, unbound {@link AFUNIXServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final AFUNIXSocketAddress addr) throws IOException {
    AFUNIXServerSocket socket = newInstance();
    socket.bind(addr);
    return socket;
  }

  @Override
  public void bind(SocketAddress endpoint, int backlog) throws IOException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (isBound()) {
      throw new SocketException("Already bound");
    }
    if (!(endpoint instanceof AFUNIXSocketAddress)) {
      throw new IOException("Can only bind to endpoints of type " + AFUNIXSocketAddress.class
          .getName());
    }

    implementation.bind(endpoint, getReuseAddress() ? -1 : 0);
    boundEndpoint = (AFUNIXSocketAddress) endpoint;

    implementation.listen(backlog);
  }

  @Override
  public boolean isBound() {
    return boundEndpoint != null;
  }

  @Override
  public boolean isClosed() {
    return super.isClosed() || (isBound() && !implementation.getFD().valid());
  }

  @Override
  public AFUNIXSocket accept() throws IOException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    AFUNIXSocket as = newSocketInstance();
    implementation.accept(as.impl);
    as.addr = boundEndpoint;
    NativeUnixSocket.setConnected(as);
    return as;
  }

  protected AFUNIXSocket newSocketInstance() throws IOException {
    return AFUNIXSocket.newInstance();
  }

  @Override
  public String toString() {
    if (!isBound()) {
      return "AFUNIXServerSocket[unbound]";
    }
    return "AFUNIXServerSocket[" + boundEndpoint.toString() + "]";
  }

  @Override
  public void close() throws IOException {
    if (isClosed()) {
      return;
    }

    super.close();
    implementation.close();
  }

  /**
   * Checks whether everything is setup to support AF_UNIX sockets.
   * 
   * @return {@code true} if supported.
   */
  public static boolean isSupported() {
    return NativeUnixSocket.isLoaded();
  }

  public static class ForRMI extends AFUNIXServerSocket {
    protected ForRMI() throws IOException {
      super(Mode.RMI);
    }

    public static AFUNIXServerSocket newInstance() throws IOException {
      return new AFUNIXServerSocket(Mode.RMI);
    }

    public static AFUNIXServerSocket bindOn(final AFUNIXSocketAddress addr) throws IOException {
      AFUNIXServerSocket socket = new AFUNIXServerSocket.ForRMI();
      socket.bind(addr);
      return socket;
    }

    @Override
    protected AFUNIXSocket newSocketInstance() throws IOException {
      return AFUNIXSocket.newInstanceForRMI();
    }
  }
}
