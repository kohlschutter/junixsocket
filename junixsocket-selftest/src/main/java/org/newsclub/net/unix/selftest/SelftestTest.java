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
package org.newsclub.net.unix.selftest;

import org.junit.jupiter.api.Test;

import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

public class SelftestTest {
  @Test
  public void testHelloWorld() {
  }

  @Test
  public void testFoo() {
    // fail();
  }

  @Test
  public void testStdout() {
    System.out.println("Some interrupting text");
  }

  @Test
  public void testBar() {
  }

  @Test
  public void testAbortedInformational() {
    throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_INFORMATIONAL,
        "Some important message with source info");
  }

  @Test
  public void testAbortedInformationalShort() {
    throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
        "Some important message");
  }
}
