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

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;

import org.eclipse.jdt.annotation.Nullable;
import org.newsclub.net.unix.AFSelector.PollFd;

/**
 * "Naive" implementation of {@link VirtualThreadPoller}, using
 * {@link NativeUnixSocket#poll(PollFd, int)} on non-virtual threads.
 *
 * @author Christian Kohlschütter
 */
final class VirtualThreadPollerNaive implements VirtualThreadPoller {
  private static final int POLL_INTERVAL_MILLIS = 1_000; // should remain at 1 second to simplify
                                                         // socket timeout handling

  private static final Map<FileDescriptor, PollJob> POLL_JOBS = new ConcurrentHashMap<>();

  private static final InterruptedIOException POLL_INTERRUPTED_SENTINEL =
      new InterruptedIOException();

  private static final class PollJob {
    private final List<Thread> waitingThreads = new LinkedList<>();
    private final FileDescriptor fd;
    private final int mode;
    private final long now;
    private final AFSupplier<Integer> timeout;

    PollJob(FileDescriptor fd, int mode, long now, AFSupplier<Integer> timeout) {
      this.fd = fd;
      this.mode = mode;
      this.now = now;
      this.timeout = timeout;
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    AFFuture<@Nullable IOException> trigger(Thread waitingThread) {
      synchronized (fd) {
        waitingThreads.add(waitingThread);
      }
      return AFFuture.supplyAsync(() -> {
        try {
          Thread thread = Thread.currentThread();
          PollFd pfd = new PollFd(new FileDescriptor[] {fd}, new int[] {mode});
          do {
            if (thread.isInterrupted() || !fd.valid()) {
              return POLL_INTERRUPTED_SENTINEL;
            }
            try {
              NativeUnixSocket.poll(pfd, POLL_INTERVAL_MILLIS);
            } catch (IOException e) {
              return e;
            }
            if (thread.isInterrupted() || !fd.valid()) {
              return POLL_INTERRUPTED_SENTINEL;
            }
            if (pfd.rops[0] != 0) {
              break;
            }

            int timeoutMillis = timeout.get();
            if (timeoutMillis > 0) {
              if ((System.currentTimeMillis() - now) >= timeoutMillis) {
                // handle in calling thread
                break;
              }
            }
          } while (true); // NOPMD.WhileLoopWithLiteralBoolean
        } finally {
          Thread threadToWake = null;
          try {
            synchronized (fd) {
              threadToWake = waitingThreads.remove(0);
              if (waitingThreads.isEmpty()) {
                POLL_JOBS.remove(fd);
              }
            }
          } finally {
            if (threadToWake != null) {
              LockSupport.unpark(threadToWake);
            }
          }
        }

        return null;
      })::get;
    }
  }

  @Override
  public void parkThreadUntilReady(FileDescriptor fd, int mode, long now,
      AFSupplier<Integer> timeout, Closeable closeOnInterrupt) throws IOException {
    Thread virtualThread = Thread.currentThread();

    PollJob job = Java7Util.computeIfAbsent(POLL_JOBS, fd, (k) -> new PollJob(fd, mode, now,
        timeout));
    AFFuture<@Nullable IOException> future = job.trigger(virtualThread);

    LockSupport.park();
    if (virtualThread.isInterrupted()) {
      throw SocketClosedByInterruptException.newInstanceAndClose(closeOnInterrupt);
    }

    try {
      IOException ex = future.get();
      if (ex != null) {
        if (ex == POLL_INTERRUPTED_SENTINEL) {
          throw SocketClosedByInterruptException.newInstanceAndClose(closeOnInterrupt);
        }
        throw ex;
      }
    } catch (InterruptedException | ExecutionException e) {
      throw SocketClosedByInterruptException.newInstanceAndClose(closeOnInterrupt); // NOPMD.PreserveStackTrace
    }

    int timeoutMillis = timeout.get();
    if (timeoutMillis > 0) {
      if ((System.currentTimeMillis() - now) >= timeoutMillis) {
        throw new SocketTimeoutException();
      }
    }
  }
}
