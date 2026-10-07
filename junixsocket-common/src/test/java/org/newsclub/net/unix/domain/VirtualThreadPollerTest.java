/*
 * junixsocket
 *
 * Copyright 2009-2026 Christian Kohlschütter
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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.TestUtil;
import org.newsclub.net.unix.ThreadUtil;

import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

/**
 * Tests blocking socket operations on virtual threads, which are handled by the
 * {@code VirtualThreadPoller}.
 *
 * See https://github.com/kohlschutter/junixsocket/issues/172
 *
 * @author Jakub Kultys (Azahe)
 */
@SuppressWarnings({"PMD.DoNotUseThreads", "PMD.AvoidInstantiatingObjectsInsideLoops"})
public class VirtualThreadPollerTest {
  private static final int TRIGGER = 0;
  private static final int WAITER = 1;
  private static final int REPLY = 42;

  @BeforeEach
  public void requireVirtualThreads() {
    if (!ThreadUtil.isVirtualThreadSupported()) {
      throw new TestAbortedNotAnIssueException("Virtual threads not supported");
    }
  }

  /**
   * Ensures that virtual-thread socket reads cannot starve each other, regardless of how many
   * virtual threads are waiting for data at the same time.
   *
   * Many "waiter" connections block in {@code read()} until a "trigger" connection has received its
   * reply (think: transactions waiting for a lock held by another transaction). If waiting for a
   * file descriptor occupies a platform thread from a bounded pool, the waiters exhaust that pool
   * and the trigger's reply is never noticed: deadlock.
   */
  @Test
  public void testMoreWaitersThanProcessors() throws Exception { // NOPMD.CognitiveComplexity
    final int waiters = Runtime.getRuntime().availableProcessors() * 2;

    Path socketPath = TestUtil.newPathForUnixDomainSocket();
    CountDownLatch waitersConnected = new CountDownLatch(waiters);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(waiters + 1);
    AtomicInteger failures = new AtomicInteger();

    try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
      AFUNIXSocketAddress address = AFUNIXSocketAddress.of(socketPath.toFile());
      server.bind(address);

      ThreadUtil.startNewDaemonThread(false, () -> {
        while (!server.isClosed()) {
          AFUNIXSocket socket;
          try {
            socket = server.accept();
          } catch (IOException e) {
            return;
          }
          ThreadUtil.startNewDaemonThread(false, () -> {
            try (AFUNIXSocket s = socket) {
              if (s.getInputStream().read() == WAITER) {
                waitersConnected.countDown();
                release.await();
              }
              s.getOutputStream().write(REPLY);
              s.getInputStream().read(); // wait for client to close
            } catch (Exception e) { // NOPMD
              // ignore
            }
          });
        }
      });

      ExecutorService virtualEs = ThreadUtil.newVirtualThreadPerTaskExecutor();
      try {
        for (int i = 0; i < waiters; i++) {
          virtualEs.submit(() -> connectAndRead(address, WAITER, failures, done, null));
        }
        assertTrue(waitersConnected.await(5, TimeUnit.SECONDS));
        waitForPoller(waiters);

        virtualEs.submit(() -> connectAndRead(address, TRIGGER, failures, done, release));

        assertTrue(done.await(10, TimeUnit.SECONDS), "Deadlock detected: " + done.getCount()
            + " of " + (waiters + 1) + " connections did not complete");
        assertEquals(0, failures.get());
      } finally {
        release.countDown(); // unblock any stuck waiters
        virtualEs.shutdown();
      }
    } finally {
      Files.deleteIfExists(socketPath);
    }
  }

  @Test
  public void testReadTimeout() throws Exception {
    withConnectedPair((client, server) -> {
      client.setSoTimeout(200);
      Future<Throwable> f = ThreadUtil.newVirtualThreadPerTaskExecutor().submit(() -> {
        long start = System.nanoTime();
        try {
          client.getInputStream().read();
          return null;
        } catch (SocketTimeoutException e) {
          long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
          assertTrue(elapsedMillis >= 150, "Timed out too early: " + elapsedMillis + "ms");
          return e;
        }
      });
      assertTrue(f.get(5, TimeUnit.SECONDS) instanceof SocketTimeoutException);
    });
  }

  @Test
  public void testReadAfterTimeoutStillWorks() throws Exception {
    withConnectedPair((client, server) -> {
      client.setSoTimeout(100);
      ExecutorService virtualEs = ThreadUtil.newVirtualThreadPerTaskExecutor();
      Future<Integer> f = virtualEs.submit(() -> {
        try {
          client.getInputStream().read();
          throw new IllegalStateException("Expected timeout");
        } catch (SocketTimeoutException e) {
          // expected
        }
        client.setSoTimeout(0);
        return client.getInputStream().read();
      });
      Thread.sleep(300);
      server.getOutputStream().write(REPLY);
      assertEquals(REPLY, f.get(5, TimeUnit.SECONDS));
    });
  }

  @Test
  public void testCloseWhileReading() throws Exception {
    withConnectedPair((client, server) -> {
      CountDownLatch reading = new CountDownLatch(1);
      AtomicReference<Throwable> thrown = new AtomicReference<>();
      Future<?> f = ThreadUtil.newVirtualThreadPerTaskExecutor().submit(() -> {
        reading.countDown();
        try {
          int r = client.getInputStream().read();
          thrown.set(new IllegalStateException("Unexpected read result: " + r));
        } catch (IOException e) {
          thrown.set(e);
        }
      });
      assertTrue(reading.await(5, TimeUnit.SECONDS));
      Thread.sleep(200); // let the thread park
      client.close();
      try {
        f.get(5, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        throw new IllegalStateException(e);
      }
      assertTrue(thrown.get() instanceof SocketException, "Expected SocketException: " + thrown
          .get());
    });
  }

  @FunctionalInterface
  private interface PairConsumer {
    void accept(AFUNIXSocket client, AFUNIXSocket server) throws Exception;
  }

  private static void withConnectedPair(PairConsumer consumer) throws Exception {
    Path socketPath = TestUtil.newPathForUnixDomainSocket();
    try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
      AFUNIXSocketAddress address = AFUNIXSocketAddress.of(socketPath.toFile());
      server.bind(address);
      try (AFUNIXSocket client = AFUNIXSocket.connectTo(address);
          AFUNIXSocket serverSide = server.accept()) {
        consumer.accept(client, serverSide);
      }
    } finally {
      Files.deleteIfExists(socketPath);
    }
  }

  private static void connectAndRead(AFUNIXSocketAddress address, int role, AtomicInteger failures,
      CountDownLatch done, CountDownLatch onReply) {
    try (AFUNIXSocket socket = AFUNIXSocket.newInstance()) {
      socket.connect(address);
      socket.getOutputStream().write(role);
      if (socket.getInputStream().read() != REPLY) {
        failures.incrementAndGet();
      }
      if (onReply != null) {
        onReply.countDown();
      }
    } catch (Exception e) { // NOPMD
      failures.incrementAndGet();
    } finally {
      done.countDown();
    }
  }

  /**
   * Waits until the waiters are likely parked in the poller: until there are as many platform
   * threads inside {@code NativeUnixSocket.poll} as there are waiters (one-thread-per-fd pollers),
   * or until the number stops growing (multiplexing pollers).
   */
  private static void waitForPoller(int waiters) throws InterruptedException {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

    int previous = -1;
    int stableRounds = 0;
    while (System.nanoTime() < deadlineNanos) {
      int polling = countPlatformThreadsInPoll();
      if (polling >= waiters) {
        return;
      }
      if (polling > 0 && polling == previous) {
        if (++stableRounds >= 10) {
          return;
        }
      } else {
        stableRounds = 0;
      }
      previous = polling;
      Thread.sleep(25);
    }
  }

  private static int countPlatformThreadsInPoll() {
    final String nativeUnixSocketClassName = TestUtil.getNativeUnixSocketClassName();
    int polling = 0;
    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      if (ThreadUtil.isTrulyAVirtualThread(entry.getKey())) {
        continue;
      }
      for (StackTraceElement element : entry.getValue()) {
        if ("poll".equals(element.getMethodName()) && nativeUnixSocketClassName.equals(element
            .getClassName())) {
          polling++;
          break;
        }
      }
    }
    return polling;
  }
}
