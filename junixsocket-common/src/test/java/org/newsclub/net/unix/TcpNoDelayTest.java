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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

public class TcpNoDelayTest extends SocketTestBase {
  @Test
  public void testStrictImpl() throws Exception {
    try (ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final AFUNIXSocket sock) throws IOException {
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

      try (@SuppressWarnings("resource")
      AFUNIXSocket sock = connectToServer(AFUNIXSocket.newStrictInstance())) {
        boolean gotException = false;
        try {
          sock.setTcpNoDelay(true);
        } catch (SocketException e) {
          // Got expected exception
          gotException = true;
        }
        if (!gotException) {
          // Did not expected SocketException (but that's implementation-specific)
          assertTrue(sock.getTcpNoDelay());
        }
      }
    } finally {
      Files.deleteIfExists(getSocketFile().toPath());
    }
  }

  @Test
  public void testDefaultImpl() throws Exception {
    try (ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final AFUNIXSocket sock) throws IOException {
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

      try (AFUNIXSocket sock = connectToServer()) {
        sock.setTcpNoDelay(true);
        // No exception
      }

    } finally {
      Files.deleteIfExists(getSocketFile().toPath());
    }
  }
}
