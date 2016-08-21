/**
 * junixsocket
 *
 * Copyright (c) 2009,2014 Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix.socketfactory;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import javax.net.SocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * A simple {@link SocketFactory} which takes the connection path and port as constructor parameters.
 * <p>
 * This implementation breaks the SocketFactory-spec by returning a connected socket in the createSocket-method.
 * This is necessary to work around some limitations in how the
 * <a href="https://github.com/pgjdbc/pgjdbc">PGJDBC-library</a> uses a socket factory.
 * </p>
 * <p>
 * To us this in pgjdbc, add the following to your connection-url:
 * <code>
 * &amp;socketFactory=org.newsclub.net.unix.socketfactory.PostgresqlAFUNIXSocketFactory&amp;socketFactoryArg=[path-to-the-unix-socket]
 * For many distros the default path is /var/run/postgresql/.s.PGSQL.5432
 * </code>
 * </p>
 */
public class PostgresqlAFUNIXSocketFactory extends SocketFactory {

  private final int port;
  private final File file;

  public PostgresqlAFUNIXSocketFactory(String path) {
    this(path, 0);
  }

  public PostgresqlAFUNIXSocketFactory(String path, int port) {
    this.file = new File(path);
    this.port = port;
  }


  /**
   * Creates a connected socket to the file/port given as constructor parameters.
   */
  @Override
  public Socket createSocket() throws IOException {
    AFUNIXSocket s = AFUNIXSocket.newInstance();
    s.connect(new AFUNIXSocketAddress(file, port));
    return s;
  }

  @Override
  public Socket createSocket(String s, int i) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
    throw new UnsupportedOperationException();
  }
}
