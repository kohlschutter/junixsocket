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
import java.lang.ref.Cleaner;
import java.util.Objects;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/**
 * This wrapper (along with the Java 8-specific counterpart in src/main/java8) allows us to
 * implement cleanup logic for objects that are garbage-collectable/no longer reachable.
 *
 * <p>
 * Usage:
 * <ol>
 * <li>Create a subclass of CleanableState and attach it as a private field to the object you want
 * to be cleaned up. You may call that field {@code cleanableState}.</li>
 * <li>Define all resources that need to be cleaned up in this subclass (instead of the observed
 * object itself).</li>
 * <li>Make sure to not refer the observed object instance itself, as that will create a reference
 * cycle and prevent proper cleanup.</li>
 * <li>Implement the {@link #doClean()} method to perform all the necessary cleanup steps.</li>
 * <li>If the observed class implements {@code close()}, it's a good practice to have it just call
 * {@code cleanableState.runCleaner()}.</li>
 * </ol>
 * <p>
 * Exceptions thrown upon doClean() are either thrown via {@link #close()} when invoked directly,
 * or, if cleaned during Garbage collection, to the exception handler specified in the constructor
 * or (if none specified) to the default uncaught exception handler.
 * <p>
 * Implementation details:
 * <ul>
 * <li>In Java 9 or later, {@link Cleaner} is used under the hood.</li>
 * <li>In Java 8 or earlier, {@link #finalize()} calls {@link #doClean()} directly.</li>
 * </ul>
 *
 * @author Christian Kohlschütter
 */
@IgnoreJRERequirement // see src/main/java8
public abstract class CleanableState implements Closeable {
  /**
   * A default exception handler: Calls {@link Thread#getDefaultUncaughtExceptionHandler()} with the
   * stacktrace and information about the current thread.
   */
  public static final AFConsumer<Throwable> DEFAULT_EXCEPTION_HANDLER = (t) -> Thread
      .getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), t);

  private static final Cleaner CLEANER = Cleaner.create();
  private final Cleaner.Cleanable cleanable;
  private AFConsumer<Throwable> exceptionHandler;
  private AFConsumer<Throwable> exceptionHandlerCurrent;
  private IOException exceptionUponClose = null;

  /**
   * Creates a state object to be used as an implementation detail of the specified observed
   * instance, using the {@link #DEFAULT_EXCEPTION_HANDLER}.
   *
   * @param observed The observed instance (the outer class referencing this
   *          {@link CleanableState}).
   */
  protected CleanableState(Object observed) {
    this(observed, DEFAULT_EXCEPTION_HANDLER);
  }

  /**
   * Creates a state object to be used as an implementation detail of the specified observed
   * instance, using a custom exception handler.
   *
   * @param observed The observed instance (the outer class referencing this
   *          {@link CleanableState}).
   * @param exceptionHandler The exception handler.
   */
  protected CleanableState(Object observed, AFConsumer<Throwable> exceptionHandler) {
    this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
    this.exceptionHandlerCurrent = exceptionHandler;
    this.cleanable = CLEANER.register(observed, () -> doClean1()); // NOPMD.LambdaCanBeMethodReference
                                                                   // (Retrolambda)
  }

  /**
   * Explicitly the cleanup code defined in {@link #doClean()}. This is best be called from a
   * {@code close()} method in the observed class.
   */
  public final void runCleaner() {
    cleanable.clean();
  }

  private void doClean1() {
    try {
      doClean();
    } catch (Throwable t) { // NOPMD
      exceptionHandlerCurrent.accept(t);
    }
  }

  /**
   * Performs the actual cleanup. Be sure to always clean up whenever possible, either by tracking
   * potential exceptions or by using try-finally to ensure proper cleanup.
   *
   * @throws IOException on error.
   */
  protected abstract void doClean() throws IOException;

  /**
   * Checks if we're being called from within {@link #close()}.
   *
   * @return {@code true} if being called from within {@link #close()}.
   */
  protected boolean inClose() {
    return exceptionHandlerCurrent != exceptionHandler; // NOPMD
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
