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
 * Internal helper class to dump stack traces when deemed appropriate.
 *
 * NOTE: For junit testing classes, use {@code kohlschutter-test-util}'s {@code TestStackTraceUtil}.
 *
 * @author Christian Kohlschütter
 */
public final class StackTraceUtil {
  private StackTraceUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Print/Log the stack trace of the given Throwable.
   *
   * @param t The throwable to log.
   */
  public static void printStackTrace(Throwable t) {
    t.printStackTrace(); // NOPMD
  }

  /**
   * Print/Log the stack trace of the given Throwable, marking this entry as a "severe condition".
   *
   * @param t The throwable to log.
   */
  public static void printStackTraceSevere(Throwable t) {
    t.printStackTrace(); // NOPMD
  }
}
