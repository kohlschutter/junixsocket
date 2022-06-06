/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Tests breaking out of accept.
 * 
 * @see <a href="http://code.google.com/p/junixsocket/issues/detail?id=6">Issue 6</a>
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class CancelAcceptTest<A extends SocketAddress> extends SocketTestBase<A> {
  private boolean serverSocketClosed = false;

  protected CancelAcceptTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void issue6test1() throws Exception {
    serverSocketClosed = false;

    AtomicBoolean ignoreServerSocketClosedException = new AtomicBoolean(false);
    try (ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
      }

      @Override
      protected void onServerSocketClose() {
        serverSocketClosed = true;
      }

      @Override
      protected ExceptionHandlingDecision handleException(Exception e) {
        if (ignoreServerSocketClosedException.get() && e instanceof SocketException) {
          ServerSocket serverSocket = getServerSocket();
          if (serverSocket != null && serverSocket.isClosed()) {
            return ExceptionHandlingDecision.IGNORE;
          }
        }
        return ExceptionHandlingDecision.RAISE;
      }
    }) {

      try (Socket sock = connectTo(serverThread.getServerAddress())) {
        // open and close
      }
      try (Socket sock = connectTo(serverThread.getServerAddress())) {
        // open and close
      }

      @SuppressWarnings("resource")
      final ServerSocket serverSocket = serverThread.getServerSocket();

      assertFalse(serverSocketClosed && !serverSocket.isClosed(),
          "ServerSocket should not be closed now");

      // serverSocket.close() may throw a "Socket is closed" exception in the server thread
      // so let's make sure we ignore that error when the auto-closing ServerThread
      ignoreServerSocketClosedException.set(true);
      serverSocket.close();
      try {
        try (Socket sock = connectTo(serverThread.getServerAddress())) {
          // open and close
        }
        fail("Did not throw SocketException");
      } catch (SocketException e) {
        // as expected
      }

      assertTrue(serverSocketClosed || serverSocket.isClosed(),
          "ServerSocket should be closed now");

      try {
        try (Socket sock = connectTo(serverThread.getServerAddress())) {
          fail("ServerSocket should have been closed already");
        }
        fail("Did not throw SocketException");
      } catch (SocketException e) {
        // as expected
      }
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }
}
