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
package org.newsclub.net.unix.tipc;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
public final class SocketOptionsTest extends
    org.newsclub.net.unix.SocketOptionsTest<AFTIPCSocketAddress> {
  public SocketOptionsTest() throws IOException {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Test
  public void testTIPCConnTimeout() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testTIPCImportance() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testTIPCSourceDroppable() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testTIPCDestDroppable() throws Exception {
    assumeTrue(false, "Test not supported in Java 8");
  }

  @Test
  public void testTIPCNodelay() throws Exception {
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
