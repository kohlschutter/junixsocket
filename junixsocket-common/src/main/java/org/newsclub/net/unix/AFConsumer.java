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

/**
 * junixsocket-internal variant of {@code java.util.Consumer}, to allow compiling junixsocket-common
 * with retrolambda for Java 1.7.
 *
 * @param <T> the type of things to be accepted
 */
@FunctionalInterface
public interface AFConsumer<T> {

  /**
   * Accepts something.
   *
   * @param t the thing.
   */
  void accept(T t);
}
