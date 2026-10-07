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
package org.newsclub.net.unix;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.newsclub.net.unix.AFSelector.PollFd;

/**
 * Implementation of {@link VirtualThreadPoller} that multiplexes all waiting file descriptors onto
 * a small, fixed number of platform "poller" threads.
 *
 * A virtual thread that would block registers its file descriptor with a poller and parks. The
 * poller calls {@link NativeUnixSocket#poll(PollFd, int)} on all registered file descriptors at
 * once (plus a pipe used to wake it up upon new registrations), and unparks the virtual threads
 * whose file descriptors are ready.
 *
 * Unlike {@link VirtualThreadPollerNaive}, waiting does not occupy a platform thread per file
 * descriptor, so any number of virtual threads can wait concurrently without starving each other
 * (see https://github.com/kohlschutter/junixsocket/issues/172).
 *
 * @author Jakub Kultys (Azahe)
 */
final class VirtualThreadPollerShared implements VirtualThreadPoller {
  /**
   * Upper bound for a single poll; ensures that closed file descriptors and cancelled registrations
   * are noticed even if nothing else happens.
   */
  private static final int POLL_INTERVAL_MILLIS = 1_000;

  private static final String PROP_POLLERS = "org.newsclub.net.unix.VirtualThreadPoller.pollers";

  private static final String PROP_NAIVE = "org.newsclub.net.unix.VirtualThreadPoller.naive";

  private final Poller[] pollers;

  VirtualThreadPollerShared() throws IOException {
    this(Integer.parseInt(System.getProperty(PROP_POLLERS, "1")));
  }

  VirtualThreadPollerShared(int numPollers) throws IOException {
    if (numPollers < 1) {
      throw new IllegalArgumentException("numPollers");
    }
    Poller[] p = new Poller[numPollers];
    try {
      for (int i = 0; i < numPollers; i++) {
        p[i] = new Poller();
      }
    } catch (IOException | RuntimeException e) {
      for (Poller poller : p) {
        if (poller != null) {
          poller.closeQuietly();
        }
      }
      throw e;
    }
    for (int i = 0; i < numPollers; i++) {
      ThreadUtil.startNewDaemonThread(false, p[i]).setName("junixsocket VirtualThreadPoller-" + i);
    }
    this.pollers = p;
  }

  /**
   * Returns a new {@link VirtualThreadPollerShared}, or a {@link VirtualThreadPollerNaive} if that
   * is requested via system property, or if the former cannot be created.
   *
   * @return The new instance.
   */
  static VirtualThreadPoller newDefaultInstance() {
    if (Boolean.parseBoolean(System.getProperty(PROP_NAIVE, "false"))) {
      return new VirtualThreadPollerNaive();
    }
    try {
      return new VirtualThreadPollerShared();
    } catch (IOException | RuntimeException e) {
      StackTraceUtil.printStackTrace(e);
      return new VirtualThreadPollerNaive();
    }
  }

  @Override
  public void parkThreadUntilReady(FileDescriptor fd, int mode, long now,
      AFSupplier<Integer> timeout, Closeable closeOnInterrupt) throws IOException {
    Thread thread = Thread.currentThread();
    if (thread.isInterrupted() || !fd.valid()) {
      throw SocketClosedByInterruptException.newInstanceAndClose(closeOnInterrupt);
    }

    Registration reg = new Registration(fd, mode, thread);
    Poller poller = pollers.length == 1 ? pollers[0] : pollers[(System.identityHashCode(fd)
        & Integer.MAX_VALUE) % pollers.length];
    poller.register(reg);

    try {
      do {
        switch (reg.state.get()) {
          case Registration.READY:
            return;
          case Registration.CLOSED:
            throw SocketClosedByInterruptException.newInstanceAndClose(closeOnInterrupt);
          default:
            break;
        }
        if (thread.isInterrupted()) {
          throw SocketClosedByInterruptException.newInstanceAndClose(closeOnInterrupt);
        }

        int timeoutMillis = timeout.get();
        if (timeoutMillis > 0) {
          long remainingMillis = now + timeoutMillis - System.currentTimeMillis();
          if (remainingMillis <= 0) {
            throw new SocketTimeoutException();
          }
          LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(remainingMillis));
        } else {
          LockSupport.park(this);
        }
      } while (true); // NOPMD.WhileLoopWithLiteralBoolean
    } finally {
      // no-op if the poller already completed the registration;
      // otherwise the poller discards it in its next iteration
      reg.state.compareAndSet(Registration.WAITING, Registration.CANCELLED);
    }
  }

  /**
   * A virtual thread waiting for a file descriptor to become ready.
   */
  private static final class Registration {
    static final int WAITING = 0;
    static final int READY = 1;
    static final int CLOSED = 2;
    static final int CANCELLED = 3;

    final FileDescriptor fd;
    final int mode;
    final Thread thread;
    final AtomicInteger state = new AtomicInteger(WAITING);

    Registration(FileDescriptor fd, int mode, Thread thread) {
      this.fd = fd;
      this.mode = mode;
      this.thread = thread;
    }

    /**
     * Completes the registration, and unparks the waiting thread unless the registration had
     * already been completed or cancelled.
     *
     * @param newState The new state.
     */
    void complete(int newState) {
      if (state.compareAndSet(WAITING, newState)) {
        LockSupport.unpark(thread);
      }
    }

    boolean isWaiting() {
      return state.get() == WAITING;
    }
  }

  /**
   * A platform thread polling all file descriptors registered with it.
   */
  private static final class Poller implements Runnable {
    private final AFPipe wakeupPipe;
    private final FileDescriptor wakeupSource;
    private final FileDescriptor wakeupSink;
    private final int wakeupOptions;
    private final byte[] wakeupDrainBuffer = new byte[64];

    private final Queue<Registration> incoming = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean wakeupPending = new AtomicBoolean(false);

    // only accessed from the poller thread
    private List<Registration> active = new ArrayList<>();
    private List<Registration> nextActive = new ArrayList<>();

    Poller() throws IOException {
      this.wakeupPipe = AFUNIXSelectorProvider.getInstance().openSelectablePipe();
      this.wakeupSource = wakeupPipe.sourceFD();
      this.wakeupSink = wakeupPipe.sinkFD();
      this.wakeupOptions = wakeupPipe.getOptions();
      NativeUnixSocket.configureBlocking(wakeupSource, false);
      NativeUnixSocket.configureBlocking(wakeupSink, false);
    }

    void register(Registration reg) {
      incoming.add(reg);
      // At most one wakeup byte per poller iteration; see pollOnce() for the ordering requirements.
      if (wakeupPending.compareAndSet(false, true)) {
        try {
          NativeUnixSocket.write(wakeupSink, null, 1, 1, wakeupOptions, null);
        } catch (IOException e) {
          // the poller will still pick up the registration within POLL_INTERVAL_MILLIS
        }
      }
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public void run() {
      do {
        try {
          pollOnce();
        } catch (Throwable t) { // NOPMD
          // Must not die, or all registered threads would wait forever.
          // Wake up everyone; they will retry their operation and re-register if necessary.
          wakeUpAll();
          StackTraceUtil.printStackTrace(t);
          LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
      } while (true); // NOPMD.WhileLoopWithLiteralBoolean
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private void pollOnce() throws IOException {
      // Reset the flag *before* draining the queue: registrations added before this point are
      // picked up below; registrations added afterwards will trigger a new wakeup byte.
      wakeupPending.set(false);

      Registration reg;
      while ((reg = incoming.poll()) != null) {
        active.add(reg);
      }

      int size = active.size();
      FileDescriptor[] fds = new FileDescriptor[size + 1];
      int[] ops = new int[size + 1];
      fds[0] = wakeupSource;
      ops[0] = SelectionKey.OP_READ;

      int n = 1;
      for (int i = 0; i < size; i++) {
        reg = active.get(i);
        if (!reg.isWaiting()) {
          continue;
        }
        if (!reg.fd.valid()) {
          reg.complete(Registration.CLOSED);
          continue;
        }
        fds[n] = reg.fd;
        ops[n] = reg.mode;
        nextActive.add(reg);
        n++;
      }
      swapActive();

      PollFd pfd;
      if (n == fds.length) {
        pfd = new PollFd(fds, ops);
      } else {
        FileDescriptor[] fds0 = new FileDescriptor[n];
        int[] ops0 = new int[n];
        System.arraycopy(fds, 0, fds0, 0, n);
        System.arraycopy(ops, 0, ops0, 0, n);
        pfd = new PollFd(fds0, ops0);
      }

      int numReady;
      try {
        numReady = NativeUnixSocket.poll(pfd, POLL_INTERVAL_MILLIS);
      } catch (IOException e) {
        // e.g., EINTR. Wake up everyone; they will retry their operation (and encounter any
        // persistent error condition themselves), and re-register if necessary.
        wakeUpAll();
        return;
      }
      if (numReady <= 0) {
        return;
      }

      if (pfd.rops[0] != 0) {
        drainWakeupPipe();
      }

      // Registrations are in the same order as file descriptors 1..n
      size = active.size();
      for (int i = 0; i < size; i++) {
        reg = active.get(i);
        if (pfd.rops[i + 1] != 0) {
          // also when OP_INVALID (error/hangup): the caller retries and sees the actual condition
          reg.complete(Registration.READY);
        } else if (reg.isWaiting()) {
          nextActive.add(reg);
        }
      }
      swapActive();
    }

    private void swapActive() {
      List<Registration> tmp = active;
      active = nextActive;
      nextActive = tmp;
      nextActive.clear();
    }

    private void drainWakeupPipe() throws IOException {
      int read;
      do {
        read = NativeUnixSocket.read(wakeupSource, wakeupDrainBuffer, 0, wakeupDrainBuffer.length,
            wakeupOptions, null, 0);
      } while (read == wakeupDrainBuffer.length);
    }

    private void wakeUpAll() {
      for (Registration reg : active) {
        reg.complete(Registration.READY);
      }
      active.clear();
      nextActive.clear();
      Registration reg;
      while ((reg = incoming.poll()) != null) {
        reg.complete(Registration.READY);
      }
    }

    void closeQuietly() {
      try {
        wakeupPipe.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }
}
