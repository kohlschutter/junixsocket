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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.KnownJavaBugIOException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.AssertUtil;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;
import com.kohlschutter.testutil.TestResourceUtil;

// CPD-OFF
@SuppressFBWarnings("URLCONNECTION_SSRF_FD")
public class ValidatingX509TrustManagerTest extends SSLTestBase {

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testInspectTrustedCertificateExpired(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory;
    SSLSocketFactory clientSocketFactory;

    try {
      serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.p12"), TestCredentials::getJuxServerPassword) //
          .buildAndDestroyBuilder().getSocketFactory();

      clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
          .withTrustManagers((tmf) -> {

            KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
            try (InputStream in = TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
                "juxclient.truststore").openStream()) {
              ks.load(in, TestCredentials.getJuxClientTrustStorePassword());
            }
            tmf.init(ks);

            X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
            ValidatingX509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

            // we didn't change them here
            assertArrayEquals(underlyingTrustManager.getAcceptedIssuers(), tm.getAcceptedIssuers());

            return new TrustManager[] {tm};
          }) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> runServerAndClient(addr, serverSocketFactory,
            clientSocketFactory, serverException, clientException));
      });

      AssertUtil.assertInstanceOf(serverException.get(), SocketException.class,
          SSLHandshakeException.class, //
          SSLProtocolException.class, BOUNCYCASTLE_TLS_EXCEPTION, // Bouncycastle,
          SSLException.class // IBMJSSEProvider2
      );
      AssertUtil.assertInstanceOf(clientException.get(), SocketException.class,
          SSLHandshakeException.class, //
          SSLProtocolException.class, BOUNCYCASTLE_TLS_EXCEPTION, // Bouncycastle,
          SSLException.class // IBMJSSEProvider2
      );
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testInspectTrustedCertificateNotExpired(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    // NOTE: we simply swap client and server certificates here, since only the server certificate
    // is expired

    SSLSocketFactory serverSocketFactory;
    SSLSocketFactory clientSocketFactory;

    try {
      serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxclient.p12"), TestCredentials::getJuxClientPassword) //
          .buildAndDestroyBuilder().getSocketFactory();

      clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
          .withTrustManagers((tmf) -> {

            KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
            try (InputStream in = TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
                "juxserver.truststore").openStream()) {
              ks.load(in, TestCredentials.getJuxServerTrustStorePassword());
            }
            tmf.init(ks);

            X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
            ValidatingX509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

            // we didn't change them here
            assertArrayEquals(underlyingTrustManager.getAcceptedIssuers(), tm.getAcceptedIssuers());

            return new TrustManager[] {tm};
          }) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
            clientException);
      });

      assertNull(serverException.get());
      assertNull(clientException.get());
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testInspectTrustedCertificateExpiredNested(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory;
    SSLSocketFactory clientSocketFactory;

    try {
      serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.p12"), TestCredentials::getJuxServerPassword) //
          .buildAndDestroyBuilder().getSocketFactory();

      clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
          .withTrustManagers((tmf) -> {

            KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
            try (InputStream in = TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
                "juxclient.truststore").openStream()) {
              ks.load(in, TestCredentials.getJuxClientTrustStorePassword());
            }
            tmf.init(ks);

            X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            // nesting two ValidatingX509TrustManager, just to cover onCertificateException
            ValidatingX509TrustManager tm = new ValidatingX509TrustManager(
                new ValidatingX509TrustManager(underlyingTrustManager));

            // we didn't change them here
            assertArrayEquals(underlyingTrustManager.getAcceptedIssuers(), tm.getAcceptedIssuers());

            return new TrustManager[] {tm};
          }) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> runServerAndClient(addr, serverSocketFactory,
            clientSocketFactory, serverException, clientException));
      });

      AssertUtil.assertInstanceOf(serverException.get(), SocketException.class,
          SSLHandshakeException.class, //
          SSLProtocolException.class, BOUNCYCASTLE_TLS_EXCEPTION, // Bouncycastle,
          SSLException.class // IBMJSSEProvider2
      );
      AssertUtil.assertInstanceOf(clientException.get(), SocketException.class,
          SSLHandshakeException.class, //
          SSLProtocolException.class, BOUNCYCASTLE_TLS_EXCEPTION, // Bouncycastle,
          SSLException.class // IBMJSSEProvider2
      );
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testInspectTrustedCertificateExpiredNestedFilter(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory;
    SSLSocketFactory clientSocketFactory;

    try {
      serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.p12"), TestCredentials::getJuxServerPassword) //
          .buildAndDestroyBuilder().getSocketFactory();

      clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
          .withTrustManagers((tmf) -> {

            KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
            try (InputStream in = TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
                "juxclient.truststore").openStream()) {
              ks.load(in, TestCredentials.getJuxClientTrustStorePassword());
            }
            tmf.init(ks);

            X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            // nesting two FilterX509TrustManager, just to cover onCertificateException
            X509TrustManager tm = new FilterX509TrustManager(new ValidatingX509TrustManager(
                underlyingTrustManager)) {

              @Override
              protected void onCertificateException(boolean checkClient, CertificateException e,
                  X509Certificate[] chain, String authType) throws CertificateException {
                // swallow exception
                assertFalse(checkClient); // we're checking the server certificate
              }

              @Override
              protected void onCertificateTrusted(boolean checkClient, X509Certificate[] chain,
                  String authType) throws CertificateException {
                fail("unexpected");
              }
            };

            // we didn't change them here
            assertArrayEquals(underlyingTrustManager.getAcceptedIssuers(), tm.getAcceptedIssuers());

            return new TrustManager[] {tm};
          }) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
            clientException);
      });
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testInspectTrustedClientCertificate(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory;
    SSLSocketFactory clientSocketFactory;

    try {
      serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.p12"), TestCredentials::getJuxServerPassword) //
          .withDefaultSSLParameters((p) -> {
            p.setNeedClientAuth(true);
          }) //
          .withTrustManagers((tmf) -> {

            KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
            try (InputStream in = TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
                "juxserver.truststore").openStream()) {
              ks.load(in, TestCredentials.getJuxServerTrustStorePassword());
            }
            tmf.init(ks);

            X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            X509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

            return new TrustManager[] {tm};
          }) //
          .buildAndDestroyBuilder().getSocketFactory();

      clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxclient.p12"), TestCredentials::getJuxClientPassword) //
          .withTrustStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxclient.truststore"), TestCredentials::getJuxClientTrustStorePassword) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
            clientException);
      });

      assertNull(serverException.get());
      assertNull(clientException.get());
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testInspectTrustedClientCertificateExpired(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    // NOTE: we simply swap client and server certificates here, since only the server certificate
    // is expired

    SSLSocketFactory serverSocketFactory;
    SSLSocketFactory clientSocketFactory;

    try {
      serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxclient.p12"), TestCredentials::getJuxClientPassword) //
          .withDefaultSSLParameters((p) -> {
            p.setNeedClientAuth(true);
          }) //
          .withTrustManagers((tmf) -> {

            KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
            try (InputStream in = TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
                "juxclient.truststore").openStream()) {
              ks.load(in, TestCredentials.getJuxClientTrustStorePassword());
            }
            tmf.init(ks);

            X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            X509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

            return new TrustManager[] {tm};
          }) //
          .buildAndDestroyBuilder().getSocketFactory();

      clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.p12"), TestCredentials::getJuxServerPassword) //
          .withTrustStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.truststore"), TestCredentials::getJuxServerTrustStorePassword) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> runServerAndClient(addr, serverSocketFactory,
            clientSocketFactory, serverException, clientException));
      });

      AssertUtil.assertInstanceOf(serverException.get(), SocketException.class,
          SSLHandshakeException.class, //
          SSLProtocolException.class, BOUNCYCASTLE_TLS_EXCEPTION, // Bouncycastle,
          SSLException.class // IBMJSSEProvider2
      );
      AssertUtil.assertInstanceOf(clientException.get(), SocketException.class,
          SSLHandshakeException.class, //
          SSLProtocolException.class, BOUNCYCASTLE_TLS_EXCEPTION, // Bouncycastle,
          SSLException.class // IBMJSSEProvider2
      );
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testInspectTrustedClientCertificateExpiredNested(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    // NOTE: we simply swap client and server certificates here, since only the server certificate
    // is expired

    SSLSocketFactory serverSocketFactory;
    SSLSocketFactory clientSocketFactory;

    try {
      serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxclient.p12"), TestCredentials::getJuxClientPassword) //
          .withDefaultSSLParameters((p) -> {
            p.setNeedClientAuth(true);
          }) //
          .withTrustManagers((tmf) -> {

            KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
            try (InputStream in = TestResourceUtil.getRequiredResource(SSLContextBuilderTest.class,
                "juxclient.truststore").openStream()) {
              ks.load(in, TestCredentials.getJuxClientTrustStorePassword());
            }
            tmf.init(ks);

            X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            X509TrustManager tm = new FilterX509TrustManager(new ValidatingX509TrustManager(
                underlyingTrustManager)) {

              @Override
              protected void onCertificateTrusted(boolean checkClient, X509Certificate[] chain,
                  String authType) throws CertificateException {
                // that's good (but unexpected)
                fail("unexpected");
              }

              @Override
              protected void onCertificateException(boolean checkClient, CertificateException e,
                  X509Certificate[] chain, String authType) throws CertificateException {
                // still accept certificate
              }
            };

            return new TrustManager[] {tm};
          }) //
          .buildAndDestroyBuilder().getSocketFactory();

      clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
          .withKeyStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.p12"), TestCredentials::getJuxServerPassword) //
          .withTrustStore(TestResourceUtil.getRequiredResource(ValidatingX509TrustManagerTest.class,
              "juxserver.truststore"), TestCredentials::getJuxServerTrustStorePassword) //
          .buildAndDestroyBuilder().getSocketFactory();
    } catch (KnownJavaBugIOException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          e.getMessage(), e);
    }

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
            clientException);
      });

      assertNull(serverException.get());
      assertNull(clientException.get());
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  private void runServerAndClient(AFUNIXSocketAddress addr, SSLSocketFactory serverSocketFactory,
      SSLSocketFactory clientSocketFactory, CompletableFuture<Exception> serverException,
      CompletableFuture<Exception> clientException) throws Exception {

    TestingAFSocketServer<AFUNIXSocketAddress> server =
        new TestingAFSocketServer<AFUNIXSocketAddress>(addr) {
          @Override
          protected void doServeSocket(AFSocket<? extends AFUNIXSocketAddress> plainSocket)
              throws IOException {
            Exception caught = null;
            try (SSLSocket sslSocket = (SSLSocket) serverSocketFactory.createSocket(plainSocket,
                "localhost.junixsocket", plainSocket.getPort(), false)) {

              try (InputStream in = sslSocket.getInputStream();
                  OutputStream out = sslSocket.getOutputStream();) {

                int v = in.read();
                assertEquals('!', v);

                out.write("Hello World".getBytes(StandardCharsets.UTF_8));
                out.flush();
              }
            } catch (Exception e) {
              caught = e;
            } finally {
              serverException.complete(caught);
            }
          }
        };
    try {
      server.startAndWaitToBecomeReady();

      SNIServerName hostname = new SNIHostName("subdomain.example.com");

      Exception caught = null;
      try (AFUNIXSocket plainSocket = AFUNIXSocket.connectTo(addr);
          SSLSocket sslSocket = (SSLSocket) clientSocketFactory.createSocket(plainSocket,
              "localhost.junixsocket", plainSocket.getPort(), false)) {

        SSLParametersUtil.setSNIServerName(sslSocket, hostname);

        sslSocket.startHandshake();

        try (InputStream in = sslSocket.getInputStream();
            OutputStream out = sslSocket.getOutputStream()) {
          out.write('!');
          out.flush();

          byte[] by = new byte[11];
          int offset = 0;
          int r;
          while (offset < by.length && (r = in.read(by, offset, by.length - offset)) >= 0) {
            offset += r;
          }
          assertEquals("Hello World", new String(by, 0, offset, StandardCharsets.UTF_8));
        }
      } catch (SocketException e) {
        caught = e;
      } catch (Exception e) {
        caught = e;
        throw e;
      } finally {
        clientException.complete(caught);
      }
    } finally {
      server.stop();

      TestUtil.throwMoreInterestingThrowableThanSocketException(() -> serverException.getNow(null),
          () -> clientException.getNow(null));

      server.checkThrowable();
    }
  }
}
