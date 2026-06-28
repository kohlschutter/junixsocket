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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.TestUtil;
import org.newsclub.net.unix.ThreadUtil;

import com.kohlschutter.testutil.ForkedVM;
import com.kohlschutter.testutil.ForkedVMRequirement;
import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

/**
 * Ensures dependent virtual-thread socket reads can complete when the common pool has a single
 * worker.
 * 
 * @author Jakub Kultys (Azahe)
 */
@SuppressWarnings({"PMD.DoNotUseThreads", "PMD.AvoidInstantiatingObjectsInsideLoops"})
public class VirtualThreadPollerNaiveDeadlockTest {
  @Test
  @ForkedVMRequirement(forkSupported = true)
  public void testWithParallelism1() throws Exception {
    if (!ThreadUtil.isVirtualThreadSupported()) {
      throw new TestAbortedNotAnIssueException("Virtual threads not supported");
    }

    if (ThreadUtil.commonPool().getParallelism() == 1) {
      // no need to fork
      testNoForkWithParallelism1();
    } else {
      testForkWithParallelism1();
    }
  }

  private void testForkWithParallelism1() throws Exception {
    ForkedVM vm = new ForkedVM(getClass()) {
      @Override
      protected void onJavaMainClass(String arg) {
        onJavaOption("-Djava.util.concurrent.ForkJoinPool.common.parallelism=1");
        if (TestUtil.getJavaFeatureVersion() >= 24) {
          onJavaOption("--enable-native-access=ALL-UNNAMED");
        }
        super.onJavaMainClass(arg);
      }
    };
    vm.setRedirectError(Redirect.PIPE);
    vm.setRedirectOutput(Redirect.INHERIT);

    Process p = vm.fork();

    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream(),
        Charset.defaultCharset()))) {
      ThreadUtil.startNewDaemonThread(false, () -> {
        String l;
        try {
          while ((l = errorReader.readLine()) != null) {
            if (l.startsWith("WARNING: Unknown module: ")) {
              // ignore
              continue;
            }
            System.err.println(l);
          }
        } catch (Exception e) { // NOPMD
          e.printStackTrace();
        }
      });

      assertTrue(p.waitFor(10, TimeUnit.SECONDS),
          "Forked VM should have terminated within a resonable time interval");
      assertEquals(0, p.exitValue(), "Forked VM should have terminated successfully");
    }
  }

  @Test
  @ForkedVMRequirement(forkSupported = false)
  public void testNoForkWithParallelism1() throws Exception {
    reproduce();
  }

  /**
   * Called from ForkedVM only (testForkWithParallelism1).
   * 
   * @param args Unchecked.
   * @throws Exception on error.
   */
  public static void main(String[] args) throws Exception {
    reproduce();
  }

  private static void reproduce() throws Exception { // NOPMD.CognitiveComplexity
    if (!ThreadUtil.isVirtualThreadSupported()) {
      throw new TestAbortedNotAnIssueException("Virtual threads not supported");
    }
    if (ThreadUtil.commonPool().getParallelism() != 1) {
      throw new TestAbortedNotAnIssueException(
          "Test requires -Djava.util.concurrent.ForkJoinPool.common.parallelism=1");
    }

    Path socketPath = Files.createTempFile("junixsocket-vtpoll-deadlock-", ".sock");
    Files.deleteIfExists(socketPath);

    CountDownLatch firstTransactionOwnsResource = new CountDownLatch(1);
    CountDownLatch secondTransactionAwaitsResource = new CountDownLatch(1);
    CountDownLatch secondTransactionPolls = new CountDownLatch(1);
    CountDownLatch firstTransactionFinished = new CountDownLatch(1);
    CountDownLatch secondTransactionFinished = new CountDownLatch(1);

    try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
      AFUNIXSocketAddress address = AFUNIXSocketAddress.of(socketPath.toFile());
      server.bind(address);

      startPlatformThread(() -> {
        try (AFUNIXSocket socket = server.accept()) {
          int transaction = socket.getInputStream().read();
          if (transaction == 1) {
            socket.getOutputStream().write(11);
            socket.getOutputStream().flush();
          } else if (transaction == 2) {
            firstTransactionFinished.await();
            socket.getOutputStream().write(22);
            socket.getOutputStream().flush();
          }
        }
      });
      startPlatformThread(() -> {
        try (AFUNIXSocket socket = server.accept()) {
          int transaction = socket.getInputStream().read();
          if (transaction == 1) {
            socket.getOutputStream().write(11);
            socket.getOutputStream().flush();
          } else if (transaction == 2) {
            firstTransactionFinished.await();
            socket.getOutputStream().write(22);
            socket.getOutputStream().flush();
          }
        }
      });

      ExecutorService virtualEs = ThreadUtil.newVirtualThreadPerTaskExecutor();

      virtualEs.submit(() -> {
        try (AFUNIXSocket socket = AFUNIXSocket.newInstance()) {
          socket.connect(address);

          firstTransactionOwnsResource.countDown();
          secondTransactionAwaitsResource.await();
          secondTransactionPolls.await();

          socket.getOutputStream().write(1);
          socket.getOutputStream().flush();

          if (socket.getInputStream().read() == 11) {
            firstTransactionFinished.countDown();
          }
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      });

      virtualEs.submit(() -> {
        try (AFUNIXSocket socket = AFUNIXSocket.newInstance()) {
          firstTransactionOwnsResource.await();

          socket.connect(address);
          socket.getOutputStream().write(2);
          socket.getOutputStream().flush();

          secondTransactionAwaitsResource.countDown();
          if (socket.getInputStream().read() == 22) {
            secondTransactionFinished.countDown();
          }
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      });

      assertTrue(secondTransactionAwaitsResource.await(5, TimeUnit.SECONDS));
      assertTrue(waitForPoller());
      secondTransactionPolls.countDown();

      if (!firstTransactionFinished.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Deadlock detected");
      }
      assertTrue(secondTransactionFinished.await(5, TimeUnit.SECONDS));
    } finally {
      Files.deleteIfExists(socketPath);
    }
  }

  private static void startPlatformThread(ThrowingRunnable runnable) {
    ThreadUtil.startNewDaemonThread(false, () -> {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static boolean waitForPoller() throws InterruptedException {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

    final String nativeUnixSocketClassName = TestUtil.getNativeUnixSocketClassName();

    while (System.nanoTime() < deadlineNanos) {
      for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
        if (ThreadUtil.isTrulyAVirtualThread(entry.getKey())) {
          continue;
        }
        for (StackTraceElement element : entry.getValue()) {
          if ("poll".equals(element.getMethodName()) && nativeUnixSocketClassName.equals(element
              .getClassName())) {
            return true;
          }
        }
      }
      Thread.sleep(25);
    }
    return false;
  }
}
