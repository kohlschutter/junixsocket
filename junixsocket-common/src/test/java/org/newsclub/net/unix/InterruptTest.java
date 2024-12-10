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

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class InterruptTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected InterruptTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testInterruptSocketVirtualThread() throws Exception {
    if (!ThreadUtil.isVirtualThreadSupported()) {
      throw new TestAbortedNotAnIssueException("Virtual Threads are not supported by this JVM");
    }

    try (ServerSocket serverSocket = newServerSocketBindOn(newTempAddress())) {
      CompletableFuture<Thread> myThread = new CompletableFuture<>();
      CompletableFuture<Boolean> result = new CompletableFuture<>();

      ExecutorService executor = ThreadUtil.newVirtualThreadPerTaskExecutor();
      TestUtil.trackFuture(executor.submit(() -> {
        myThread.complete(Thread.currentThread());
        boolean closedByInterrupt = false;
        try {
          serverSocket.accept();
        } catch (SocketClosedByInterruptException e) {
          if (Thread.interrupted()) {
            closedByInterrupt = true;
          } else {
            e.printStackTrace();
          }
        } catch (SocketException e) {
          if ("Closed by interrupt".equals(e.getMessage()) && Thread.interrupted() && serverSocket
              .isClosed()) {
            closedByInterrupt = true;
          } else {
            e.printStackTrace();
          }
        } catch (IOException e) {
          TestUtil.printStackTrace(e);
        } finally {
          result.complete(closedByInterrupt);
        }
      }));

      Thread t = myThread.get();
      t.interrupt();
      assertTrue(result.get(),
          "Thread should have thrown a \"Closed by interrupt\" SocketException,"
              + " the server socket should be closed, and the interrupt state should be set");

      executor.shutdownNow();
      assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }
  }

  @Test
  public void testInterruptSocketChannelVirtualThread() throws Exception {
    if (!ThreadUtil.isVirtualThreadSupported()) {
      throw new TestAbortedNotAnIssueException("Virtual Threads are not supported by this JVM");
    }

    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      try (ServerSocketChannel serverSocketChannel = newServerSocketChannelBindOn(
          newTempAddress())) {
        serverSocketChannel.configureBlocking(true);

        CompletableFuture<Thread> myThread = new CompletableFuture<>();
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        ExecutorService executor = ThreadUtil.newVirtualThreadPerTaskExecutor();
        TestUtil.trackFuture(executor.submit(() -> {
          myThread.complete(Thread.currentThread());
          boolean closedByInterrupt = false;
          try {
            serverSocketChannel.accept();
          } catch (ClosedByInterruptException e) {
            if (Thread.currentThread().isInterrupted()) {
              closedByInterrupt = true;
            } else {
              e.printStackTrace();
            }
          } catch (IOException e) {
            TestUtil.printStackTrace(e);
          } finally {
            result.complete(closedByInterrupt);
          }
        }));

        Thread t = myThread.get();
        t.interrupt();
        // assertTrue(result.get(),
        // "Thread should have thrown a \"Closed by interrupt\" SocketException, and the interrupt
        // state should be set");

        executor.shutdownNow();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
      }
    });
  }
}
