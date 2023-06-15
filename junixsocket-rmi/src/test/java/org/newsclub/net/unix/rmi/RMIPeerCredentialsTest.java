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
package org.newsclub.net.unix.rmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.rmi.NotBoundException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFUNIXSocketCredentials;

/**
 * Verifies that peer credentials are properly set when communicating over RMI.
 *
 * @author Christian Kohlschütter
 */
@AFSocketCapabilityRequirement({
    AFSocketCapability.CAPABILITY_UNIX_DOMAIN, AFSocketCapability.CAPABILITY_PEER_CREDENTIALS})
public class RMIPeerCredentialsTest extends TestBase {
  public RMIPeerCredentialsTest() throws IOException {
    super();
  }

  @Test
  public void testRemotePeerCredentials() throws NotBoundException, IOException {
    TestService svc = lookupTestService();

    // CPD-OFF
    AFUNIXSocketCredentials clientCreds = svc.remotePeerCredentials();
    AFUNIXSocketCredentials serverCreds = RemotePeerInfo.remotePeerCredentials(svc);

    if (serverCreds != AFUNIXSocketCredentials.SAME_PROCESS) { // NOPMD
      assertEquals(clientCreds, serverCreds,
          "Since our tests run in the same process, the peer credentials must be identical");
      assertEquals(clientCreds.toString(), serverCreds.toString(),
          "Since our tests run in the same process, the peer credentials must be identical");
      assertEquals(clientCreds.getGid(), serverCreds.getGid(),
          "Since our tests run in the same process, the peer credentials must be identical");
      assertArrayEquals(clientCreds.getGids(), serverCreds.getGids(),
          "Since our tests run in the same process, the peer credentials must be identical");
      assertEquals(clientCreds.getUid(), serverCreds.getUid(),
          "Since our tests run in the same process, the peer credentials must be identical");
      assertEquals(clientCreds.getPid(), serverCreds.getPid(),
          "Since our tests run in the same process, the peer credentials must be identical");
      assertEquals(clientCreds.getUUID(), serverCreds.getUUID(),
          "Since our tests run in the same process, the peer credentials must be identical");
    }
    // CPD-ON

    assertNull(AFUNIXSocketCredentials.remotePeerCredentials(),
        "AFUNIXSocketCredentials.remotePeerCredentials should be null when called outside of an RMI service");
  }
}
