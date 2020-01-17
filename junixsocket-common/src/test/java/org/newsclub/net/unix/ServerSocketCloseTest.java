/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Ensures that we don't have any dangling "accept" calls after closing our ServerSocket.
 */
public class ServerSocketCloseTest {

  @Test
  public void testUnblockAcceptsWithSoTimeout() throws Exception {
    testUnblockAccepts(60 * 1000);
  }

  @Test
  public void testUnblockAcceptsWithoutSoTimeout() throws Exception {
    testUnblockAccepts(0);
  }

  private void testUnblockAccepts(int timeout) throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      File socketFile = SocketTestBase.initSocketFile();
      try (AFUNIXServerSocket serverSocket = AFUNIXServerSocket.bindOn(new AFUNIXSocketAddress(
          socketFile))) {
        serverSocket.setSoTimeout(timeout);

        final int numThreads = 32;

        final CountDownLatch cdl = new CountDownLatch(numThreads);

        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L,
            TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        for (int i = 0; i < numThreads; i++) {
          threadPool.submit(new Runnable() {

            @Override
            public void run() {
              try {
                cdl.countDown();
                serverSocket.accept();
              } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                  // ignore
                } else {
                  fail(e);
                }
              } catch (IOException e) {
                fail(e);
              }
            }
          });
        }

        // Wait until all threads are in accept
        cdl.await();

        serverSocket.close();

        threadPool.shutdown();
        threadPool.awaitTermination(500, TimeUnit.MILLISECONDS);

        assertEquals(0, threadPool.getActiveCount(), "There should be no pending accepts");
      }
    });
  }
}
