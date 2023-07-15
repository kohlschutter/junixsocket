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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AddressSpecifics;

import com.kohlschutter.testutil.AvailabilityRequirement;

/**
 * Shim class to allow for some tests that test code that is only available in newer Java versions,
 * particularly JEP 380-related code (UnixDomainSocketAddress).
 *
 * @author Christian Kohlschütter
 */
abstract class ThroughputTestShim extends
    org.newsclub.net.unix.ThroughputTest<AFUNIXSocketAddress> {

  protected ThroughputTestShim(AddressSpecifics<AFUNIXSocketAddress> asp) {
    super(asp);
  }

  @Test
  @AvailabilityRequirement(classes = "java.net.UnixDomainSocketAddress", //
      message = "This test requires Java 16 or later")
  public void testJEP380() throws Exception {
    fail("Should not be reached");
  }

  @Test
  @AvailabilityRequirement(classes = "java.net.UnixDomainSocketAddress", //
      message = "This test requires Java 16 or later")
  public void testJEP380direct() throws Exception {
    fail("Should not be reached");
  }
}
