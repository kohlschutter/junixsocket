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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class TcpNoDelayTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected TcpNoDelayTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testStrictImpl() throws Exception {
    try (ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
      }

      @Override
      protected ExceptionHandlingDecision handleException(Exception e) {
        if (e instanceof SocketException) {
          return ExceptionHandlingDecision.IGNORE;
        } else {
          return super.handleException(e);
        }
      }

      @Override
      protected void onServerSocketClose() {
      }
    }) {

      try (Socket sock = newStrictSocket()) {
        try {
          connectSocket(sock, serverThread.getServerAddress());
        } catch (SocketTimeoutException e) {
          // report and try again
          e.printStackTrace();
          connectSocket(sock, serverThread.getServerAddress());
        }
        boolean gotException = false;
        try {
          sock.setTcpNoDelay(true);
        } catch (SocketException e) {
          // Got expected exception
          gotException = true;
        }
        if (!gotException) {
          // Did not get expected SocketException (but that's implementation-specific)
          sock.getTcpNoDelay(); // not guaranteed to be set to true
          // assertTrue(sock.getTcpNoDelay());
        }
      }
    }
  }

  @Test
  public void testDefaultImpl() throws Exception {
    try (ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
      }

      @Override
      protected ExceptionHandlingDecision handleException(Exception e) {
        if (e instanceof SocketException) {
          return ExceptionHandlingDecision.IGNORE;
        } else {
          return super.handleException(e);
        }
      }

      @Override
      protected void onServerSocketClose() {
      }
    }) {

      try (Socket sock = connectTo(serverThread.getServerAddress())) {
        sock.setTcpNoDelay(true);
        // No exception
      }
    }
  }
}
