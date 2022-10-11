/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.Test;

public abstract class SocketChannelTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected SocketChannelTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testNonBlockingConnect() throws IOException {
    SocketAddress sa = newTempAddress();

    ServerSocketChannel ssc = selectorProvider().openServerSocketChannel();
    ssc.configureBlocking(false);
    ssc.bind(sa, 1);
    sa = ssc.getLocalAddress();

    {
      SocketChannel sc = selectorProvider().openSocketChannel();
      sc.configureBlocking(false);
      if (!sc.connect(sa)) {
        // connect pending
        assertTrue(sc.isConnected() || sc.isConnectionPending());
        long now = System.currentTimeMillis();
        do {
          if (sc.finishConnect()) {
            break;
          }
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            break;
          }
          if ((System.currentTimeMillis() - now) > 1000) {
            fail("Non-blocking connect not connected after 1s");
            break;
          }
        } while (!Thread.interrupted());
        assertTrue(sc.finishConnect());
      }
      assertTrue(sc.isConnected());
      assertFalse(sc.isConnectionPending());
    }
  }

}
