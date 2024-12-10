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

import java.util.concurrent.Future;

import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;
import com.kohlschutter.testutil.TestAsyncUtil;
import com.kohlschutter.testutil.TestStackTraceUtil;

/**
 * Some test-related checks.
 *
 * @author Christian Kohlschütter
 */
public final class TestUtil {
  private static final boolean IS_HAIKU_OS = "Haiku".equals(System.getProperty("os.name"));

  private TestUtil() {
    throw new IllegalStateException("No instances");
  }

  public static void printStackTrace(Throwable t) {
    TestStackTraceUtil.printStackTrace(t);
  }

  public static void trackFuture(Future<?> future) {
    TestAsyncUtil.trackFuture(future);
  }

  /**
   * Checks if the system under test is Haiku Os.
   *
   * @return {@code true} if so.
   */
  public static boolean isHaikuOS() {
    // Checks if the system
    // under test is Haiku Os.
    // Return true if so.
    return IS_HAIKU_OS;
  }

  private static void handleBug(String id, Throwable e) throws Exception {
    String key = "selftest." + id;
    switch (System.getProperty(key, "")) {
      case "dump":
        e.printStackTrace();
        break;
      case "fail":
        if (e instanceof Exception) {
          throw (Exception) e;
        } else if (e instanceof Error) {
          throw (Error) e;
        } else {
          throw new IllegalStateException(e);
        }
      case "":
        // nothing
        return;
      default:
        System.err.println("Invalid value for System property " + key);
    }
  }

  /**
   * In certain Haiku environments, socketpair/connect/accept for AF_UNIX is buggy; it may not set
   * the "connected state" (fixed in hrev57189).
   *
   * See <a href="https://dev.haiku-os.org/ticket/18534">Haiku Bug 18534</a> for details.
   *
   * @param e The caught throwable.
   * @return A TestAbortedWithImportantMessageException wrapping the caught throwable.
   */
  public static Exception haikuBug18534(Throwable e) throws Exception {
    handleBug("haikuBug18534", e);
    return new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
        "AF_UNIX support is buggy in this Haiku release; see https://dev.haiku-os.org/ticket/18534",
        e);
  }

  /**
   * In certain Haiku environments, working with datagram sockets may result in a kernel hang
   * (spinlock upon send).
   *
   * See <a href="https://dev.haiku-os.org/ticket/18535">Haiku Bug 18535</a> for details.
   *
   * @param e The caught throwable.
   * @return A TestAbortedWithImportantMessageException wrapping the caught throwable.
   */
  public static Exception haikuBug18535(Throwable e) throws Exception, Error {
    handleBug("haikuBug18535", e);
    return new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
        "AF_UNIX datagram support is buggy in this Haiku release or environment; see https://dev.haiku-os.org/ticket/18535",
        e);
  }
}
