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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class SelectorTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected SelectorTest(AddressSpecifics<A> asp) throws IOException {
    super(asp);
  }

  private static void assertChangeToNonBlocking(ServerSocketChannel as) throws IOException {
    assertTrue(as.isBlocking());
    as.configureBlocking(false);
    assertFalse(as.isBlocking());
  }

  private static void assertSelect(int expected, Selector sel, boolean block) throws IOException {
    int now = sel.selectNow();
    if (now != 0) {
      assertEquals(expected, now);
    }

    if (block) {
      assertEquals(expected, now = Math.max(now, sel.select()));
    }
    assertEquals(expected, Math.max(now, sel.selectNow()));
    assertEquals(expected, sel.selectedKeys().size());
  }

  @Test
  public void testNonBlockingAccept() throws IOException, InterruptedException {
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      Selector sscSel = selectorProvider().openSelector();
      assertTrue(sscSel.selectedKeys().isEmpty());

      try (ServerSocketChannel ssc = selectorProvider().openServerSocketChannel()) {
        bindServerSocket(ssc, newTempAddress());
        assertChangeToNonBlocking(ssc);

        SelectionKey key = ssc.register(sscSel, SelectionKey.OP_ACCEPT);

        assertEquals(Collections.singleton(key), sscSel.keys());
        assertTrue(sscSel.selectedKeys().isEmpty());

        assertNull(ssc.accept());
        assertEquals(0, sscSel.selectNow());

        try (SocketChannel sc = selectorProvider().openSocketChannel()) {
          CompletableFuture<Boolean> future = new CompletableFuture<>();
          new Thread(() -> {
            try {
              // connect from a different thread
              // TIPC doesn't like being called from the same thread
              future.complete(connectSocket(sc, ssc.getLocalAddress()));
            } catch (RuntimeException | IOException e) {
              future.completeExceptionally(e);
            }
          }).start();

          assertSelect(1, sscSel, true);
          assertEquals(Collections.singleton(key), sscSel.selectedKeys());

          SocketChannel sscsc = ssc.accept();
          assertNotNull(sscsc);

          assertTrue(future.get());

          assertNull(ssc.accept());
        }
      }

      assertSelect(0, sscSel, false);
    });
  }

  @Test
  public void testCancelSelect() throws Exception {
    Selector selector = selectorProvider().openSelector();
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
    try {
      assertEquals(0, cf.get(5, TimeUnit.SECONDS));
    } catch (ExecutionException e) {
      if ((e.getCause() instanceof SocketException) && e.getCause().getMessage().contains(
          "closed")) {
        // OK
      } else {
        throw e;
      }
    }
  }

  private Future<Integer> newHelloClient(SocketAddress serverAddr, Semaphore sema) {
    return Executors.newFixedThreadPool(1).submit(() -> {
      try (Socket sock = connectTo(serverAddr); //
          OutputStream out = sock.getOutputStream()) {
        out.write("Hello".getBytes(StandardCharsets.UTF_8));
        out.flush();

        if (sema != null) {
          sema.tryAcquire(1, 10000, TimeUnit.MILLISECONDS);
        }
      }
      return 0;
    });
  }

  /**
   * Tests a non-blocking server setup where a client connects, sends "Hello" to the server and then
   * disconnects after the server has received the message.
   *
   * In the selection loop, after the client has disconnected, the server should see that the
   * corresponding {@link SelectionKey} is "readable" (even if that means the connection was
   * closed).
   *
   * @throws Exception on error.
   */
  @Test
  public void testConnectionCloseEventualClientDisconnect() throws Exception {
    testConnectionClose(false, false);
  }

  /**
   * Tests a non-blocking server setup where a client connects, sends "Hello" to the server and then
   * immediately disconnects.
   *
   * Unlike {@link #testConnectionCloseEventualClientDisconnect()}, this may uncover additional
   * faults, such as an unexpected exception when trying to read the socket name.
   *
   * @throws Exception on error.
   */
  @Test
  public void testConnectionCloseImmediateClientDisconnect() throws Exception {
    testConnectionClose(true, false);
  }

  /**
   * Tests a non-blocking server setup where a client connects, sends "Hello" to the server and then
   * disconnects after the server has received the message.
   *
   * In the selection loop, after the client has disconnected, the server should see that the
   * corresponding {@link SelectionKey} is "readable" (even if that means the connection was
   * closed).
   *
   * @throws Exception on error.
   */
  @Test
  public void testConnectionCloseEventualClientDisconnectKeepLooping() throws Exception {
    testConnectionClose(false, true);
  }

  /**
   * Tests a non-blocking server setup where a client connects, sends "Hello" to the server and then
   * immediately disconnects.
   *
   * Unlike {@link #testConnectionCloseEventualClientDisconnect()}, this may uncover additional
   * faults, such as an unexpected exception when trying to read the socket name.
   *
   * @throws Exception on error.
   */
  @Test
  public void testConnectionCloseImmediateClientDisconnectKeepLooping() throws Exception {
    testConnectionClose(true, true);
  }

  @SuppressWarnings({
      "PMD.CognitiveComplexity", "PMD.NcssCount", "PMD.CyclomaticComplexity",
      "PMD.ExcessiveMethodLength", "PMD.NPathComplexity"})
  private void testConnectionClose(boolean clientCloseImmediately, boolean checkInvalid)
      throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(512);

    SelectorProvider provider = selectorProvider();
    try (ServerSocketChannel server = provider.openServerSocketChannel()) {
      bindServerSocket(server, newTempAddress());
      server.configureBlocking(false);

      Selector selector = provider.openSelector();
      server.register(selector, SelectionKey.OP_ACCEPT);

      final Semaphore mayCloseSema = new Semaphore(0);
      final Future<Integer> threadFuture = newHelloClient(server.getLocalAddress(),
          clientCloseImmediately ? null : mayCloseSema);

      int numAcceptable = 0;
      int numReadable = 0;
      int numClosedChannelException = 0;

      long timeout = 5000;
      selectLoop : while (timeout > 0) {
        long time = System.currentTimeMillis();
        while (selector.select(timeout) != 0) {
          for (SelectionKey key : selector.selectedKeys()) {
            if (numAcceptable > 3 || numReadable > 2) {
              break selectLoop;
            }
            if (checkInvalid && !key.isValid()) {
              key.cancel();
              timeout = 10;
              continue;
            }
            if (key.isAcceptable()) {
              SocketChannel channel = server.accept();
              if (channel == null) {
                continue;
              }
              numAcceptable++;
              channel.configureBlocking(false);
              channel.register(selector, SelectionKey.OP_READ);
              if (!clientCloseImmediately) {
                assertNotNull(channel.getLocalAddress());
              } else {
                channel.getLocalAddress(); // just trigger
              }
              channel.getRemoteAddress(); // just trigger
            }
            if (key.isReadable()) {
              numReadable++;
              SocketChannel channel = (SocketChannel) key.channel();
              try {
                buffer.clear();
                int numRead = channel.read(buffer);
                switch (numReadable) {
                  case 1:
                    assertEquals("Hello", new String(buffer.array(), 0, numRead,
                        StandardCharsets.UTF_8));
                    break;
                  case 2:
                    fail("Should have thrown ClosedChannelException");
                    break;
                  default:
                    fail("Should not have been reached");
                    break;
                }
                break selectLoop;
              } catch (ClosedChannelException e) {
                // channel.close(); // not strictly necessary -- will be closed via Cleaner
                numClosedChannelException++;
                if (!checkInvalid) {
                  key.cancel();
                  if (numReadable >= 2) {
                    break selectLoop;
                  }
                }
              }
            }
          }
        }
        timeout -= (System.currentTimeMillis() - time);
      }

      mayCloseSema.release();

      if (numAcceptable == 0 && threadFuture.isDone()) {
        try {
          threadFuture.get();
        } catch (ExecutionException e) {
          Throwable t = e.getCause();
          if (t instanceof RuntimeException) {
            throw (RuntimeException) t; // NOPMD.PreserveStackTrace
          }
        }
      }
      assertEquals(1, numAcceptable);

      if (!checkInvalid) {
        // invalid
      } else {
        if (numClosedChannelException == 0) { // either the client closed and data didn't make it
                                              // through, or the second select correctly said
                                              // nothing was readable
          if (clientCloseImmediately) {
            if (numReadable != 0) {
              assertEquals(1, numReadable);
            }
          }
        } else {
          assertEquals(2, numReadable);
          assertEquals(1, numClosedChannelException);
        }
      }

      threadFuture.get(1, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testClosedSelectorSelect() throws Exception {
    assertThrows(ClosedSelectorException.class, () -> {
      @SuppressWarnings("resource")
      Selector sel = selectorProvider().openSelector();
      sel.close();
      sel.select();
    });
  }

  @Test
  public void testClosedSelectorWakeup() throws Exception {
    @SuppressWarnings("resource")
    Selector sel = selectorProvider().openSelector();
    sel.close();
    assertEquals(sel, sel.wakeup());
  }
}
