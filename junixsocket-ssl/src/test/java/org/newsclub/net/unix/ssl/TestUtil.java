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
package org.newsclub.net.unix.ssl;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;

final class TestUtil {
  private TestUtil() {
    throw new IllegalStateException("No instances");
  }

  @SafeVarargs
  static void assertInstanceOf(Throwable t, Class<? extends Throwable>... expected) {
    if (t == null) {
      fail("Should have thrown something, specifically one of " + Arrays.asList(expected));
      return;
    }
    Class<? extends Throwable> thrownClass = t.getClass();
    for (Class<? extends Throwable> e : expected) {
      if (e.isAssignableFrom(thrownClass)) {
        return;
      }
    }
    fail("Should have thrown one of " + Arrays.asList(expected) + " but did: " + t);
  }
}
