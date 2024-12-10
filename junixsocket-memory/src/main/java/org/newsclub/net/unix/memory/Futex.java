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
package org.newsclub.net.unix.memory;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * A generic Futex interface.
 *
 * @author Christian Kohlschütter
 */
interface Futex extends Closeable {
  /**
   * Tries to wait on the futex, if and as long as it has the value specified in {@code ifValue},
   * for the given amount in milliseconds, or, if {@code timeoutMillis} is 0, for as long as the
   * condition holds. Sporadic wakeups (with a return value of {@code false}) may occur.
   *
   * @param ifValue The expected value for this wait.
   * @param timeoutMillis The timeout, in milliseconds, or 0 for "undetermined".
   * @return {@code true} if the wait was successful (the value changed or {@link #tryWake(boolean)}
   *         was called), {@code false} otherwise.
   * @throws IOException on error.
   */
  boolean tryWait(int ifValue, int timeoutMillis) throws IOException;

  /**
   * Tries to wake waiting threads on the futex. If {@code wakeAll} is true, then all waiting
   * threads are woken up, if not, then only one is woken up.
   *
   * @param wakeAll {@code true} if all waiting threads should be woken.
   * @return {@code true} if we definitely woke some; {@code false} may indicate "we don't know".
   * @throws IOException on error.
   */
  boolean tryWake(boolean wakeAll) throws IOException;

  /**
   * Tries to wake any/all waiters on the futex for up to the given amount of time (in
   * {@code timeoutMillis}), intermittently pausing for {@code pauseMillis}) to give other threads
   * time to react, for as long as the timeout is not expired and {@code keepGoing} supplies
   * {@code true}.
   *
   * @param wakeAll {@code true} if all waiting threads should be woken.
   * @param timeoutMillis The maximum amount of time (in milliseconds) to try.
   * @param pauseMillis While trying, pause this amount of milliseconds to give other threads time
   *          to react.
   * @param keepGoing Keeps going unless this returns {@code true} or the timeout elapses.
   * @return {@code false} If the timeout elapsed without either {@link #tryWake(boolean)} returns
   *         {@code true} or {@code keepGoing} returns {@code false}.
   * @throws IOException on error.
   * @throws InterruptedException on interrupt.
   */
  default boolean tryWakeWithTimeout(boolean wakeAll, int timeoutMillis, int pauseMillis,
      Supplier<Boolean> keepGoing) throws IOException, InterruptedException {
    long end = System.currentTimeMillis() + timeoutMillis;
    while (keepGoing.get() && !tryWake(wakeAll)) {
      if (System.currentTimeMillis() >= end) {
        return false;
      } else {
        Thread.sleep(pauseMillis);
      }
    }
    return true;
  }

  /**
   * Returns {@code true} if this {@link Futex} has been closed.
   *
   * @return {@code true} if closed.
   */
  boolean isClosed();

  /**
   * Reports if this {@link Futex} can safely be accessed from multiple processes, or not. The
   * actual way of accessing this {@link Futex} is unspecified, but typically this is coordinated
   * via {@link SharedMemory}.
   * <p>
   * The value returned is constant.
   *
   * @return {@code true} if inter-process access is permitted.
   */
  boolean isInterProcess();
}
