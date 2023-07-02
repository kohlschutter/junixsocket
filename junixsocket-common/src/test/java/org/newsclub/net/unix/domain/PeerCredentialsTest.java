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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFDatagramUtil;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXDatagramSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketCredentials;
import org.newsclub.net.unix.SocketTestBase;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.util.ProcessUtil;

/**
 * Verifies that peer credentials are properly set.
 *
 * @author Christian Kohlschütter
 */
@AFSocketCapabilityRequirement({
    AFSocketCapability.CAPABILITY_UNIX_DOMAIN, AFSocketCapability.CAPABILITY_PEER_CREDENTIALS})
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public final class PeerCredentialsTest extends SocketTestBase<AFUNIXSocketAddress> {
  private static AFUNIXSocketCredentials credsSockets = null;
  private static AFUNIXSocketCredentials credsDatagramSockets = null;

  public PeerCredentialsTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  public void testSocketsSameProcess() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      final CompletableFuture<AFUNIXSocketCredentials> clientCredsFuture =
          new CompletableFuture<>();

      Semaphore sema = new Semaphore(0);
      try (AFUNIXServerThread serverThread = new AFUNIXServerThread() {

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
      }; AFUNIXSocket socket = (AFUNIXSocket) connectTo(serverThread.getServerAddress())) {
        try (InputStream unused = socket.getInputStream()) {
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

          setCredsSockets(clientCreds);
        }
      }
    });
  }

  private static void checkCredentialFeatures(StringBuilder sbYes, StringBuilder sbNo, String key,
      boolean supported) {
    ((supported) ? sbYes : sbNo).append(' ').append(key);
  }

  private static void checkCredentialFeatures(AFUNIXSocketCredentials creds) {
    StringBuilder sbYes = new StringBuilder(32);
    StringBuilder sbNo = new StringBuilder(32);

    checkCredentialFeatures(sbYes, sbNo, "pid", (creds.getPid() > 0));
    checkCredentialFeatures(sbYes, sbNo, "uid", (creds.getUid() != -1));
    checkCredentialFeatures(sbYes, sbNo, "gid", (creds.getGid() != -1));
    checkCredentialFeatures(sbYes, sbNo, "additional_gids", (creds.getGids() != null && creds
        .getGids().length > 0));
    checkCredentialFeatures(sbYes, sbNo, "uuid", (creds.getUUID() != null));

    System.out.print("Supported credentials:  ");
    if (sbYes.length() == 0) {
      System.out.println(" (none)");
    } else {
      System.out.println(sbYes);
    }
    System.out.print("Unsupported credentials:");
    if (sbNo.length() == 0) {
      System.out.println(" (none)");
    } else {
      System.out.println(sbNo);
    }
  }

  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
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

      DatagramPacket dp = AFDatagramUtil.datagramWithCapacityAndPayload("Hello".getBytes(
          StandardCharsets.UTF_8));
      ds2.send(dp);
      dp = AFDatagramUtil.datagramWithCapacity(1024);

      ds1.receive(dp);
      AFUNIXSocketCredentials pc1 = ds1.getPeerCredentials();

      AFUNIXSocketCredentials pc2 = ds2.getPeerCredentials();

      assertEquals(pc1, pc2);
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
    if (credsSockets != null) {
      checkCredentialFeatures(credsSockets);
      if (credsDatagramSockets != null) {
        if (credsDatagramSockets.isEmpty() && !credsSockets.isEmpty()) {
          System.out.println("WARNING: No peer credentials for datagram sockets");
        } else {
          assertEquals(credsSockets, credsDatagramSockets,
              "The credentials received via Socket and via DatagramSocket should be the same");
        }
      }
    }
  }
}
