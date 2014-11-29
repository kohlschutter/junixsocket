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
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
 * See http://code.google.com/p/junixsocket/issues/detail?id=9
 * 
 * @author Derrick Rice (April, 2010)
 */
public class EndOfFileTest {
  File socketFile;
  ServerSocket server;
  ExecutorService executor;

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
    } catch (IOException ignore) {
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
  public void bidirectionalSanity() throws Exception {
    Socket[] sockets = connectToServer();
    try (Socket serverSocket = sockets[0]; //
        Socket clientSocket = sockets[1]) {
      String input = null;
      String output = null;

      OutputStreamWriter clientOutWriter = new OutputStreamWriter(clientSocket.getOutputStream());
      BufferedReader serverInReader =
          new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
      input = "TestStringOne";
      clientOutWriter.write(input + "\n");
      clientOutWriter.flush();
      output = serverInReader.readLine();

      assertEquals("Server output should match client input.", input, output);

      OutputStreamWriter serverOutWriter = new OutputStreamWriter(serverSocket.getOutputStream());
      BufferedReader clientInReader =
          new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      input = "TestStringTwo";
      serverOutWriter.write(input + "\n");
      serverOutWriter.flush();
      output = clientInReader.readLine();

      assertEquals("Client output should match server input.", input, output);
    }
  }

  @SuppressWarnings("resource")
  @Test(timeout = 2000)
  public void serverReadEof() throws Exception {
    Socket[] sockets = connectToServer();
    Socket serverSocket = sockets[0];
    Socket clientSocket = sockets[1];
    clientSocket.close();
    // wait for close to propagate
    Thread.sleep(100);

    int read = serverSocket.getInputStream().read();

    assertEquals("Server should see EOF indicated by -1 from read()", -1, read);

    read = serverSocket.getInputStream().read(new byte[3]);

    assertEquals("Server should continue to see EOF indicated by -1 from read(...)", -1, read);

    try {
      serverSocket.close();
    } catch (IOException ignore) {
      // ignore
    }
  }

  @Test(timeout = 2000)
  @SuppressWarnings("resource")
  public void clientReadEof() throws Exception {
    Socket[] sockets = connectToServer();
    Socket serverSocket = sockets[0];
    Socket clientSocket = sockets[1];

    serverSocket.close();
    // wait for close to propagate
    Thread.sleep(100);

    int read = clientSocket.getInputStream().read();

    assertEquals("Client should see EOF indicated by -1 from read()", -1, read);

    read = clientSocket.getInputStream().read(new byte[3]);

    assertEquals("Client should continue to see EOF indicated by -1 from read(...)", -1, read);

    try {
      clientSocket.close();
    } catch (IOException ignore) {
      // ignore
    }
  }

  @Test(timeout = 2000)
  @SuppressWarnings("resource")
  public void serverWriteToSocketClosedByServer() throws Exception {
    Socket[] sockets = connectToServer();
    Socket serverSocket = sockets[0];
    Socket clientSocket = sockets[1];

    serverSocket.close();
    // wait for close to propagate
    Thread.sleep(100);

    IOException exception = null;
    try {
      serverSocket.getOutputStream().write(new byte[] {'1'});
    } catch (IOException e) {
      exception = e;
    }

    assertNotNull("Server should see an IOException when writing to a socket that it closed.",
        exception);

    try {
      clientSocket.close();
    } catch (IOException ignore) {
      // ignore
    }
  }

  @Test(timeout = 2000)
  @SuppressWarnings("resource")
  public void serverWriteToSocketClosedByClient() throws Exception {
    Socket[] sockets = connectToServer();
    Socket serverSocket = sockets[0];
    Socket clientSocket = sockets[1];

    clientSocket.close();
    // wait for close to propagate
    Thread.sleep(100);

    IOException exception = null;
    try {
      /*
       * http://www.unixguide.net/network/socketfaq/2.1.shtml http://www.faqs.org/rfcs/rfc793.html
       * 
       * The TCP RFC allows the open side to continue sending data. In most (all?) implementations,
       * the closed side will respond with a RST. For this reason, it takes two writes to cause an
       * IOException with TCP sockets. (or more, if there is latency)
       * 
       * However, it is expected that the write give an IOException as soon as possible - which
       * means it is OK for our socket implementation to give an IOException on the first write.
       */
      serverSocket.getOutputStream().write(new byte[] {'1'});
      Thread.sleep(100);
      serverSocket.getOutputStream().write(new byte[] {'2'});
    } catch (IOException e) {
      exception = e;
    }

    assertNotNull(
        "Server should see an IOException when writing to a socket that the client closed.",
        exception);

    try {
      serverSocket.close();
    } catch (IOException ignore) {
      // ignore
    }
  }

  @Test(timeout = 2000)
  @SuppressWarnings("resource")
  public void clientWriteToSocketClosedByClient() throws Exception {
    Socket[] sockets = connectToServer();
    Socket serverSocket = sockets[0];
    Socket clientSocket = sockets[1];

    clientSocket.close();
    // wait for close to propagate
    Thread.sleep(100);

    IOException exception = null;
    try {
      clientSocket.getOutputStream().write(new byte[] {'1'});
    } catch (IOException e) {
      exception = e;
    }

    assertNotNull("Client should see an IOException when writing to a socket which it closed.",
        exception);

    try {
      serverSocket.close();
    } catch (IOException ignore) {
      // ignore
    }
  }

  @Test(timeout = 2000)
  @SuppressWarnings("resource")
  public void clientWriteToSocketClosedByServer() throws Exception {
    Socket[] sockets = connectToServer();
    Socket serverSocket = sockets[0];
    Socket clientSocket = sockets[1];

    serverSocket.close();
    // wait for close to propagate
    Thread.sleep(100);

    IOException exception = null;
    try {
      // see comments in serverWriteToSocketClosedByClient()
      serverSocket.getOutputStream().write(new byte[] {'1'});
      Thread.sleep(100);
      serverSocket.getOutputStream().write(new byte[] {'2'});
    } catch (IOException e) {
      exception = e;
    }

    assertNotNull(
        "Client should see an IOException when writing to a socket that the server closed.",
        exception);

    try {
      clientSocket.close();
    } catch (IOException ignore) {
      // ignore
    }
  }
}
