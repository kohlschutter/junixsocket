/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * This test ensures that the test cases written for the junixsocket also pass for the standard Java
 * Internet sockets. If they do not, the tests may be incorrect. As much as possible, junixsocket's
 * AFUnixSocket should emulate the regular Java Internet sockets so they can be used with all
 * existing tools.
 * 
 * See http://code.google.com/p/junixsocket/issues/detail?id=9
 * 
 * @author Derrick Rice (April, 2010)
 */
public class EndOfFileJavaTest extends EndOfFileTest {
  int port;

  @Override
  @BeforeEach
  public void setUp() throws IOException {
    final int desiredPort;
    String explicitPort = System.getProperty("org.newsclub.net.unix.testport");
    if (explicitPort != null) {
      desiredPort = Integer.parseInt(explicitPort);
    } else {
      desiredPort = 0;
    }

    server = new ServerSocket();
    server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), desiredPort), 1);
    port = server.getLocalPort();
    executor = Executors.newFixedThreadPool(2);
  }

  @Override
  @AfterEach
  public void tearDown() throws IOException {
    super.tearDown();
  }

  @SuppressWarnings("resource")
  @Override
  Socket[] connectToServer() throws Exception {
    Socket clientSocket = new Socket();

    Future<Socket> serverAcceptFuture = executor.submit(new Callable<Socket>() {
      @Override
      public Socket call() throws Exception {
        return server.accept();
      }
    });

    clientSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));

    Socket serverSocket = serverAcceptFuture.get(100, TimeUnit.MILLISECONDS);

    return new Socket[] {serverSocket, clientSocket};
  }
}
