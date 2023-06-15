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
package org.newsclub.net.unix;

import java.net.SocketAddress;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import com.kohlschutter.testutil.AvailabilityRequirement;

/**
 * Tests the {@code Socket#getOption(SocketOption)} API available since Java 9.
 *
 * This class (in src/test/java8) is a stub that overrides this type so we can compile for Java 8
 * and, at the same time, acknowledge the absence of the test programmatically in jUnit.
 *
 * @author Christian Kohlschütter
 */
public abstract class StandardSocketOptionsTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected StandardSocketOptionsTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  @AvailabilityRequirement(classes = {"java.lang.ProcessHandle"}, //
      message = "This test requires Java 9 or later")
  public void testUnconnectedServerSocketOptions() throws Exception {
    throw new TestAbortedException();
  }

  @Test
  @AvailabilityRequirement(classes = {"java.lang.ProcessHandle"}, //
      message = "This test requires Java 9 or later")
  public void testSocketOptions() throws Exception {
    throw new TestAbortedException();
  }
}
