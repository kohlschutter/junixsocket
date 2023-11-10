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
import java.net.Socket;
import java.net.SocketException;

import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * The base for a SocketFactory that connects to VSOCK sockets.
 */
public abstract class AFVSOCKSocketFactory extends AFSocketFactory<AFVSOCKSocketAddress> {
  /**
   * Creates a {@link AFVSOCKSocketFactory}.
   */
  protected AFVSOCKSocketFactory() {
    super();
  }

  @Override
  public final Socket createSocket() throws SocketException {
    return configure(AFVSOCKSocket.newInstance(this));
  }

  @Override
  protected final AFVSOCKSocket connectTo(AFVSOCKSocketAddress addr) throws IOException {
    return configure(AFVSOCKSocket.connectTo(addr));
  }

  /**
   * Performs some optional configuration on a newly created socket.
   *
   * @param sock The socket.
   * @return The very socket.
   * @throws SocketException on error.
   */
  protected AFVSOCKSocket configure(AFVSOCKSocket sock) throws SocketException {
    return sock;
  }

  /**
   * Always connects sockets to the given VSOCK type and instance.
   *
   * @author Christian Kohlschütter
   */
  public static class FixedAddress extends AFVSOCKSocketFactory {
    private final AFVSOCKSocketAddress addr;

    /**
     * Creates an {@link AFVSOCKSocketFactory} that always uses the given VSOCK address.
     *
     * @param port The VSOCK port.
     * @param cid The VSOCK CID.
     * @throws SocketException on internal error.
     */
    public FixedAddress(int port, int cid) throws SocketException {
      this(AFVSOCKSocketAddress.ofPortAndCID(port, cid));
    }

    /**
     * Creates an {@link AFVSOCKSocketFactory} that always uses the given VSOCK address.
     *
     * @param addr The address.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public FixedAddress(AFVSOCKSocketAddress addr) {
      super();
      this.addr = addr;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    @Override
    public final AFVSOCKSocketAddress addressFromHost(String host, int javaPort)
        throws SocketException {
      return addr;
    }
  }
}
