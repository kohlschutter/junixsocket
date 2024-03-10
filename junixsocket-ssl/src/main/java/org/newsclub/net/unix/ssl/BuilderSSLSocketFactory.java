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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * An {@link SSLSocketFactory} wrapper that applies settings specified with
 * {@link SSLContextBuilder}.
 *
 * @author Christian Kohlschütter
 */
final class BuilderSSLSocketFactory extends SSLSocketFactory {
  private final SSLSocketFactory factory;
  private final SSLParameters defaultParams;
  private final boolean clientMode;
  private final SSLContext context;
  private final SocketFactory underlyingSocketFactory;

  BuilderSSLSocketFactory(boolean clientMode, SSLContext context, SSLSocketFactory factory,
      SSLParameters defaultParams, SocketFactory socketFactory) {
    super();
    this.clientMode = clientMode;
    this.context = context;
    this.factory = factory;
    this.defaultParams = defaultParams;
    this.underlyingSocketFactory = socketFactory;
  }

  SSLContext getContext() {
    return context;
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return defaultParams.getCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return factory.getSupportedCipherSuites();
  }

  private SSLSocket init(SSLSocket socket) {
    socket.setSSLParameters(defaultParams);
    socket.setUseClientMode(clientMode);
    return socket;
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose)
      throws IOException {
    return init(new BuilderSSLSocket((SSLSocket) factory.createSocket(s, host, port, autoClose), s,
        autoClose));
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
    Socket socket = underlyingSocketFactory.createSocket(host, port);
    return createSocket(socket, host, port, true);
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException, UnknownHostException {
    Socket socket = underlyingSocketFactory.createSocket(host, port, localHost, localPort);
    return createSocket(socket, host, port, true);
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    Socket socket = underlyingSocketFactory.createSocket(host, port);
    return createSocket(socket, host.getHostName(), port, true);
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    Socket socket = underlyingSocketFactory.createSocket(address, port, localAddress, localPort);
    return createSocket(socket, address.getHostName(), port, true);
  }

  @Override
  public Socket createSocket(Socket s, InputStream consumed, boolean autoClose) throws IOException {
    return init(new BuilderSSLSocket((SSLSocket) factory.createSocket(s, consumed, autoClose), s,
        autoClose));
  }

  @Override
  public Socket createSocket() throws IOException {
    throw (SocketException) new SocketException("Unconnected sockets not implemented").initCause(
        new UnsupportedOperationException());
  }
}
