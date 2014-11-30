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

import java.io.IOException;
import java.net.Socket;

import org.junit.Test;

/**
 * Tests {@link Socket#setSoTimeout(int)} behavior.
 * 
 * @see <a href="http://code.google.com/p/junixsocket/issues/detail?id=14">Issue 14</a>
 */
public class SoTimeoutTest extends SocketTestBase {
  public SoTimeoutTest() throws IOException {
    super();
  }

  /**
   * Triggers a case where {@link Socket#setSoTimeout(int)} fails on some platforms: when the socket
   * is closed.
   */
  @Test
  public void issue14Fail() throws Exception {
    final ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
        stopAcceptingConnections();
      }
    };

    try (AFUNIXSocket sock = connectToServer()) {
      // Sometimes this test would pass, so let's sleep for a moment
      Thread.yield();

      try {
        sock.setSoTimeout(12000);
        System.err
            .println("NOTE: Socket#setSoTimeout(int) did not throw an AFUNIXSocketException. This is OK.");
      } catch (final AFUNIXSocketException e) {
        // expected, as the socket is actually closed
      }
    }
    serverThread.getServerSocket().close();
  }

  /**
   * Triggers a regular case where {@link Socket#setSoTimeout(int)} should work.
   */
  @Test
  public void issue14Pass() throws Exception {
    final ServerThread serverThread = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
        // Let's wait some time for a byte that never gets sent by the
        // client
        sock.getInputStream().read();

        stopAcceptingConnections();
      }
    };

    try (AFUNIXSocket sock = connectToServer()) {
      sock.setSoTimeout(12000);
    }
    serverThread.getServerSocket().close();
  }
}
