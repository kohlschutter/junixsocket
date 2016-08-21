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
 * A simple {@link SocketFactory} which returns {@link AFUNIXSocket}s.
 */
public class SimpleAFUNIXSocketFactory extends SocketFactory {

  @Override
  public Socket createSocket() throws IOException {
    return AFUNIXSocket.newInstance();
  }

  @Override
  public Socket createSocket(String s, int port) throws IOException {
    AFUNIXSocket afunixSocket = AFUNIXSocket.newInstance();
    afunixSocket.connect(new AFUNIXSocketAddress(new File(s), port));
    return afunixSocket;
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
