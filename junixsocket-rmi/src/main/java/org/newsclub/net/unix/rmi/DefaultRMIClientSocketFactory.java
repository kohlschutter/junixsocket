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
package org.newsclub.net.unix.rmi;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * An implementation of {@link RMIClientSocketFactory}.
 *
 * @see AFRMISocketFactory
 */
@SuppressFBWarnings("SING_SINGLETON_IMPLEMENTS_SERIALIZABLE")
public final class DefaultRMIClientSocketFactory implements RMIClientSocketFactory, Serializable {
  private static final long serialVersionUID = 1L;

  private static final DefaultRMIClientSocketFactory INSTANCE = new DefaultRMIClientSocketFactory();

  private DefaultRMIClientSocketFactory() {
  }

  /**
   * Returns the singleton instance for DefaultRMIClientSocketFactory.
   *
   * @return The singleton.
   */
  public static DefaultRMIClientSocketFactory getInstance() {
    return INSTANCE;
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    Socket socket = new Socket(host, port);
    // socket.setSoTimeout(60 * 60 * 1000);
    // socket.setSoLinger(false, 0);
    return socket;
  }

  // we must implement this (see RMIClientSocketFactory)
  @Override
  public boolean equals(Object obj) {
    return obj instanceof DefaultRMIClientSocketFactory;
  }

  // we must implement this (see RMIClientSocketFactory)
  @Override
  public int hashCode() {
    return 1;
  }
}
