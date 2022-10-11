/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
package org.newsclub.net.unix.vsock;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK)
public final class SocketOptionsTest extends
    org.newsclub.net.unix.SocketOptionsTest<AFVSOCKSocketAddress> {
  public SocketOptionsTest() throws IOException {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  @Test
  public void testVSOCKConnTimeout() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testVSOCKImportance() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testVSOCKSourceDroppable() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testVSOCKDestDroppable() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testVSOCKNodelay() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testGroupJoinLeave() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testGroupLoopback() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testCommunication() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testGroupCommunication() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }
}
