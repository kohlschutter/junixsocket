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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Helper class to allow codebase to remain compatible with Java 7 (via retrolambda,
 * animal-sniffer).
 *
 * @author Christian Kohlschütter
 */
@IgnoreJRERequirement // see src/main/java8
final class Java7Util {
  private static final boolean JAVA8_OR_LATER;

  static {
    boolean ok;
    try {
      Class.forName("java.util.concurrent.CompletableFuture");
      ok = true;
    } catch (ClassNotFoundException e) {
      ok = false;
    }
    JAVA8_OR_LATER = ok;
  }

  static <@Nullable U> AFFuture<U> supplyAsync(AFSupplier<U> supplier) {
    if (JAVA8_OR_LATER) {
      return CompletableFuture.supplyAsync(supplier::get)::get;
    } else {
      return new Java7CompletableFuture<>(supplier);
    }
  }

  static <K, V> V computeIfAbsent(Map<K, V> map, K key,
      AFFunction<? super K, ? extends V> mappingFunction) {
    if (JAVA8_OR_LATER) {
      return map.computeIfAbsent(key, mappingFunction::apply);
    }

    Objects.requireNonNull(mappingFunction);
    V v;
    if ((v = map.get(key)) == null) {
      V newValue;
      if ((newValue = mappingFunction.apply(key)) != null) {
        map.put(key, newValue);
        return newValue;
      }
    }

    return v;
  }

  static final class Java7CompletableFuture<@Nullable T> implements AFFuture<T> {
    private final Semaphore sema = new Semaphore(0);
    private T object = null;

    Java7CompletableFuture(AFSupplier<T> supplier) {
      Thread t = new Thread(new Runnable() {

        @Override
        public void run() {
          object = supplier.get();
          sema.release();
        }
      });
      t.start();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      sema.acquire();
      return object;
    }
  }
}
