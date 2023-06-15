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
package org.newsclub.net.unix.vsock;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class ThroughputTest extends
    org.newsclub.net.unix.ThroughputTest<AFVSOCKSocketAddress> {

  public ThroughputTest() {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  @Override
  protected String stbTestType() {
    return "AF_VSOCK";
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK_DGRAM)
  @Override
  public void testDatagramPacket() throws Exception {
    super.testDatagramPacket();
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK_DGRAM)
  @Override
  public void testDatagramChannel() throws Exception {
    super.testDatagramChannel();
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK_DGRAM)
  @Override
  public void testDatagramChannelDirect() throws Exception {
    super.testDatagramChannelDirect();
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK_DGRAM)
  @Override
  public void testDatagramChannelNonBlocking() throws Exception {
    super.testDatagramChannelNonBlocking();
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK_DGRAM)
  @Override
  public void testDatagramChannelNonBlockingDirect() throws Exception {
    super.testDatagramChannelNonBlockingDirect();
  }
}
