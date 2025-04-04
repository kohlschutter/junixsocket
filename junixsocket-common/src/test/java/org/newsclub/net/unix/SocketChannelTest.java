/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;
import com.kohlschutter.testutil.TestAsyncUtil;

@SuppressFBWarnings("PATH_TRAVERSAL_IN")
public abstract class SocketChannelTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected SocketChannelTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testNonBlockingConnect() throws IOException {
    SocketAddress sa = newTempAddress();

    ServerSocketChannel ssc = selectorProvider().openServerSocketChannel();
    ssc.configureBlocking(false);
    bindServerSocket(ssc, sa, 1);
    sa = ssc.getLocalAddress();

    {
      SocketChannel sc = selectorProvider().openSocketChannel();
      sc.configureBlocking(false);
      if (!handleConnect(sc, sa)) {
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

  @Test
  public void testDoubleBindAddressNotReusable() throws Exception {
    testDoubleBind(false);
  }

  @Test
  public void testDoubleBindAddressReusable() throws Exception {
    testDoubleBind(true);
  }

  @SuppressWarnings({
      "PMD.ExcessiveMethodLength", "PMD.CognitiveComplexity", "PMD.NPathComplexity",
      "PMD.CyclomaticComplexity"})
  private void testDoubleBind(boolean reuseAddress) throws Exception {
    SocketAddress sa0 = newTempAddress();

    final Future<SocketChannel> acceptCall;
    final Future<SocketChannel> acceptCall2;
    Future<Void> connectCall = null;

    AtomicBoolean socketDomainWillAcceptCallOnFirstBind = new AtomicBoolean(true);

    try (ServerSocketChannel ssc1 = selectorProvider().openServerSocketChannel()) {
      bindServerSocket(ssc1, sa0, 1);
      final SocketAddress sa = resolveAddressForSecondBind(sa0, ssc1);

      AtomicBoolean connectMustSucceed = new AtomicBoolean(false);
      AtomicBoolean wasRebound = new AtomicBoolean(false);

      acceptCall = TestAsyncUtil.supplyAsync(() -> {
        try {
          SocketChannel sc;
          try {
            sc = ssc1.accept();
          } catch (ClosedChannelException | SocketClosedException e) {
            if (wasRebound.get()) {
              // The system terminated our accept because another socket was rebound
              // This may not occur on all systems, but we have to handle it.
              return null;
            } else {
              throw e;
            }
          }
          socketDomainWillAcceptCallOnFirstBind.set(false);
          Objects.requireNonNull(sc);
          if (reuseAddress && !connectMustSucceed.get()) {
            // fail("Did not throw SocketException"); // no longer thrown in Sonoma 14.2.1?
          }
          return sc;
        } catch (ClosedChannelException | SocketException e) { // NOPMD.ExceptionAsFlowControl
          String msg = checkKnownBugAcceptFailure(e);
          if (msg != null) {
            throw new TestAbortedWithImportantMessageException(
                MessageType.TEST_ABORTED_SHORT_WITH_ISSUES, msg, summaryImportantMessage(msg), e);
          }
          if (reuseAddress) {
            // expected (Software caused connection abort)
          } else {
            fail(e);
          }
        } catch (SocketTimeoutException e) {
          String msg = checkKnownBugAcceptFailure(e);
          if (msg != null) {
            throw new TestAbortedWithImportantMessageException(
                MessageType.TEST_ABORTED_SHORT_WITH_ISSUES, msg, summaryImportantMessage(msg), e);
          }
          fail(e);
        } catch (IOException e) { // NOPMD.ExceptionAsFlowControl
          fail(e);
        }
        return null;
      });

      try (ServerSocketChannel ssc2 = selectorProvider().openServerSocketChannel()) {
        try {
          ssc2.setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        } catch (UnsupportedOperationException e) {
          // ignore
        }

        try {
          wasRebound.set(true);
          bindServerSocket(ssc2, sa, 1);
          if (!reuseAddress && !socketDomainPermitsDoubleBind()) {
            fail("Did not throw expected SocketException (Address already in use)");
          }
        } catch (ClosedChannelException | SocketException e) {
          if (!reuseAddress) {
            // expected
          } else {
            // permissible, depending on socket domain
            // but connecting to the first server must succeed
            connectMustSucceed.set(true);
          }
        }

        if (reuseAddress) {
          acceptCall2 = TestAsyncUtil.supplyAsync(() -> {
            try {
              SocketChannel sc = ssc2.accept();
              socketDomainWillAcceptCallOnFirstBind.set(false);
              Objects.requireNonNull(sc);
              return sc;
            } catch (InvalidArgumentSocketException e) {
              if (!acceptCall.isDone()) {
                socketDomainWillAcceptCallOnFirstBind.set(true);
                // some socket domains will permit a bind but not another accept until the first one
                // is done (e.g., VSOCK).
              } else {
                fail(e);
              }
            } catch (IOException e) {
              fail(e);
            }
            return null;
          });
        } else {
          acceptCall2 = null;
        }

        // unblock accept of any successful bind
        if (!acceptCall.isDone() && socketDomainWillAcceptCallOnFirstBind.get()) {
          connectCall = TestAsyncUtil.supplyAsync(() -> {
            try {
              newSocket().connect(sa);
            } catch (ClosedChannelException | SocketClosedException e) {
              // ignore
            } catch (SocketException e) {
              if (connectMustSucceed.get()) {
                fail("Connect should have succeeded", e);
              } else {
                // ignore
              }
            } catch (IOException e) {
              fail(e);
            }
          });
        }
      }
    }

    // Assert that eventually all accept jobs have terminated.
    if (acceptCall2 != null) {
      try {
        acceptCall2.get(5, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // ignore socket closed etc.
      } catch (TimeoutException e) {
        fail("Second accept call did not terminate");
      }
    }

    String triggerWithIssues = null;

    try {
      acceptCall.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      // ignore socket closed etc.
      if (e.getCause() instanceof TestAbortedWithImportantMessageException) {
        throw (TestAbortedWithImportantMessageException) e.getCause(); // NOPMD.PreserveStackTrace
      } else {
        throw e;
      }
    } catch (TimeoutException e) {
      triggerWithIssues = checkKnownBugFirstAcceptCallNotTerminated();
      if (triggerWithIssues == null) {
        fail("First accept call did not terminate");
      }
    }
    if (triggerWithIssues != null) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          triggerWithIssues, summaryImportantMessage(triggerWithIssues));
    }

    if (connectCall != null) {
      try {
        connectCall.get(5, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // ignore socket closed etc.
      } catch (TimeoutException e) {
        fail("Connect call did not terminate");
      }
    }
  }

  /**
   * Subclasses may override this to tell that there is a known issue with "accept".
   *
   * @param e The exception
   * @return An explanation iff this should not cause a test failure but trigger "With issues".
   */
  protected String checkKnownBugAcceptFailure(IOException e) {
    return null;
  }

  /**
   * Bind the given address to the given {@link ServerSocketChannel}.
   *
   * By default, this just calls `ssc.bind(sa)`, but you may handle some exceptions by overriding
   * this method in a subclass.
   *
   * @param ssc The server socket channel.
   * @param sa The socket address to bind to.
   * @throws IOException on error.
   */
  protected void handleBind(ServerSocketChannel ssc, SocketAddress sa) throws IOException {
    ssc.bind(sa);
  }

  /**
   * Connect the given {@link SocketChannel} with the given address.
   *
   * By default, this just calls `sc.connect(sa)`, but you may handle some exceptions by overriding
   * this method in a subclass.
   *
   * @param sc The socket channel.
   * @param sa The socket address to connect to.
   * @throws IOException on error.
   */
  protected boolean handleConnect(SocketChannel sc, SocketAddress sa) throws IOException {
    return sc.connect(sa);
  }

  @Test
  public void testByteBufferWithPositionOffset() throws Exception {
    SocketAddress sa = newTempAddress();

    final int bb1Offset = 32;
    final int bb2Offset = 1;

    byte[] data = new byte[96];
    getRandom().nextBytes(data);

    try (ServerSocketChannel ssc = selectorProvider().openServerSocketChannel()) {
      handleBind(ssc, sa);

      ByteBuffer bb1 = ByteBuffer.allocate(data.length + bb1Offset);
      bb1.position(bb1Offset);

      bb1.put(data);
      bb1.flip();

      bb1.position(bb1Offset);
      assertEquals(bb1Offset, bb1.position());

      TestAsyncUtil.runAsync(() -> {
        try (SocketChannel sc = ssc.accept()) {
          int written = 0;
          while (bb1.hasRemaining()) {
            written += sc.write(bb1);
          }
          assertEquals(data.length, written);
        } catch (IOException e) {
          fail(e);
        }
      });

      SocketChannel sc = selectorProvider().openSocketChannel();

      ByteBuffer bb2 = ByteBuffer.allocate(data.length + bb2Offset);

      assertTrue(handleConnect(sc, ssc.getLocalAddress()));

      bb2.position(bb2Offset);

      int read = 0;
      int r;
      do {
        r = sc.read(bb2);
        if (r == -1) {
          break;
        }
        read += r;
      } while (bb2.hasRemaining());
      assertEquals(data.length, read);

      assertEquals(bb1.capacity(), bb1.position());
      assertEquals(bb1.capacity(), bb1.limit());
      assertEquals(data.length + bb2Offset, bb2.position());
      assertEquals(bb2.capacity(), bb2.limit());

      bb1.position(bb1Offset);

      for (int i = 0, n = data.length; i < n; i++) {
        assertEquals(bb1.get(bb1Offset + i), bb2.get(bb2Offset + i), "at pos " + i);
      }
    }
  }

  /**
   * Subclasses may override this to tell that there is a known issue with "First accept call did
   * not terminate".
   *
   * @return An explanation iff this should not cause a test failure but trigger "With issues".
   */
  protected String checkKnownBugFirstAcceptCallNotTerminated() {
    return null;
  }

  /**
   * Returns the temporary address usable to binding on for a second bind.
   *
   * Depending on the socket domain, a wildcard address may be permittable or not for a second bind.
   *
   * @param originalAddress The original temporary address (e.g., a wildcard address).
   * @param ssc The socket that was bound to that address.
   * @return The local bound address, or the {@code originalAddress}.
   * @throws IOException on error.
   * @see #testDoubleBindAddressReusable()
   */
  protected SocketAddress resolveAddressForSecondBind(SocketAddress originalAddress,
      ServerSocketChannel ssc) throws IOException {
    return ssc.getLocalAddress();
  }

  /**
   * Override to declare that a certain socket domain permits double-binding an address,
   * particularly when the address is comparable to a wildcard address.
   *
   * @return {@code true} iff double-binding the same address is allowed.
   * @see #testDoubleBindAddressReusable()
   */
  protected boolean socketDomainPermitsDoubleBind() {
    return false;
  }

  @Test
  public void testReadNotConnectedYet() throws Exception {
    SocketChannel sc = newSocketChannel();
    assertThrows(NotYetConnectedException.class, () -> sc.read(ByteBuffer.allocate(1)));
  }

  @Test
  public void testWriteNotConnectedYet() throws Exception {
    SocketChannel sc = newSocketChannel();
    assertThrows(NotYetConnectedException.class, () -> sc.write(ByteBuffer.allocate(1)));
  }

  @Test
  public void testAcceptNotBoundYet() throws Exception {
    ServerSocketChannel sc = newServerSocketChannel();
    assertThrows(NotYetBoundException.class, sc::accept);
  }

  protected boolean mayTestBindNullThrowUnsupportedOperationException() {
    return true;
  }

  protected boolean mayTestBindNullHaveNullLocalSocketAddress() {
    return true;
  }

  protected void cleanupTestBindNull(ServerSocketChannel sc, SocketAddress addr) throws Exception {
  }

  protected ServerSocket socketIfPossible(ServerSocketChannel channel) {
    try {
      return channel.socket();
    } catch (UnsupportedOperationException e) {
      return null;
    }
  }

  @Test
  public void testBindNull() throws Exception {
    try (ServerSocketChannel sc = newServerSocketChannel()) {
      ServerSocket s = socketIfPossible(sc);
      assertTrue(s == null || !s.isBound());
      try {
        sc.bind(null);
      } catch (UnsupportedOperationException e) {
        if (mayTestBindNullThrowUnsupportedOperationException()) {
          // OK
          return;
        } else {
          throw e;
        }
      }
      assertTrue(s == null || s.isBound());

      SocketAddress addr = sc.getLocalAddress();
      if (!mayTestBindNullHaveNullLocalSocketAddress()) {
        assertNotNull(addr);
      }

      cleanupTestBindNull(sc, addr);
    }
  }
}
