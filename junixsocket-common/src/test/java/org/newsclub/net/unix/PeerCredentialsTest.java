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
package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.kohlschutter.util.ProcessUtil;

/**
 * Verifies that peer credentials are properly set.
 * 
 * @author Christian Kohlschütter
 */
@AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_PEER_CREDENTIALS)
public class PeerCredentialsTest extends SocketTestBase {
  private static AFUNIXSocketCredentials credsSockets = null;
  private static AFUNIXSocketCredentials credsDatagramSockets = null;

  @Test
  public void testSocketsSameProcess() throws Exception {
    assertTimeout(Duration.ofSeconds(10), () -> {
      final CompletableFuture<AFUNIXSocketCredentials> clientCredsFuture =
          new CompletableFuture<>();

      Semaphore sema = new Semaphore(0);
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          AFUNIXSocketCredentials clientCreds = socket.getPeerCredentials();
          clientCredsFuture.complete(clientCreds);
          try {
            sema.acquire();
          } catch (InterruptedException e) {
            // ignore
          }
        }
      }; AFUNIXSocket socket = connectToServer()) {
        try (InputStream in = socket.getInputStream()) {
          AFUNIXSocketCredentials serverCreds = socket.getPeerCredentials();
          AFUNIXSocketCredentials clientCreds = clientCredsFuture.get();

          sema.release();

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

          if (clientCreds.getPid() == -1) {
            // PID information is unvailable on this platform
          } else {
            assertEquals(ProcessUtil.getPid(), clientCreds.getPid(),
                "The returned PID must be the one of our process");
          }

          System.out.println(" (streams) : " + checkCredentialFeatures(clientCreds));
          setCredsSockets(clientCreds);
        }
      }
    });
  }

  private static String checkCredentialFeatures(AFUNIXSocketCredentials creds) {
    StringBuilder sb = new StringBuilder();
    if (creds.getPid() != -1) {
      sb.append("[pid] ");
    } else {
      sb.append(" pid  ");
    }
    if (creds.getUid() != -1) {
      sb.append("[uid] ");
    } else {
      sb.append(" uid  ");
    }
    if (creds.getGid() != -1) {
      sb.append("[gid] ");
    } else {
      sb.append(" gid  ");
    }
    if (creds.getGids() != null && creds.getGids().length > 0) {
      sb.append("[additional gids] ");
    } else {
      sb.append(" additional gids  ");
    }
    if (creds.getUUID() != null) {
      sb.append("[uuid] ");
    } else {
      sb.append(" uuid  ");
    }
    sb.setLength(sb.length() - 1);

    return sb.toString();
  }

  @Test
  public void testDatagramSocket() throws Exception {
    AFUNIXSocketAddress ds1Addr = AFUNIXSocketAddress.of(newTempFile());
    AFUNIXSocketAddress ds2Addr = AFUNIXSocketAddress.of(newTempFile());

    try (AFUNIXDatagramSocket ds1 = AFUNIXDatagramSocket.newInstance();
        AFUNIXDatagramSocket ds2 = AFUNIXDatagramSocket.newInstance();) {

      ds1.bind(ds1Addr);
      ds2.bind(ds2Addr);
      ds1.connect(ds2Addr);
      ds2.connect(ds1Addr);

      DatagramPacket dp = AFUNIXDatagramUtil.datagramWithCapacityAndPayload("Hello".getBytes(
          StandardCharsets.UTF_8));
      ds2.send(dp);
      dp = AFUNIXDatagramUtil.datagramWithCapacity(1024);

      ds1.receive(dp);
      AFUNIXSocketCredentials pc1 = ds1.getPeerCredentials();

      AFUNIXSocketCredentials pc2 = ds2.getPeerCredentials();

      assertEquals(pc1, pc2);
      System.out.println("(datagrams): " + checkCredentialFeatures(pc1));
      setCredsDatagramSockets(pc1);
    }
  }

  private static void setCredsSockets(AFUNIXSocketCredentials creds) {
    credsSockets = creds;
  }

  private static void setCredsDatagramSockets(AFUNIXSocketCredentials creds) {
    credsDatagramSockets = creds;
  }

  @AfterAll
  public static void ensureSameCreds() {
    if (credsSockets != null && credsDatagramSockets != null) {
      assertEquals(credsSockets, credsDatagramSockets,
          "The credentials received via Socket and via DatagramSocket should be the same");
    }
  }
}
