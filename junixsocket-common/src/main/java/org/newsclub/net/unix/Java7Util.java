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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.eclipse.jdt.annotation.Nullable;

import com.kohlschutter.annotations.compiletime.SuppressForbidden;

/**
 * Helper class to allow codebase to remain compatible with Java 7 (via retrolambda,
 * animal-sniffer).
 *
 * @author Christian Kohlschütter
 */
@IgnoreJRERequirement // see src/main/java8
final class Java7Util {
  static <@Nullable U> AFFuture<U> supplyAsync(AFSupplier<U> supplier, Executor executor) {
    return CompletableFuture.supplyAsync(supplier::get, executor)::get;
  }

  static <K, V> V computeIfAbsent(Map<K, V> map, K key,
      AFFunction<? super K, ? extends V> mappingFunction) {
    return map.computeIfAbsent(key, mappingFunction::apply);
  }

  static ForkJoinPool commonPool() {
    return ForkJoinPool.commonPool();
  }

  static ExecutorService newWorkStealingPool() {
    return Executors.newWorkStealingPool();
  }

  @SuppressWarnings("deprecation")
  @SuppressForbidden
  static int getJavaFeatureVersion() {
    return Runtime.version().major();
  }
}
