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
package org.newsclub.net.unix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * See https://code.google.com/p/junixsocket/issues/detail?id=20
 */
public class BufferOverflowTest {
  private File socketFile;
  private ServerSocket server;
  private ExecutorService executor;

  @Before
  public void setup() throws IOException {
    String explicitFile = System.getProperty("org.newsclub.net.unix.testsocket");
    if (explicitFile != null) {
      socketFile = new File(explicitFile);
    } else {
      socketFile =
          new File(new File(System.getProperty("java.io.tmpdir")), "junixsocket-test.sock");
    }

    server = AFUNIXServerSocket.newInstance();
    server.bind(new AFUNIXSocketAddress(socketFile));

    executor = Executors.newFixedThreadPool(2);
  }

  @After
  public void teardown() {
    try {
      if (server != null) {
        server.close();
      }
    } catch (IOException e) {
      // ignore
    }

    if (executor != null) {
      executor.shutdown();
    }
  }

  @SuppressWarnings("resource")
  Socket[] connectToServer() throws Exception {
    AFUNIXSocket clientSocket = AFUNIXSocket.newInstance();

    Future<Socket> serverAcceptFuture = executor.submit(new Callable<Socket>() {
      @Override
      public Socket call() throws Exception {
        return server.accept();
      }
    });

    Thread.sleep(100);

    clientSocket.connect(new AFUNIXSocketAddress(socketFile));

    Socket serverSocket = serverAcceptFuture.get(100, TimeUnit.MILLISECONDS);

    return new Socket[] {serverSocket, clientSocket};
  }

  @Test(timeout = 2000)
  public void readOverflow() throws Exception {
    Socket[] sockets = connectToServer();
    try (Socket serverSocket = sockets[0];//
        Socket clientSocket = sockets[1];) {

      byte[] input = new byte[16];
      byte[] output = new byte[15];

      try (OutputStream clientOutStream = clientSocket.getOutputStream(); //
          InputStream serverInStream = serverSocket.getInputStream()) {
        clientOutStream.write(input, 0, 16);
        int numRead = serverInStream.read(output, 0, 16);
        assertTrue("Buffer Overflow not detected", numRead <= 15);
      }
    }
  }

  @Test(timeout = 2000)
  public void writeOverflow() throws Exception {
    Socket[] sockets = connectToServer();
    try (Socket serverSocket = sockets[0]; //
        Socket clientSocket = sockets[1];) {

      byte[] input = new byte[15];
      byte[] output = new byte[16];

      try (OutputStream clientOutStream = clientSocket.getOutputStream();//
          InputStream serverInStream = serverSocket.getInputStream()) {
        try {
          clientOutStream.write(input, 0, 16);
          int numRead = serverInStream.read(output, 0, 15);
          assertEquals(15, numRead);
          serverInStream.read();
          fail("The call to read should have thrown an IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException ex) {
          // expected
        }
      }
    }
  }
}
