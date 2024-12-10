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

/**
 * A mutually exclusive lock, which may or may not be reentrant.
 *
 * @author Christian Kohlschütter
 */
public interface SharedMutex extends Closeable {
  /**
   * Try to lock the mutex.
   * <p>
   * Note that unless {@link #isReentrant()} is {@code}, trying to lock the mutex a second time will
   * not succeed until someone else calls {@link #unlock()}
   *
   * @param timeoutMillis The timeout, in milliseconds, or {@code 0} for "try indefinitely".
   * @return {@code true} if the lock was acquired.
   * @throws IOException on error.
   */
  boolean tryLock(int timeoutMillis) throws IOException;

  /**
   * Unlocks the mutex.
   * <p>
   * By default, no ownership checks are performed.
   *
   * @throws IOException on error.
   */
  void unlock() throws IOException;

  /**
   * Reports if this lock instance is re-entrant, or not.
   * <p>
   * The value returned is constant.
   *
   * @return {@code true} if reentrant.
   */
  boolean isReentrant();

  /**
   * Reports if this lock instance can safely be accessed from multiple processes, or not. The
   * actual way of accessing this {@link SharedMutex} is unspecified, but typically this is
   * coordinated via {@link SharedMemory}.
   * <p>
   * The value returned is constant.
   *
   * @return {@code true} if inter-process access is permitted.
   */
  boolean isInterProcess();
}
