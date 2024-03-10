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
package org.newsclub.net.unix.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * An {@link SSLServerSocketFactory} wrapper that applies settings specified with
 * {@link SSLContextBuilder}.
 *
 * @author Christian Kohlschütter
 */
final class BuilderSSLServerSocketFactory extends SSLServerSocketFactory {
  private final SSLServerSocketFactory factory;
  private final SSLParameters defaultParams;

  BuilderSSLServerSocketFactory(SSLServerSocketFactory factory, SSLParameters defaultParams) {
    super();
    this.factory = factory;
    this.defaultParams = defaultParams;
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return defaultParams.getCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return factory.getSupportedCipherSuites();
  }

  private SSLServerSocket init(SSLServerSocket socket) {
    socket.setSSLParameters(defaultParams);
    return socket;
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    return init((SSLServerSocket) factory.createServerSocket(port));
  }

  @Override
  public ServerSocket createServerSocket(int port, int backlog) throws IOException {
    return init((SSLServerSocket) factory.createServerSocket(port, backlog));
  }

  @Override
  public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress)
      throws IOException {
    return init((SSLServerSocket) factory.createServerSocket(port, backlog, ifAddress));
  }

  @Override
  public ServerSocket createServerSocket() throws IOException {
    return init((SSLServerSocket) factory.createServerSocket());
  }
}
