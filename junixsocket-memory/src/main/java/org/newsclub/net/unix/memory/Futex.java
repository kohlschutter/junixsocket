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

interface Futex extends Closeable {
  boolean tryWait(int ifValue, int timeoutMillis) throws IOException;

  boolean tryWake(boolean wakeAll) throws IOException;

  default boolean tryWakeWithTimeout(boolean wakeAll, int timeoutMillis, int pauseMillis)
      throws IOException, InterruptedException {
    long end = System.currentTimeMillis() + timeoutMillis;
    while (!tryWake(false)) {
      if (System.currentTimeMillis() >= end) {
        return false;
      } else {
        Thread.sleep(pauseMillis);
      }
    }
    return true;
  }

  boolean isClosed();
}
