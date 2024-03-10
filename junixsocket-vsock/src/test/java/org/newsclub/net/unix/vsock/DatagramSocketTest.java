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
package org.newsclub.net.unix.vsock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement({
    AFSocketCapability.CAPABILITY_VSOCK, AFSocketCapability.CAPABILITY_VSOCK_DGRAM})
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class DatagramSocketTest extends
    org.newsclub.net.unix.DatagramSocketTest<AFVSOCKSocketAddress> {

  public DatagramSocketTest() {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  @Override
  protected void assertUnconnectedUnbound(DatagramSocket ds) {
    if (ds.isBound()) {
      // permittable
      // ds.getLocalAddress().isAnyLocalAddress() -- not applicable (wrongly false)
      AFVSOCKSocketAddress sa = (AFVSOCKSocketAddress) ds.getLocalSocketAddress();
      assertNotNull(sa);
      try {
        assertEquals(AFVSOCKSocketAddress.ofAnyPort(), sa);
      } catch (SocketException e) {
        fail(e);
      }
    }
  }

  @Override
  protected void assertExpectedSocketAddressFromDatagramChannelReceive(SocketAddress expected,
      SocketAddress received) {
    // ignore for VSOCK
  }
}
