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
package org.newsclub.net.unix.rmi;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

/**
 * An implementation of {@link RMIServerSocketFactory}.
 *
 * @see AFRMISocketFactory
 */
public class DefaultRMIServerSocketFactory implements RMIServerSocketFactory, Serializable {
  private static final long serialVersionUID = 1L;
  private static final DefaultRMIServerSocketFactory INSTANCE = new DefaultRMIServerSocketFactory();

  /**
   * Creates a new {@link DefaultRMIClientSocketFactory}.
   */
  public DefaultRMIServerSocketFactory() {
    super();
  }

  /**
   * Returns the singleton instance for DefaultRMIServerSocketFactory.
   *
   * @return The singleton.
   */
  public static DefaultRMIServerSocketFactory getInstance() {
    return INSTANCE;
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    ServerSocket socket = new ServerSocket(port);
    // socket.setSoTimeout(60 * 60 * 1000);
    return socket;
  }

  // we must implement this (see RMIServerSocketFactory)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof DefaultRMIServerSocketFactory;
  }

  // we must implement this (see RMIServerSocketFactory)
  @Override
  public int hashCode() {
    return 1;
  }
}
