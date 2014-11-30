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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.junit.Test;

public class AvailableTest extends SocketTestBase {
  private final int bytesSent = 23;
  private final int timeToSleep = 100;

  public AvailableTest() throws IOException {
    super();
  }

  private void receiveBytes(final Socket sock, final int expected) throws IOException {
    @SuppressWarnings("resource")
    final InputStream in = sock.getInputStream();

    int toExpect = expected;

    char firstChar = 'A';

    int available = in.available();
    if (available == 0 && expected != 0) {
      // this may happen, and it's ok.
      final int r = in.read();
      assertEquals(
          "Available returned 0, so we tried to read the first byte (which should be 65=='A')",
          'A', r);

      // as we have already read one byte, we now expect one byte less
      toExpect--;

      available = in.available();

      firstChar = 'B';
    }
    assertEquals(toExpect, available);
    final byte[] buf = new byte[expected];
    final int numRead = in.read(buf);
    assertEquals(toExpect, numRead);

    for (int i = 0; i < numRead; i++) {
      assertEquals(firstChar + i, buf[i] & 0xFF);
    }

    assertEquals(0, in.available());
  }

  private void sendBytes(final Socket sock) throws IOException {
    @SuppressWarnings("resource")
    final OutputStream out = sock.getOutputStream();
    final byte[] buf = new byte[bytesSent];
    for (int i = 0; i < bytesSent; i++) {
      buf[i] = (byte) (i + 'A');
    }
    out.write(buf);
    out.flush();
  }

  /**
   * Tests if {@link InputStream#available()} works as expected. The server sends 23 bytes. The
   * client waits for 100ms. After that, the client should be able to read exactly 23 bytes without
   * blocking. Then, we try the opposite direction.
   */
  @Test(timeout = 2000)
  public void testAvailableAtClient() throws Exception {

    final ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
        sendBytes(sock);
        sleepFor(timeToSleep);
        receiveBytes(sock, bytesSent);

        stopAcceptingConnections();
      }
    };

    try (AFUNIXSocket sock = connectToServer()) {
      sleepFor(timeToSleep);
      receiveBytes(sock, bytesSent);
      sendBytes(sock);
    }

    serverThread.getServerSocket().close();
    serverThread.checkException();
  }

  /**
   * Tests if {@link InputStream#available()} works as expected. The client sends 23 bytes. The
   * server waits for 100ms. After that, the server should be able to read exactly 23 bytes without
   * blocking. Then, we try the opposite direction.
   */
  @Test(timeout = 2000)
  public void testAvailableAtServer() throws Exception {

    final ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
        sleepFor(timeToSleep);
        receiveBytes(sock, bytesSent);
        sendBytes(sock);

        stopAcceptingConnections();
      }
    };

    try (AFUNIXSocket sock = connectToServer()) {
      sendBytes(sock);
      sleepFor(timeToSleep);

      receiveBytes(sock, bytesSent);
    }

    serverThread.getServerSocket().close();
    serverThread.checkException();
  }
}
