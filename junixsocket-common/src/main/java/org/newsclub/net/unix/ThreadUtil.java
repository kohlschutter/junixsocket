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

import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/**
 * Helper class to support certain Thread-specific features.
 *
 * @author Christian Kohlschütter
 */
@IgnoreJRERequirement // see src/main/java20
public final class ThreadUtil {
  private ThreadUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Checks if the current {@link Thread} is to be considered a virtual thread.
   *
   * @return {@code true} if so.
   */
  public static boolean isVirtualThread() {
    return Thread.currentThread().isVirtual();
  }

  /**
   * Checks if virtual threads are considered to be supported (and therefore if special support
   * should be enabled).
   *
   * @return {@code true} if so.
   */
  public static boolean isVirtualThreadSupported() {
    return true;
  }

  /**
   * Returns a new "virtual thread per task executor".
   *
   * @return The new executor service.
   * @throws UnsupportedOperationException if not possible
   */
  public static ExecutorService newVirtualThreadPerTaskExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  /**
   * Checks if the current Thread has been interrupted, without clearing the flag; if interrupted,
   * an {@link InterruptedIOException} is thrown, otherwise {@code true} is returned.
   *
   * @return {@code true}.
   * @throws InterruptedIOException if interrupted.
   */
  public static boolean checkNotInterruptedOrThrow() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedIOException();
    }
    return true;
  }

  /**
   * Starts a new daemon thread.
   *
   * @param virtual If {@code true}, start a virtual Thread instead of a platform Thread; if
   *          {@code false}, a "daemon" platform thread is started.
   * @param run The runnable.
   * @return The thread.
   * @throws UnsupportedOperationException if not possible
   */
  public static Thread startNewDaemonThread(boolean virtual, Runnable run) {
    if (virtual) {
      return Thread.ofVirtual().start(run);
    } else {
      return Thread.ofPlatform().daemon(true).start(run);
    }
  }

  /**
   * Ensures that the given operation is being executed on a system thread. If the current thread is
   * a virtual thread, the operation is executed <em>synchronously</em> via
   * {@link CompletableFuture#runAsync(Runnable)}: the virtual thread is suspended during that
   * operation, and subsequently resumed.
   *
   * @param op The operation to run.
   * @throws InterruptedException on interrupt.
   */
  public static void runOnSystemThread(Runnable op) throws InterruptedException {
    if (isVirtualThread()) {
      Thread vt = Thread.currentThread();

      CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
        try {
          op.run();
        } finally {
          LockSupport.unpark(vt);
        }
      });

      LockSupport.park();
      try {
        cf.get();
      } catch (ExecutionException e) {
        Throwable t = e.getCause();
        if (t instanceof Error) {
          throw (Error) t; // NOPMD.PreserveStackTrace
        } else if (t instanceof RuntimeException) {
          throw (RuntimeException) t; // NOPMD.PreserveStackTrace
        } else {
          throw new IllegalStateException(e);
        }
      }
    } else {
      op.run();
    }
  }
}
