/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class CleanableState implements Closeable {
  private final AtomicBoolean clean = new AtomicBoolean(false);

  @SuppressWarnings("PMD.UnusedFormalParameter")
  protected CleanableState(Object observed) {
  }

  public final void runCleaner() {
    if (clean.compareAndSet(false, true)) {
      doClean();
    }
  }

  @SuppressWarnings("all")
  @Override
  protected final void finalize() {
    try {
      runCleaner();
    } catch (Exception e) {
      // nothing that can be done here
    }
  }

  protected abstract void doClean();

  @Override
  public final void close() throws IOException {
    runCleaner();
  }
}
