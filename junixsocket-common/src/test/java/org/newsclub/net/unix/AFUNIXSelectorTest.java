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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class AFUNIXSelectorTest {
  private final AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();

  private static void assertChangeToNonBlocking(AFUNIXServerSocketChannel as) throws IOException {
    assertTrue(as.isBlocking());
    as.configureBlocking(false);
    assertFalse(as.isBlocking());
  }

  private static void assertSelect(int expected, Selector sel, boolean block) throws IOException {
    assertEquals(expected, sel.selectNow());
    if (block) {
      assertEquals(expected, sel.select(1));
      assertEquals(expected, sel.select());
    }
    assertEquals(expected, sel.selectNow());
    assertEquals(expected, sel.selectedKeys().size());
  }

  @Test
  public void testNonBlockingAccept() throws IOException, InterruptedException {
    assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
      AFUNIXSocketAddress sa = AFUNIXSocketAddress.of(SocketTestBase.newTempFile());

      Selector sscSel = provider.openSelector();
      assertTrue(sscSel.selectedKeys().isEmpty());

      try (AFUNIXServerSocketChannel ssc = provider.openServerSocketChannel(sa)) {
        assertChangeToNonBlocking(ssc);

        SelectionKey key = ssc.register(sscSel, SelectionKey.OP_ACCEPT);

        assertEquals(Collections.singleton(key), sscSel.keys());
        assertTrue(sscSel.selectedKeys().isEmpty());

        assertNull(ssc.accept());
        assertEquals(0, sscSel.selectNow());

        try (AFUNIXSocketChannel sc = provider.openSocketChannel(sa)) {
          assertSelect(1, sscSel, true);
          assertEquals(Collections.singleton(key), sscSel.selectedKeys());

          AFUNIXSocketChannel sscsc = ssc.accept();
          assertNotNull(sscsc);

          assertNull(ssc.accept());
        }
      }

      assertSelect(0, sscSel, false);
    });
  }

  @Test
  public void testCancelSelect() throws Exception {
    Selector selector = provider.openSelector();
    CompletableFuture<Integer> cf = new CompletableFuture<>();

    new Thread() {
      @Override
      public void run() {
        int num;
        try {
          num = selector.select();
        } catch (IOException e) {
          cf.completeExceptionally(e);
          return;
        }
        cf.complete(num);
      }
    }.start();

    selector.wakeup();
    assertEquals(0, cf.get(5, TimeUnit.SECONDS));
  }
}
