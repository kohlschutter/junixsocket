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
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class CleanableState implements Closeable {
  public static final AFConsumer<Throwable> DEFAULT_EXCEPTION_HANDLER = (t) -> Thread
      .getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), t);

  private final AtomicBoolean clean = new AtomicBoolean(false);
  private final AFConsumer<Throwable> exceptionHandler;
  private AFConsumer<Throwable> exceptionHandlerCurrent;
  private IOException exceptionUponClose = null;

  @SuppressWarnings("PMD.UnusedFormalParameter")
  protected CleanableState(Object observed) {
    this(observed, DEFAULT_EXCEPTION_HANDLER);
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  protected CleanableState(Object observed, AFConsumer<Throwable> exceptionHandler) {
    this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
    this.exceptionHandlerCurrent = exceptionHandler;
  }

  public final void runCleaner() {
    if (clean.compareAndSet(false, true)) {
      doClean1();
    }
  }

  @SuppressWarnings("all")
  @Deprecated
  protected final void finalize() {
    try {
      runCleaner();
    } catch (Exception e) {
      // nothing that can be done here
    }
  }

  private void doClean1() {
    try {
      doClean();
    } catch (Throwable t) { // NOPMD
      exceptionHandlerCurrent.accept(t);
    }
  }

  protected abstract void doClean() throws IOException;

  protected boolean inClose() {
    return exceptionHandlerCurrent != exceptionHandler;
  }

  @Override
  public final void close() throws IOException {
    this.exceptionHandlerCurrent = (t) -> {
      IOException exc = exceptionUponClose;
      if (exc != null) {
        exc.addSuppressed(t);
      } else if (t instanceof IOException) {
        exceptionUponClose = (IOException) t;
      } else {
        exceptionUponClose = new IOException();
        exceptionUponClose.addSuppressed(t);
      }
    };
    runCleaner();
    this.exceptionHandlerCurrent = exceptionHandler;

    if (exceptionUponClose != null) {
      throw exceptionUponClose;
    }
  }
}
