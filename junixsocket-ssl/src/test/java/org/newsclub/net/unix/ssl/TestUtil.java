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

import java.net.SocketException;
import java.util.function.Supplier;

final class TestUtil {
  private TestUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Handle the case where both server and client may throw exceptions, and where a
   * {@link SocketException} is less interesting than something else.
   *
   * @param s1 The supplier of the first exception (preference given as tie breaker).
   * @param s2 The supplier of the second exception
   * @throws Exception on exception.
   * @throws Error on error.
   */
  @SuppressWarnings("PMD.CognitiveComplexity")
  static void throwMoreInterestingThrowableThanSocketException(Supplier<? extends Throwable> s1,
      Supplier<? extends Throwable> s2) throws Exception {
    Throwable t1 = s1.get();
    Throwable t2 = s2.get();

    // easy cases first
    if (t1 == null) {
      if (t2 == null) {
        return;
      } else {
        if (t2 instanceof Error) {
          throw (Error) t2;
        } else {
          throw (Exception) t2;
        }
      }
    } else if (t2 == null) {
      if (t1 instanceof Error) {
        throw (Error) t1;
      } else {
        throw (Exception) t1;
      }
    }

    if (t1 instanceof SocketException) {
      if (t2 instanceof SocketException) {
        throw (SocketException) t1;
      } else {
        if (t2 instanceof Error) {
          throw (Error) t2;
        } else {
          throw (Exception) t2;
        }
      }
    } else {
      if (t1 instanceof Error) {
        throw (Error) t1;
      } else {
        throw (Exception) t1;
      }
    }
  }
}
