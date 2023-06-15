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
package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * See http://code.google.com/p/junixsocket/issues/detail?id=9
 *
 * @author Derrick Rice (April, 2010)
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class EndOfFileTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected ServerSocket server;
  protected ExecutorService executor;

  protected EndOfFileTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @BeforeEach
  public void setUp() throws IOException {
    server = newServerSocket();
    bindServerSocket(server, getServerBindAddress());

    executor = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  public void tearDown() throws IOException {
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

  protected Socket[] connect() throws Exception {
    Socket clientSocket = newSocket();

    Future<Socket> serverAcceptFuture = executor.submit(new Callable<Socket>() {
      @Override
      public Socket call() throws Exception {
        return server.accept();
      }
    });

    Thread.sleep(100);

    connectSocket(clientSocket, server.getLocalSocketAddress());

    Socket serverSocket = serverAcceptFuture.get(5, TimeUnit.SECONDS);

    return new Socket[] {serverSocket, clientSocket};
  }

  @Test
  public void bidirectionalSanity() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      Socket[] sockets = connect();
      try (Socket serverSocket = sockets[0]; //
          Socket clientSocket = sockets[1]) {
        String input;
        String output;

        OutputStreamWriter clientOutWriter = new OutputStreamWriter(clientSocket.getOutputStream(),
            "UTF-8");
        BufferedReader serverInReader = new BufferedReader(new InputStreamReader(serverSocket
            .getInputStream(), "UTF-8"));
        input = "TestStringOne";
        clientOutWriter.write(input + "\n");
        clientOutWriter.flush();
        output = serverInReader.readLine();

        assertEquals(input, output, "Server output should match client input.");

        OutputStreamWriter serverOutWriter = new OutputStreamWriter(serverSocket.getOutputStream(),
            "UTF-8");
        BufferedReader clientInReader = new BufferedReader(new InputStreamReader(clientSocket
            .getInputStream(), "UTF-8"));
        input = "TestStringTwo";
        serverOutWriter.write(input + "\n");
        serverOutWriter.flush();
        output = clientInReader.readLine();

        assertEquals(input, output, "Client output should match server input.");
      }
    });
  }

  @SuppressWarnings("resource")
  @Test
  public void serverReadEof() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      Socket[] sockets = connect();
      Socket serverSocket = sockets[0];
      Socket clientSocket = sockets[1];
      clientSocket.close();
      // wait for close to propagate
      Thread.sleep(100);

      int read = serverSocket.getInputStream().read();

      assertEquals(-1, read, "Server should see EOF indicated by -1 from read()");

      read = serverSocket.getInputStream().read(new byte[3]);

      assertEquals(-1, read, "Server should continue to see EOF indicated by -1 from read(...)");

      try {
        serverSocket.close();
      } catch (IOException ignore) {
        // ignore
      }
    });
  }

  @Test
  @SuppressWarnings("resource")
  public void clientReadEof() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {

      Socket[] sockets = connect();
      Socket serverSocket = sockets[0];
      Socket clientSocket = sockets[1];

      serverSocket.close();
      // wait for close to propagate
      Thread.sleep(100);

      int read = clientSocket.getInputStream().read();

      assertEquals(-1, read, "Client should see EOF indicated by -1 from read()");

      read = clientSocket.getInputStream().read(new byte[3]);

      assertEquals(-1, read, "Client should continue to see EOF indicated by -1 from read(...)");

      try {
        clientSocket.close();
      } catch (IOException ignore) {
        // ignore
      }
    });
  }

  @Test
  @SuppressWarnings("resource")
  public void serverWriteToSocketClosedByServer() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      Socket[] sockets = connect();
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

      assertNotNull(exception,
          "Server should see an IOException when writing to a socket that it closed.");

      try {
        clientSocket.close();
      } catch (IOException ignore) {
        // ignore
      }
    });
  }

  @Test
  @SuppressWarnings("resource")
  public void serverWriteToSocketClosedByClient() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      Socket[] sockets = connect();
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
         * The TCP RFC allows the open side to continue sending data. In most (all?)
         * implementations, the closed side will respond with a RST. For this reason, it takes two
         * writes to cause an IOException with TCP sockets. (or more, if there is latency)
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

      assertNotNull(exception,
          "Server should see an IOException when writing to a socket that the client closed.");

      try {
        serverSocket.close();
      } catch (IOException ignore) {
        // ignore
      }
    });
  }

  @Test
  @SuppressWarnings("resource")
  public void clientWriteToSocketClosedByClient() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      Socket[] sockets = connect();
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

      assertNotNull(exception,
          "Client should see an IOException when writing to a socket which it closed.");

      try {
        serverSocket.close();
      } catch (IOException ignore) {
        // ignore
      }
    });
  }

  @Test
  @SuppressWarnings("resource")
  public void clientWriteToSocketClosedByServer() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      Socket[] sockets = connect();
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

      assertNotNull(exception,
          "Client should see an IOException when writing to a socket that the server closed.");

      try {
        clientSocket.close();
      } catch (IOException ignore) {
        // ignore
      }
    });
  }
}
