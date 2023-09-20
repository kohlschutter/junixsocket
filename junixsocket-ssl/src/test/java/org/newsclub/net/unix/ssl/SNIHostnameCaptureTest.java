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
package org.newsclub.net.unix.ssl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.time.Duration;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.DestroyFailedException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.server.AFSocketServer;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

public class SNIHostnameCaptureTest {
  private static SSLSocketFactory initClientSocketFactory() throws GeneralSecurityException,
      IOException, DestroyFailedException {
    return SSLContextBuilder.forClient() //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxclient.truststore"),
            () -> "clienttrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();
  }

  @Test
  public void testSNISuccess() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = initClientSocketFactory();

    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, false, false, false));
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, false, true, false));
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, false, false, true));
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, false, true, true));
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, true, false, false));
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, true, false, true));
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, true, true, false));
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, true, true, true));
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @SuppressWarnings("PMD.CognitiveComplexity")
  private void runServerAndClient(AFUNIXSocketAddress addr, SSLSocketFactory serverSocketFactory,
      SSLSocketFactory clientSocketFactory, boolean expectDefault,
      boolean serverNullDefaultHostname, boolean clientEmptyDefaultHostname) throws Exception {
    // We cannot reuse SSLContext due to a potential Java bug, see
    // https://github.com/kohlschuetter/snisslbug
    clientSocketFactory = initClientSocketFactory();

    AFSocketServer<AFUNIXSocketAddress> server = new TestingAFSocketServer<AFUNIXSocketAddress>(
        addr) {

      @Override
      protected void doServeSocket(AFSocket<? extends AFUNIXSocketAddress> plainSocket)
          throws IOException {
        try (SSLSocket sslSocket = (SSLSocket) serverSocketFactory.createSocket(plainSocket,
            "localhost.junixsocket", plainSocket.getPort(), false)) {

          SNIHostnameCapture hnc = serverNullDefaultHostname ? //
              SNIHostnameCapture.configure(sslSocket, SNIHostnameCapture.ACCEPT_ANY_HOSTNAME) : //
              SNIHostnameCapture.configure(sslSocket, SNIHostnameCapture.ACCEPT_ANY_HOSTNAME,
                  () -> "defaulthost");

          assertFalse(hnc.isComplete());
          assertThrows(IllegalStateException.class, () -> hnc.getHostname());

          // make sure handshake is completed, otherwise we have to wait until the first read/write
          sslSocket.startHandshake();

          assertTrue(hnc.isComplete());

          if (expectDefault) {
            if (serverNullDefaultHostname && clientEmptyDefaultHostname) {
              assertNull(hnc.getHostname());
            } else if (clientEmptyDefaultHostname) {
              assertEquals("defaulthost", hnc.getHostname());
            } else {
              assertEquals("localhost.junixsocket", hnc.getHostname());
            }
          } else {
            assertEquals("subdomain.example.com", hnc.getHostname());
          }

          try (InputStream in = sslSocket.getInputStream();
              OutputStream out = sslSocket.getOutputStream();) {

            int v = in.read();
            assertEquals('?', v);

            assertEquals(expectDefault ? 1 : 0, in.read());
            assertEquals(serverNullDefaultHostname ? 1 : 0, in.read());
            assertEquals(clientEmptyDefaultHostname ? 1 : 0, in.read());

            out.write("Hello World".getBytes(StandardCharsets.UTF_8));
            out.flush();
            stop();
          }
        } catch (Exception | Error e) {
          e.printStackTrace();
          throw e;
        }
      }
    };
    server.startAndWaitToBecomeReady();

    try (AFUNIXSocket plainSocket = AFUNIXSocket.connectTo(addr);
        SSLSocket sslSocket = (SSLSocket) clientSocketFactory.createSocket(plainSocket,
            clientEmptyDefaultHostname ? "" : "localhost.junixsocket", plainSocket.getPort(),
            false)) {

      if (!expectDefault) {
        SSLParametersUtil.setSNIServerName(sslSocket, new SNIHostName("subdomain.example.com"));
      }

      try (InputStream in = sslSocket.getInputStream();
          OutputStream out = sslSocket.getOutputStream()) {
        out.write('?');

        out.write(expectDefault ? 1 : 0);
        out.write(serverNullDefaultHostname ? 1 : 0);
        out.write(clientEmptyDefaultHostname ? 1 : 0);

        out.flush();

        byte[] by = new byte[11];
        int r = in.read(by);
        assertEquals("Hello World", new String(by, 0, r, StandardCharsets.UTF_8));
      }
    }
  }

  /**
   * Test unexpected {@link SSLSocket} behavior. Mostly for code coverage.
   *
   * @throws Exception on error.
   */
  @Test
  public void testWonkySSLSocket() throws Exception {
    testWonkySSLSocket(0);
    testWonkySSLSocket(23);
  }

  private void testWonkySSLSocket(int matchType) throws Exception {
    SSLSocket sslSocket = new SSLSocket() {
      private HandshakeCompletedListener hcl;
      private SSLParameters params = new SSLParameters();

      @Override
      @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
      public void startHandshake() throws IOException {
        for (SNIMatcher sm : getSSLParameters().getSNIMatchers()) {
          sm.matches(new SNIHostName("hostname"));
          sm.matches(new SNIServerName(23, "WAT".getBytes(StandardCharsets.US_ASCII)) {

          });
          sm.matches(new SNIHostName("hostname"));
          sm.matches(new SNIHostName("anotherHostname"));
        }
        hcl.handshakeCompleted(new HandshakeCompletedEvent(this, null));
      }

      @Override
      public void setWantClientAuth(boolean want) {
      }

      @Override
      public void setUseClientMode(boolean mode) {
      }

      @Override
      public void setNeedClientAuth(boolean need) {
      }

      @Override
      public void setEnabledProtocols(String[] protocols) {
      }

      @Override
      public void setEnabledCipherSuites(String[] suites) {
      }

      @Override
      public void setEnableSessionCreation(boolean flag) {
      }

      @Override
      public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
      }

      @Override
      public boolean getWantClientAuth() {
        return false;
      }

      @Override
      public boolean getUseClientMode() {
        return false;
      }

      @Override
      public String[] getSupportedProtocols() {
        return null; // NOPMD
      }

      @Override
      public String[] getSupportedCipherSuites() {
        return null; // NOPMD
      }

      @Override
      public SSLSession getSession() {
        return null;
      }

      @Override
      public boolean getNeedClientAuth() {
        return false;
      }

      @Override
      public String[] getEnabledProtocols() {
        return null; // NOPMD
      }

      @Override
      public String[] getEnabledCipherSuites() {
        return null; // NOPMD
      }

      @Override
      public boolean getEnableSessionCreation() {
        return false;
      }

      @Override
      public SSLParameters getSSLParameters() {
        return params;
      }

      @Override
      public void setSSLParameters(SSLParameters params) {
        this.params = params;
      }

      @Override
      public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        if (this.hcl != null) {
          throw new IllegalStateException();
        }
        this.hcl = listener;
      }
    };

    SNIHostnameCapture shc = SNIHostnameCapture.configure(sslSocket, new SNIMatcher(matchType) {
      @Override
      public boolean matches(SNIServerName serverName) {
        return serverName.getType() == matchType;
      }
    });
    sslSocket.startHandshake();

    if (matchType == 0) {
      assertEquals("hostname", shc.getHostname());
    } else {
      assertNull(shc.getHostname());
    }
  }
}
