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
import java.lang.ref.Cleaner;

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
 * </p>
 * <p>
 * Implementation details:
 * <ul>
 * <li>In Java 9 or later, {@link Cleaner} is used under the hood.</li>
 * <li>In Java 8 or earlier, {@link #finalize()} calls {@link #doClean()} directly.</li>
 * </ul>
 * </p>
 *
 * @author Christian Kohlschütter
 */
abstract class CleanableState implements Closeable {
  private static final Cleaner CLEANER = Cleaner.create();
  private final Cleaner.Cleanable cleanable;

  /**
   * Creates a state object to be used as an implementation detail of the specified observed
   * instance.
   *
   * @param observed The observed instance (the outer class referencing this
   *          {@link CleanableState}).
   */
  protected CleanableState(Object observed) {
    this.cleanable = CLEANER.register(observed, () -> doClean());
  }

  /**
   * Explicitly the cleanup code defined in {@link #doClean()}. This is best be called from a
   * {@code close()} method in the observed class.
   */
  public final void runCleaner() {
    cleanable.clean();
  }

  /**
   * Performs the actual cleanup.
   */
  protected abstract void doClean();

  @Override
  public final void close() throws IOException {
    runCleaner();
  }
}
