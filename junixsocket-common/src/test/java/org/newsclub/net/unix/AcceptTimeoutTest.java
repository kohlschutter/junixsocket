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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

/**
 * Verifies that accept properly times out when an soTimeout was specified.
 *
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class AcceptTimeoutTest<A extends SocketAddress> extends SocketTestBase<A> {
  private static final int TIMING_INACCURACY_MILLIS = 5000;

  protected AcceptTimeoutTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testCatchTimeout() throws Exception {
    final int timeoutMillis = 500;
    assertTimeoutPreemptively(Duration.ofMillis(5 * timeoutMillis), () -> {
      try (ServerSocket sock = startServer()) {
        long time = System.currentTimeMillis();
        sock.setSoTimeout(timeoutMillis);
        long actualTimeout = sock.getSoTimeout();
        if (actualTimeout == 0) {
          // timeout not supported. So far we know this is only true for z/OS
          if ("z/OS".equals(System.getProperty("os.name"))) {
            return;
          }
        }
        assertTrue(Math.abs(timeoutMillis - actualTimeout) <= TIMING_INACCURACY_MILLIS,
            "We should roughly get the same timeout back that we set before, but was "
                + actualTimeout + " instead of " + timeoutMillis);
        try (Socket socket = sock.accept()) {
          fail("Did not receive " + SocketTimeoutException.class.getName() + "; socket=" + socket);
        } catch (SocketException | SocketTimeoutException e) {
          // expected
          time = System.currentTimeMillis() - time;

          assertTrue(Math.abs(time - timeoutMillis) <= TIMING_INACCURACY_MILLIS,
              "Timeout not properly honored. Exception thrown after " + time + "ms vs. expected "
                  + timeoutMillis + "ms");
        }
      }
    });
  }

  @Test
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.ExcessiveMethodLength"})
  @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
  public void testTimeoutAfterDelay() throws Exception {
    final int timeoutMillis = 5000;

    AtomicBoolean keepRunning = new AtomicBoolean(true);

    CompletableFuture<SocketAddress> serverAddressCF = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofMillis(2 * timeoutMillis), () -> {
        try (ServerSocket serverSock = startServer()) {
          final int connectDelayMillis = 50;
          serverSock.setSoTimeout(timeoutMillis);

          long actualTimeout = serverSock.getSoTimeout();
          if (actualTimeout == 0) {
            // timeout not supported. So far we know this is only true for z/OS
            if ("z/OS".equals(System.getProperty("os.name"))) {
              return;
            }
          }
          assertTrue(Math.abs(timeoutMillis - actualTimeout) <= 10,
              "We should roughly get the same timeout back that we set before, but was "
                  + actualTimeout + " instead of " + timeoutMillis);

          final AtomicBoolean accepted = new AtomicBoolean(false);
          final CompletableFuture<RuntimeException> runtimeExceptionCF = new CompletableFuture<>();

          SocketAddress serverAddress = serverSock.getLocalSocketAddress();
          serverAddressCF.complete(serverAddress);

          new Thread() {
            private final Socket socket = newSocket();

            {
              setDaemon(true);
            }

            @Override
            public void run() {
              int i = 0;
              while (keepRunning.get()) {
                i++;
                try {
                  Thread.sleep(connectDelayMillis);
                } catch (InterruptedException e) {
                  return;
                }

                try {
                  connectSocket(socket, serverAddress);
                  runtimeExceptionCF.complete(null);
                } catch (SocketTimeoutException e) {
                  if (!keepRunning.get()) {
                    return;
                  }

                  System.out.println("SocketTimeout, trying connect again (" + i + ")");
                  // e.printStackTrace();
                  continue;
                } catch (TestAbortedWithImportantMessageException e) {
                  runtimeExceptionCF.complete(e);
                } catch (IOException e) {
                  // ignore "connection reset by peer", etc. after connection was accepted
                  if (!accepted.get()) {
                    e.printStackTrace();
                  }
                }

                break; // NOPMD.AvoidBranchingStatementAsLastInLoop
              }
            }
          }.start();

          long time = System.currentTimeMillis();
          try (Socket socket = serverSock.accept();) {
            assertNotNull(socket);
            accepted.set(true);
          }
          time = System.currentTimeMillis() - time;

          RuntimeException re = runtimeExceptionCF.get();
          if (re != null) {
            throw re;
          }

          assertTrue(time >= connectDelayMillis && (time < timeoutMillis || (time
              - connectDelayMillis) <= TIMING_INACCURACY_MILLIS),
              "Timeout not properly honored. Accept succeeded after " + time + "ms vs. expected "
                  + timeoutMillis + "ms");
        }
      });
    } catch (AssertionFailedError e) {
      String msg = checkKnownBugAcceptTimeout(serverAddressCF.getNow(null));
      if (msg == null) {
        throw e;
      } else {
        throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_WITH_ISSUES,
            msg, e);
      }
    } finally {
      keepRunning.set(false);
    }
  }

  /**
   * Subclasses may override this to tell that there is a known issue with "Accept timeout after
   * delay".
   *
   * @param serverAddr The server address.
   * @return An explanation iff this should not cause a test failure but trigger "With issues".
   */
  protected String checkKnownBugAcceptTimeout(SocketAddress serverAddr) {
    return null;
  }

  @Test
  public void testAcceptWithoutBindToService() throws Exception {
    ServerSocket ss = newServerSocket();
    assertThrows(SocketException.class, () -> ss.accept());
  }
}
