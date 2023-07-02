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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Ensures that we don't have any dangling "accept" calls after closing our ServerSocket.
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class ServerSocketCloseTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected ServerSocketCloseTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  public void testUnblockAcceptsWithSoTimeout() throws Exception {
    testUnblockAccepts(60 * 1000);
  }

  @Test
  public void testUnblockAcceptsWithoutSoTimeout() throws Exception {
    testUnblockAccepts(0);
  }

  private void testUnblockAccepts(int timeout) throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
      try (ServerSocket serverSocket = newServerSocketBindOn(getServerBindAddress())) {
        serverSocket.setSoTimeout(timeout);

        final int numThreads = 4;

        final CountDownLatch cdl = new CountDownLatch(numThreads);

        @SuppressWarnings("resource") // only since Java 19 ThreadPoolExecutor is AutoCloseable
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L,
            TimeUnit.SECONDS, new SynchronousQueue<>());
        for (int i = 0; i < numThreads; i++) {
          threadPool.submit(new Runnable() {

            @Override
            public void run() {
              try {
                cdl.countDown();
                try (Socket accept = serverSocket.accept()) {
                  // usually not reached
                  assertNotNull(accept);
                }
              } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                  // ignore
                } else {
                  fail(e);
                }
              } catch (IOException e) {
                e.printStackTrace();
                fail(e);
              }
            }
          });
        }

        // Wait until all threads are in accept
        cdl.await();
        Thread.sleep(100);

        serverSocket.close();

        threadPool.shutdown();
        threadPool.awaitTermination(5, TimeUnit.SECONDS);

        int active = threadPool.getActiveCount();
        if (active == numThreads) {
          checkFailedTestActuallySupported();
        }
        assertEquals(0, active, "There should be no pending accepts");
      }
    });
  }

  protected void checkFailedTestActuallySupported() {
  }
}
