/**
 * junixsocket
 *
 * Copyright 2009-2018 Christian Kohlschütter
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
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpNoDelayTest extends SocketTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(TcpNoDelayTest.class);

  public TcpNoDelayTest() throws IOException {
    super();
  }

  @Test
  public void testStrictImpl() throws Exception {
    new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
        stopAcceptingConnections();
      }

      @Override
      protected void onServerSocketClose() {
      }
    };

    try (AFUNIXSocket sock = connectToServer(AFUNIXSocket.newStrictInstance())) {
      boolean gotException = false;
      try {
        sock.setTcpNoDelay(true);
      } catch (SocketException e) {
        // Got expected exception
        gotException = true;
      }
      if (!gotException) {
        LOG.info("Did not expected SocketException (but that's implementation-specific)");
        assertTrue(sock.getTcpNoDelay());
      }
    }

    Files.delete(getSocketFile().toPath());
  }

  @Test
  public void testDefaultImpl() throws Exception {
    new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
        stopAcceptingConnections();
      }

      @Override
      protected void onServerSocketClose() {
      }
    };

    try (AFUNIXSocket sock = connectToServer()) {
      sock.setTcpNoDelay(true);
      // No exception
    }

    Files.delete(getSocketFile().toPath());
  }
}
