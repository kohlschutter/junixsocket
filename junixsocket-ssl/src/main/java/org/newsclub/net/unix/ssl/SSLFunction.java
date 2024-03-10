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
package org.newsclub.net.unix.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Function;

/**
 * A {@link Function} that may throw {@link GeneralSecurityException} or {@link IOException}.
 *
 * @param <T> The input type.
 * @param <R> The output type.
 * @author Christian Kohlschütter
 */
@FunctionalInterface
public interface SSLFunction<T, R> {

  /**
   * Applies this function to the given argument.
   *
   * @param t the function argument
   * @return the function result
   * @throws GeneralSecurityException on error.
   * @throws IOException on error.
   */
  R apply(T t) throws GeneralSecurityException, IOException;
}
