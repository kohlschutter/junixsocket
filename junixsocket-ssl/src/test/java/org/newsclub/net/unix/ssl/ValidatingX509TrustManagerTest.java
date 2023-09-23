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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;
import static org.newsclub.net.unix.ssl.TestUtil.assertInstanceOf;

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
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.bouncycastle.tls.TlsException;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

// CPD-OFF
public class ValidatingX509TrustManagerTest extends SSLTestBase {

  @Test
  public void testInspectTrustedCertificateExpired() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.p12"),
            () -> "serverpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withTrustManagers((tmf) -> {

          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream(
              "juxclient.truststore")) {
            ks.load(in, "clienttrustpass".toCharArray());
          }
          tmf.init(ks);

          X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
          ValidatingX509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

          // we didn't change them here
          assertArrayEquals(underlyingTrustManager.getAcceptedIssuers(), tm.getAcceptedIssuers());

          return new TrustManager[] {tm};
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> runServerAndClient(addr, serverSocketFactory,
            clientSocketFactory, serverException, clientException));
      });

      assertInstanceOf(serverException.get(), SocketException.class, SSLHandshakeException.class, //
          SSLProtocolException.class, TlsException.class // Bouncycastle
      );
      assertInstanceOf(clientException.get(), SocketException.class, SSLHandshakeException.class, //
          SSLProtocolException.class, TlsException.class // Bouncycastle
      );
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @Test
  public void testInspectTrustedCertificateNotExpired() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    // NOTE: we simply swap client and server certificates here, since only the server certificate
    // is expired

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxclient.p12"),
            () -> "clientpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withTrustManagers((tmf) -> {

          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream(
              "juxserver.truststore")) {
            ks.load(in, "servertrustpass".toCharArray());
          }
          tmf.init(ks);

          X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
          ValidatingX509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

          // we didn't change them here
          assertArrayEquals(underlyingTrustManager.getAcceptedIssuers(), tm.getAcceptedIssuers());

          return new TrustManager[] {tm};
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

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

  @Test
  public void testInspectTrustedCertificateExpiredNested() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.p12"),
            () -> "serverpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withTrustManagers((tmf) -> {

          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream(
              "juxclient.truststore")) {
            ks.load(in, "clienttrustpass".toCharArray());
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

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> runServerAndClient(addr, serverSocketFactory,
            clientSocketFactory, serverException, clientException));
      });

      assertInstanceOf(serverException.get(), SocketException.class, SSLHandshakeException.class, //
          SSLProtocolException.class, TlsException.class // Bouncycastle
      );
      assertInstanceOf(clientException.get(), SocketException.class, SSLHandshakeException.class, //
          SSLProtocolException.class, TlsException.class // Bouncycastle
      );
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @Test
  public void testInspectTrustedCertificateExpiredNestedFilter() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.p12"),
            () -> "serverpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withTrustManagers((tmf) -> {

          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream(
              "juxclient.truststore")) {
            ks.load(in, "clienttrustpass".toCharArray());
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

  @Test
  public void testInspectTrustedClientCertificate() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.p12"),
            () -> "serverpass".toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          p.setNeedClientAuth(true);
        }) //
        .withTrustManagers((tmf) -> {

          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream(
              "juxserver.truststore")) {
            ks.load(in, "servertrustpass".toCharArray());
          }
          tmf.init(ks);

          X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

          X509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

          return new TrustManager[] {tm};
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxclient.p12"),
            () -> "clientpass".toCharArray()) //
        .withTrustStore(ValidatingX509TrustManagerTest.class.getResource("juxclient.truststore"),
            () -> "clienttrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

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

  @Test
  public void testInspectTrustedClientCertificateExpired() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    // NOTE: we simply swap client and server certificates here, since only the server certificate
    // is expired

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxclient.p12"),
            () -> "clientpass".toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          p.setNeedClientAuth(true);
        }) //
        .withTrustManagers((tmf) -> {

          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream(
              "juxclient.truststore")) {
            ks.load(in, "clienttrustpass".toCharArray());
          }
          tmf.init(ks);

          X509TrustManager underlyingTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

          X509TrustManager tm = new ValidatingX509TrustManager(underlyingTrustManager);

          return new TrustManager[] {tm};
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.p12"),
            () -> "serverpass".toCharArray()) //
        .withTrustStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.truststore"),
            () -> "servertrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> runServerAndClient(addr, serverSocketFactory,
            clientSocketFactory, serverException, clientException));
      });

      assertInstanceOf(serverException.get(), SocketException.class, SSLHandshakeException.class, //
          SSLProtocolException.class, TlsException.class // Bouncycastle
      );
      assertInstanceOf(clientException.get(), SocketException.class, SSLHandshakeException.class, //
          SSLProtocolException.class, TlsException.class // Bouncycastle
      );
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @Test
  public void testInspectTrustedClientCertificateExpiredNested() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    // NOTE: we simply swap client and server certificates here, since only the server certificate
    // is expired

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxclient.p12"),
            () -> "clientpass".toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          p.setNeedClientAuth(true);
        }) //
        .withTrustManagers((tmf) -> {

          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream(
              "juxclient.truststore")) {
            ks.load(in, "clienttrustpass".toCharArray());
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

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withKeyStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.p12"),
            () -> "serverpass".toCharArray()) //
        .withTrustStore(ValidatingX509TrustManagerTest.class.getResource("juxserver.truststore"),
            () -> "servertrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

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
