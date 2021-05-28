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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketException;

import org.junit.jupiter.api.Test;

public class AFUNIXSocketChannelTest {
  private final AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();

  @Test
  public void testNonBlockingConnect() throws IOException {
    AFUNIXSocketAddress sa = AFUNIXSocketAddress.of(SocketTestBase.newTempFile());

    AFUNIXServerSocketChannel ssc = provider.openServerSocketChannel();
    ssc.configureBlocking(false);
    ssc.bind(sa, 1);

    {
      AFUNIXSocketChannel sc = provider.openSocketChannel();
      sc.configureBlocking(false);
      assertTrue(sc.connect(sa));
      assertTrue(sc.isConnected());
    }

    {
      AFUNIXSocketChannel sc = provider.openSocketChannel();
      sc.configureBlocking(false);
      assertThrows(SocketException.class, () -> sc.connect(sa)); // connection refused
      assertFalse(sc.isConnected());
    }
  }

}
