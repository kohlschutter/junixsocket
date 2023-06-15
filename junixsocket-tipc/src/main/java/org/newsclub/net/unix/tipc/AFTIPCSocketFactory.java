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
import java.net.Socket;
import java.net.SocketException;

import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketAddress.Scope;

/**
 * The base for a SocketFactory that connects to TIPC sockets.
 *
 * Typically, the "hostname" is used as a reference to a socketFile on the file system. The actual
 * mapping is left to the implementor.
 */
public abstract class AFTIPCSocketFactory extends AFSocketFactory<AFTIPCSocketAddress> {
  /**
   * Creates a {@link AFTIPCSocketFactory}.
   */
  protected AFTIPCSocketFactory() {
    super();
  }

  @Override
  public final Socket createSocket() throws SocketException {
    return configure(AFTIPCSocket.newInstance(this));
  }

  @Override
  protected final AFTIPCSocket connectTo(AFTIPCSocketAddress addr) throws IOException {
    return configure(AFTIPCSocket.connectTo(addr));
  }

  /**
   * Performs some optional configuration on a newly created socket.
   *
   * @param sock The socket.
   * @return The very socket.
   * @throws SocketException on error.
   */
  protected AFTIPCSocket configure(AFTIPCSocket sock) throws SocketException {
    return sock;
  }

  /**
   * Always connects sockets to the given TIPC type and instance.
   *
   * @author Christian Kohlschütter
   */
  public static class ServiceAddress extends AFTIPCSocketFactory {
    private final int type;
    private final int instance;

    /**
     * Creates an {@link AFTIPCSocketFactory} that always uses the given TIPC service type and
     * instance, implying cluster scope.
     *
     * @param type The service type.
     * @param instance The service instance.
     */
    public ServiceAddress(int type, int instance) {
      super();
      this.type = type;
      this.instance = instance;
    }

    @Override
    public final AFTIPCSocketAddress addressFromHost(String host, int port) throws SocketException {
      return AFTIPCSocketAddress.ofService(port, Scope.SCOPE_CLUSTER, type, instance, 0);
    }
  }
}
