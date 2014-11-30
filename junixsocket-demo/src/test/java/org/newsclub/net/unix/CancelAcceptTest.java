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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.junit.Test;

/**
 * Tests breaking out of accept.
 * 
 * @see <a href="http://code.google.com/p/junixsocket/issues/detail?id=6">Issue 6</a>
 */
public class CancelAcceptTest extends SocketTestBase {
  private boolean serverSocketClosed = false;

  public CancelAcceptTest() throws IOException {
    super();
  }

  @Test
  public void issue6test1() throws Exception {
    serverSocketClosed = false;

    final ServerThread st = new ServerThread() {

      @Override
      protected void handleConnection(final Socket sock) throws IOException {
      }

      @Override
      protected void onServerSocketClose() {
        serverSocketClosed = true;
      }
    };

    try (AFUNIXSocket sock = connectToServer()) {
      // open and close
    }
    try (AFUNIXSocket sock = connectToServer()) {
      // open and close
    }

    @SuppressWarnings("resource")
    final ServerSocket servSock = st.getServerSocket();

    assertFalse("ServerSocket should not be closed now", serverSocketClosed && !servSock.isClosed());
    servSock.close();
    try {
      try (AFUNIXSocket sock = connectToServer()) {
        // open and close
      }
      fail("Did not throw SocketException");
    } catch (SocketException e) {
      // as expected
    }
    assertTrue("ServerSocket should be closed now", serverSocketClosed || servSock.isClosed());

    try {
      try (AFUNIXSocket sock = connectToServer()) {
        fail("ServerSocket should have been closed already");
      }
      fail("Did not throw SocketException");
    } catch (SocketException e) {
      // as expected
    }
  }
}
