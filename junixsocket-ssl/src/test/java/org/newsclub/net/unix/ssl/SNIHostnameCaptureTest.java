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
package org.newsclub.net.unix.ssl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Provider;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.KnownJavaBugIOException;
import org.opentest4j.AssertionFailedError;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;
import com.kohlschutter.testutil.TestResourceUtil;

public class SNIHostnameCaptureTest extends SSLTestBase {
  private static SSLSocketFactory initClientSocketFactory(TestSSLConfiguration configuration)
      throws Exception {
    try {
      return configuration.configure(SSLContextBuilder.forClient()) //
          .withTrustStore(TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
              "juxclient.truststore"), "clienttrustpass"::toCharArray) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_NO_serverNullDefault_NO_clientEmptyDefault_NO(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, false, false, false);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_NO_serverNullDefault_YES_clientEmptyDefault_NO(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, false, true, false);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_NO_serverNullDefault_NO_clientEmptyDefault_YES(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, false, false, true);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_NO_serverNullDefault_YES_clientEmptyDefault_YES(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, false, true, true);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_YES_serverNullDefault_NO_clientEmptyDefault_NO(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, true, false, false);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_YES_serverNullDefault_YES_clientEmptyDefault_NO(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, true, true, false);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_YES_serverNullDefault_NO_clientEmptyDefault_YES(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, true, false, true);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressWarnings("PMD.MethodNamingConventions")
  public void testSNISuccessExpectDefault_YES_serverNullDefault_YES_clientEmptyDefault_YES(
      TestSSLConfiguration configuration) throws Exception {
    testSNISuccess(configuration, true, true, true);
  }

  private void testSNISuccess(TestSSLConfiguration configuration, boolean expectDefault,
      boolean serverNullDefaultHostname, boolean clientEmptyDefaultHostname) throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    try {
      SSLSocketFactory serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
              "juxserver.p12"), "serverpass"::toCharArray) //
          .buildAndDestroyBuilder().getSocketFactory();

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        runServerAndClient(configuration, addr, serverSocketFactory, expectDefault,
            serverNullDefaultHostname, clientEmptyDefaultHostname);
      });
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    } catch (Exception | Error e) {
      throw configuration.handleException(e);
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  private static boolean isBouncyCastleJSSE(SSLSocketFactory socketFactory) {
    SSLContext context;
    if (socketFactory instanceof BuilderSSLSocketFactory) {
      context = ((BuilderSSLSocketFactory) socketFactory).getContext();
    } else {
      context = null;
    }
    Provider p;
    if (context != null && (p = context.getProvider()) != null && "BCJSSE".equals(p.getName())) {
      return true;
    } else {
      return false;
    }
  }

  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NcssCount", "PMD.NPathComplexity"})
  private void runServerAndClient(TestSSLConfiguration configuration, AFUNIXSocketAddress addr,
      SSLSocketFactory serverSocketFactory, boolean expectDefault,
      boolean serverNullDefaultHostname, boolean clientEmptyDefaultHostname) throws Exception {

    CompletableFuture<Throwable> failureServer = new CompletableFuture<>();
    CompletableFuture<Throwable> failureClient = new CompletableFuture<>();

    // We cannot reuse SSLContext due to a potential Java bug, see
    // https://github.com/kohlschuetter/snisslbug
    SSLSocketFactory clientSocketFactory = initClientSocketFactory(configuration);

    AtomicBoolean gotHostnameInTime = new AtomicBoolean(false);

    TestingAFSocketServer<AFUNIXSocketAddress> server =
        new TestingAFSocketServer<AFUNIXSocketAddress>(addr) {

          @SuppressWarnings("PMD.CyclomaticComplexity")
          @Override
          protected void doServeSocket(AFSocket<? extends AFUNIXSocketAddress> plainSocket)
              throws IOException {

            boolean sniBroken = false;

            try (SSLSocket sslSocket = (SSLSocket) serverSocketFactory.createSocket(plainSocket,
                "localhost.junixsocket", plainSocket.getPort(), false)) {

              SNIHostnameCapture hnc = serverNullDefaultHostname ? //
                  SNIHostnameCapture.configure(sslSocket, SNIHostnameCapture.ACCEPT_ANY_HOSTNAME) : //
                  SNIHostnameCapture.configure(sslSocket, SNIHostnameCapture.ACCEPT_ANY_HOSTNAME,
                      () -> "defaulthost");

              assertFalse(hnc.isComplete());
              assertThrows(IllegalStateException.class, hnc::getHostname);

              // NOTE: starting a handshake doesn't mean it's completed before reading the 1st byte!
              sslSocket.startHandshake();

              hnc.isComplete(1, TimeUnit.SECONDS);
              gotHostnameInTime.set(hnc.isComplete());

              try {
                if (expectDefault) {
                  if (serverNullDefaultHostname && clientEmptyDefaultHostname) {
                    assertNull(hnc.getHostname());
                  } else if (clientEmptyDefaultHostname) {
                    assertEquals("defaulthost", hnc.getHostname());
                  } else {
                    assertEquals("localhost.junixsocket", hnc.getHostname());
                  }
                } else {
                  if ("defaulthost".equals(hnc.getHostname()) && AFSocket.isRunningOnAndroid()) {
                    // This is a known edge case.
                    failureServer.complete(new TestAbortedWithImportantMessageException(
                        MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
                        "Android seems to not properly send the SNI hostname request"));
                  } else {
                    assertEquals("subdomain.example.com", hnc.getHostname());
                  }
                }
              } catch (AssertionFailedError e) {
                if (configuration.isSniBroken()) {
                  sniBroken = true;
                } else {
                  throw e;
                }
              }

              try (InputStream in = sslSocket.getInputStream();
                  OutputStream out = sslSocket.getOutputStream();) {
                int v = in.read();
                assertEquals('?', v);
                if (!hnc.isComplete()) {
                  fail("Handshake is not marked complete, but data can be read");
                }

                CompletableFuture<UnsupportedOperationException> cf =
                    new CompletableFuture<UnsupportedOperationException>();
                try {
                  assertEquals(hnc.getHostnameFromSSLSession(sslSocket, cf::complete), hnc
                      .getHostname());
                } catch (AssertionFailedError e) {
                  if (cf.isDone() && isBouncyCastleJSSE(serverSocketFactory)) {
                    // BouncyCastle doesn't support this
                  } else {
                    throw e;
                  }
                }

                assertEquals(expectDefault ? 1 : 0, in.read());
                assertEquals(serverNullDefaultHostname ? 1 : 0, in.read());
                assertEquals(clientEmptyDefaultHostname ? 1 : 0, in.read());

                out.write("Hello World".getBytes(StandardCharsets.UTF_8));
                out.flush();
              }
              if (!gotHostnameInTime.get()) {
                fail("Handshake was not marked complete in time, but eventually was");
              }
              if (sniBroken) {
                throw new TestAbortedWithImportantMessageException(
                    MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
                    "SNI support is knowingly incomplete for " + configuration);
              }
            } catch (Exception | Error e) { // NOPMD.ExceptionAsFlowControl
              failureServer.complete(e);
            }
            if (!gotHostnameInTime.get()) {
              failureServer.complete(new AssertionFailedError(
                  "Handshake was not marked complete in time"));
            }

          }
        };

    try {
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
          int offset = 0;
          int r;
          while (offset < by.length && (r = in.read(by, offset, by.length - offset)) >= 0) {
            offset += r;
          }
          assertEquals("Hello World", new String(by, 0, offset, StandardCharsets.UTF_8));
        } catch (Exception e) {
          failureClient.complete(e);
        }
      }
    } finally {
      server.stop();

      TestUtil.throwMoreInterestingThrowableThanSocketException(() -> failureServer.getNow(null),
          () -> failureClient.getNow(null));

      server.checkThrowable();
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
