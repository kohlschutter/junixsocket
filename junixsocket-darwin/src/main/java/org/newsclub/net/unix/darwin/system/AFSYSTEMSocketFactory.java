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
package org.newsclub.net.unix.darwin.system;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.newsclub.net.unix.AFSYSTEMSocketAddress;
import org.newsclub.net.unix.AFSocketFactory;

/**
 * The base for a SocketFactory that connects to AF_SYSTEM sockets.
 */
public abstract class AFSYSTEMSocketFactory extends AFSocketFactory<AFSYSTEMSocketAddress> {
  /**
   * Creates a {@link AFSYSTEMSocketFactory}.
   */
  protected AFSYSTEMSocketFactory() {
    super();
  }

  @Override
  public final Socket createSocket() throws SocketException {
    return configure(AFSYSTEMSocket.newInstance(this));
  }

  @Override
  protected final AFSYSTEMSocket connectTo(AFSYSTEMSocketAddress addr) throws IOException {
    return configure(AFSYSTEMSocket.connectTo(addr));
  }

  /**
   * Performs some optional configuration on a newly created socket.
   *
   * @param sock The socket.
   * @return The very socket.
   * @throws SocketException on error.
   */
  protected AFSYSTEMSocket configure(AFSYSTEMSocket sock) throws SocketException {
    return sock;
  }
}
