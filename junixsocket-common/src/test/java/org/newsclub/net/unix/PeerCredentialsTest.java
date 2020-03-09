/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Verifies that peer credentials are properly set.
 * 
 * @author Christian Kohlschütter
 */
@AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_PEER_CREDENTIALS)
public class PeerCredentialsTest extends SocketTestBase {
  public PeerCredentialsTest() throws IOException {
    super();
  }

  @Test
  public void testSameProcess() throws Exception {
    assertTimeout(Duration.ofSeconds(2), () -> {
      final CompletableFuture<AFUNIXSocketCredentials> clientCredsFuture =
          new CompletableFuture<>();

      final ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          AFUNIXSocket socket = (AFUNIXSocket) sock;
          AFUNIXSocketCredentials clientCreds = socket.getPeerCredentials();
          clientCredsFuture.complete(clientCreds);

          stopAcceptingConnections();
        }
      };

      try (AFUNIXSocket socket = connectToServer()) {
        AFUNIXSocketCredentials serverCreds = socket.getPeerCredentials();
        AFUNIXSocketCredentials clientCreds = clientCredsFuture.get();

        assertEquals(clientCreds, serverCreds,
            "Since our tests run in the same process, the peer credentials must be identical");

        if (clientCreds.getPid() == -1) {
          // PID information is unvailable on this platform
        } else {
          assertEquals(/* ProcessHandle.current().pid() */ TestUtils.getPid(), clientCreds.getPid(),
              "The returned PID must be the one of our process");
        }
      }

      serverThread.checkException();
    });
  }
}
