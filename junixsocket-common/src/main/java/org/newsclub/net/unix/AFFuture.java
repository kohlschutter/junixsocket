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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;

/**
 * junixsocket-internal variant of a bare-bones {@link Future}, to allow compiling
 * junixsocket-common with retrolambda for Java 1.7.
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
interface AFFuture<T> {

  /**
   * Waits if necessary for the computation to complete, and then retrieves its result.
   *
   * @return the computed result
   * @throws CancellationException if the computation was cancelled
   * @throws ExecutionException if the computation threw an exception
   * @throws InterruptedException if the current thread was interrupted while waiting
   */
  T get() throws InterruptedException, ExecutionException;

  static <@Nullable U> AFFuture<U> supplyAsync(AFSupplier<U> supplier) {
    return Java7Util.supplyAsync(supplier);
  }
}
