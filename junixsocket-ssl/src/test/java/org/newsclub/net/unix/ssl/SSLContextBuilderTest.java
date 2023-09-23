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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.newsclub.net.unix.ssl.TestUtil.assertInstanceOf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketPair;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.ExecutionEnvironmentRequirement;
import com.kohlschutter.testutil.ExecutionEnvironmentRequirement.Rule;

// CPD-OFF
@SuppressWarnings("PMD.ExcessiveImports")
public class SSLContextBuilderTest extends SSLTestBase {

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testNoClientAuth(TestSSLConfiguration configuration) throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          SSLParametersUtil.disableCipherSuites(p, "SOME_REALLY_BAD_CIPHER"); // for code coverage
          SSLParametersUtil.disableProtocols(p, "TLSv1.0", "TLSv1.1");
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxclient.truststore"),
            () -> "clienttrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runServerAndClient(addr,
          serverSocketFactory, clientSocketFactory, serverException, clientException, false));

      // no exceptions expected
      assertNull(serverException.get());
      assertNull(clientException.get());
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testClientAuthRequired(TestSSLConfiguration configuration) throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxserver.truststore"),
            () -> "servertrustpass".toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          p.setNeedClientAuth(true);
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxclient.p12"), () -> "clientpass"
            .toCharArray()) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxclient.truststore"),
            () -> "clienttrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
            clientException, false);

        // no exceptions expected
        assertNull(serverException.get());
        assertNull(clientException.get());
      });
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testClientAuthRequiredButClientIsNotSendingAKey(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          p.setNeedClientAuth(true);
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxclient.truststore"),
            () -> "clienttrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> {
          runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
              clientException, true);
        });

        // both client and server should throw an SSLHandshakeException or SocketException
        assertInstanceOf(serverException.get(), SSLHandshakeException.class, SocketException.class, //
            SSLProtocolException.class, //
            BOUNCYCASTLE_TLS_EXCEPTION, // org.bouncycastle.tls.TlsFatalAlert
            SSLException.class // IBMJSSEProvider2
        );
        assertInstanceOf(clientException.get(), null, SSLHandshakeException.class,
            SocketException.class, //
            SSLProtocolException.class, //
            BOUNCYCASTLE_TLS_EXCEPTION, // org.bouncycastle.tls.TlsFatalAlert
            SSLException.class // IBMJSSEProvider2
        );
      });
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testClientAuthRequiredButClientKeyIsNotTrusted(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          p.setNeedClientAuth(true);
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxclient.p12"), () -> "clientpass"
            .toCharArray()) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxclient.truststore"),
            () -> "clienttrustpass".toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> {
          runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
              clientException, true);
        });

        // both client and server should throw an SSLHandshakeException or SocketException
        assertInstanceOf(serverException.get(), SSLHandshakeException.class, SocketException.class, //
            SSLProtocolException.class, //
            BOUNCYCASTLE_TLS_EXCEPTION, // org.bouncycastle.tls.TlsFatalAlert
            SSLException.class // IBMJSSEProvider2
        );
        assertInstanceOf(clientException.get(), null, SSLHandshakeException.class,
            SocketException.class, //
            SSLProtocolException.class, //
            BOUNCYCASTLE_TLS_EXCEPTION, // org.bouncycastle.tls.TlsFatalAlert
            SSLException.class // IBMJSSEProvider2
        );
      });
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testClientHasNoTrustStore(TestSSLConfiguration configuration) throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .withSecureRandom(null) // just for code coverage
        .withDefaultSSLParameters((p) -> {
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
        .buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        assertThrows(IOException.class, () -> {
          runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
              clientException, true);
        });

        // both client and server should throw an SSLHandshakeException or SocketException
        assertInstanceOf(serverException.get(), SSLHandshakeException.class, SocketException.class, //
            SSLProtocolException.class, //
            BOUNCYCASTLE_TLS_EXCEPTION, // org.bouncycastle.tls.TlsFatalAlert
            SSLException.class // IBMJSSEProvider2
        );
        assertInstanceOf(clientException.get(), null, SSLHandshakeException.class,
            SocketException.class, //
            SSLProtocolException.class, //
            BOUNCYCASTLE_TLS_EXCEPTION, // org.bouncycastle.tls.TlsFatalAlert
            SSLException.class // IBMJSSEProvider2
        );
      });
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testServerAndClientBlindlyTrustAnything(TestSSLConfiguration configuration)
      throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();

    SSLSocketFactory serverSocketFactory = configuration.configure(SSLContextBuilder.forServer()) //
        .withTrustManagers((tmf) -> {
          return new TrustManager[] {IgnorantX509TrustManager.getInstance()};
        }).withDefaultSSLParameters(p -> {
          p.setNeedClientAuth(true);
        }) //
        .withKeyManagers((kmf) -> {
          KeyStore ks = SSLContextBuilder.newKeyStorePKCS12();
          try (InputStream in = SSLContextBuilderTest.class.getResourceAsStream("juxserver.p12")) {
            ks.load(in, "serverpass".toCharArray());
          }
          kmf.init(ks, "serverpass".toCharArray());
          return kmf.getKeyManagers();
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    SSLSocketFactory clientSocketFactory = configuration.configure(SSLContextBuilder.forClient()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxclient.p12"), () -> "clientpass"
            .toCharArray()) //
        .withTrustManagers((tmf) -> {
          return new TrustManager[] {IgnorantX509TrustManager.getInstance()};
        }).buildAndDestroyBuilder().getSocketFactory();

    CompletableFuture<Exception> serverException = new CompletableFuture<>();
    CompletableFuture<Exception> clientException = new CompletableFuture<>();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {

        Exception exception = null;
        try {
          runServerAndClient(addr, serverSocketFactory, clientSocketFactory, serverException,
              clientException, false);
        } catch (Exception e) {
          exception = e;
        }

        // no exceptions expected
        assertNull(serverException.get());
        assertNull(clientException.get());

        if (exception != null) {
          throw exception;
        }
      });
    } finally {
      Files.deleteIfExists(addr.getFile().toPath());
    }
  }

  private void runServerAndClient(AFUNIXSocketAddress addr, SSLSocketFactory serverSocketFactory,
      SSLSocketFactory clientSocketFactory, CompletableFuture<Exception> serverException,
      CompletableFuture<Exception> clientException, boolean expectClientTrouble) throws Exception {

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

      Exception caught = null;
      try (AFUNIXSocket plainSocket = AFUNIXSocket.connectTo(addr);
          SSLSocket sslSocket = (SSLSocket) clientSocketFactory.createSocket(plainSocket,
              "localhost.junixsocket", plainSocket.getPort(), false)) {

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
        } catch (SocketException e) {
          if (expectClientTrouble) {
            // ignore
          } else {
            throw e;
          }
        }
      } catch (Exception e) {
        caught = e;
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

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testDestroyablePasswordSupplier(TestSSLConfiguration configuration) throws Exception {
    DestroyablePasswordSupplier dps = new DestroyablePasswordSupplier();

    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), dps) //
        .withDefaultSSLParameters((p) -> {
        }); //

    builder.build();

    assertFalse(dps.isDestroyed());

    builder.destroy();

    assertTrue(dps.isDestroyed());
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testDestroyablePasswordSupplierDestroyed(TestSSLConfiguration configuration)
      throws Exception {
    DestroyablePasswordSupplier dps = new DestroyablePasswordSupplier();

    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), dps) //
        .withDefaultSSLParameters((p) -> {
        }); //

    builder.build();

    assertFalse(dps.isDestroyed());
    dps.destroy(); // destroy manually
    assertTrue(dps.isDestroyed());

    builder.destroy();

    assertTrue(dps.isDestroyed());
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testUndestroyablePasswordSupplier(TestSSLConfiguration configuration)
      throws Exception {
    UndestroyablePasswordSupplier dps = new UndestroyablePasswordSupplier();

    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), dps) //
        .withDefaultSSLParameters((p) -> {
        }); //

    builder.build();

    assertFalse(dps.isDestroyed());

    assertThrows(DestroyFailedException.class, builder::destroy);

    assertFalse(dps.isDestroyed());
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testUndestroyablePasswordSuppliers(TestSSLConfiguration configuration)
      throws Exception {
    UndestroyablePasswordSupplier dps = new UndestroyablePasswordSupplier();

    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), dps) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), dps) //
        .withDefaultSSLParameters((p) -> {
        }); //

    builder.build();

    assertFalse(dps.isDestroyed());

    try {
      builder.destroy();
      fail("Expected " + DestroyFailedException.class);
    } catch (DestroyFailedException e) {
      Throwable[] suppressed = e.getSuppressed();
      assertEquals(1, suppressed.length);
      assertInstanceOf(suppressed[0], DestroyFailedException.class);
    }

    assertFalse(dps.isDestroyed());
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
  public void testKeyStoreNullPasswordSupplied(TestSSLConfiguration configuration)
      throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> null) //
        .withDefaultSSLParameters((p) -> {
        }); //

    boolean fail = true;
    try {
      builder.build();
    } catch (UnrecoverableKeyException | NullPointerException e) {
      fail = false;
    }
    if (fail) {
      fail("Should have thrown an UnrecoverableKeyException or NullPointerException");
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
  public void testKeyStoreNullPasswordSupplier(TestSSLConfiguration configuration)
      throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), null) //
        .withDefaultSSLParameters((p) -> {
        }); //

    boolean fail = true;
    try {
      builder.build();
    } catch (UnrecoverableKeyException | NullPointerException e) {
      fail = false;
    }
    if (fail) {
      fail("Should have thrown an UnrecoverableKeyException or NullPointerException");
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testKeyStoreNullURL(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());

    assertThrows(NullPointerException.class, () -> builder.withKeyStore((URL) null,
        () -> "serverpass".toCharArray()));
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testKeyStoreURLNotFound(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());

    File f = File.createTempFile("test", ".jux");
    Files.delete(f.toPath());

    builder.withKeyStore(f.toURI().toURL(), () -> "serverpass".toCharArray());

    assertThrows(FileNotFoundException.class, builder::build);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testKeyStoreFileNotFound(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());

    File f = File.createTempFile("test", ".jux");
    Files.delete(f.toPath());

    builder.withKeyStore(f, () -> "serverpass".toCharArray());

    assertThrows(FileNotFoundException.class, builder::build);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testTrustStoreNullPasswordSupplied(TestSSLConfiguration configuration)
      throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxserver.truststore"), () -> null) //
        .withDefaultSSLParameters((p) -> {
        }); //

    assertNoExceptionOrNullPointerException(() -> builder.build());
  }

  @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
  private static void assertNoExceptionOrNullPointerException(Callable<?> op) throws Exception {
    try {
      op.call(); // no exception with standard Java provider
    } catch (NullPointerException e) {
      // bouncycastle throws NPE "no password supplied when one expected"
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testTrustStoreNullPasswordSupplier(TestSSLConfiguration configuration)
      throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer()) //
        .withTrustStore(SSLContextBuilderTest.class.getResource("juxserver.truststore"), null) //
        .withDefaultSSLParameters((p) -> {
        }); //

    assertNoExceptionOrNullPointerException(() -> builder.build());
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testTrustStoreNullURL(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());

    assertThrows(NullPointerException.class, () -> builder.withTrustStore((URL) null,
        () -> "servertrustpass".toCharArray()));
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testTrustStoreURLNotFound(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());

    File f = File.createTempFile("test", ".jux");
    Files.delete(f.toPath());

    builder.withTrustStore(f.toURI().toURL(), () -> "servertrustpass".toCharArray());

    assertThrows(FileNotFoundException.class, builder::build);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testTrustStoreFileNotFound(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());

    File f = File.createTempFile("test", ".jux");
    Files.delete(f.toPath());

    builder.withTrustStore(f, () -> "servertrustpass".toCharArray());

    assertThrows(FileNotFoundException.class, builder::build);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testBadProtocol(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forClient()) //
        .withProtocol("UnknownProtocol/UnitTesting");

    assertThrows(NoSuchAlgorithmException.class, builder::build);
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testBothKeyStoreAndKeyManagers(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());
    builder.withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"),
        () -> "serverpass".toCharArray());

    assertThrows(IllegalStateException.class, () -> {
      builder.withKeyManagers((kmf) -> {
        kmf.init(null, null);
        return kmf.getKeyManagers();
      });
    });
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testBothKeyManagersAndKeyStore(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());
    builder.withKeyManagers((kmf) -> {
      kmf.init(null, null);
      return kmf.getKeyManagers();
    });

    assertThrows(IllegalStateException.class, () -> {
      builder.withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"),
          () -> "serverpass".toCharArray());
    });
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testBothTrustStoreAndTrustManagers(TestSSLConfiguration configuration)
      throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());
    builder.withTrustStore(SSLContextBuilderTest.class.getResource("juxserver.truststore"),
        () -> "servertrustpass".toCharArray());

    assertThrows(IllegalStateException.class, () -> {
      builder.withTrustManagers((tmf) -> {
        tmf.init((KeyStore) null);
        return tmf.getTrustManagers();
      });
    });
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testBothTrustManagersAndTrustStore(TestSSLConfiguration configuration)
      throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());
    builder.withTrustManagers((tmf) -> {
      tmf.init((KeyStore) null);
      return tmf.getTrustManagers();
    });

    assertThrows(IllegalStateException.class, () -> {
      builder.withTrustStore(SSLContextBuilderTest.class.getResource("juxserver.truststore"),
          () -> "servertrustpass".toCharArray());
    });
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testWithDefaultParameters1(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());
    builder //
        .withDefaultSSLParameters((p) -> {
          assertNotNull(p);
          // consumer
        });

    assertThrows(IllegalStateException.class, () -> builder.withDefaultSSLParameters((p) -> {
      // function
      assertNotNull(p);
      return p;
    }));
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testWithDefaultParameters2(TestSSLConfiguration configuration) throws Exception {
    SSLContextBuilder builder = configuration.configure(SSLContextBuilder.forServer());
    builder //
        .withDefaultSSLParameters((p) -> {
          assertNotNull(p);
          // function
          return p;
        });

    assertThrows(IllegalStateException.class, () -> builder.withDefaultSSLParameters((p) -> {
      // consumer
      assertNotNull(p);
    }));
  }

  private static final class DestroyablePasswordSupplier implements SSLSupplier<char[]>,
      Destroyable {
    private char[] password = "serverpass".toCharArray();

    @Override
    public char[] get() throws GeneralSecurityException, IOException {
      return password.clone();
    }

    @Override
    public void destroy() throws DestroyFailedException {
      Arrays.fill(password, ' ');
      password = null;
    }

    @Override
    public boolean isDestroyed() {
      return password == null;
    }
  }

  private static final class UndestroyablePasswordSupplier implements SSLSupplier<char[]>,
      Destroyable {
    private final char[] password = "serverpass".toCharArray();

    @Override
    public char[] get() throws GeneralSecurityException, IOException {
      return password.clone();
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testSocketFactoryMethods(TestSSLConfiguration configuration) throws Exception {
    SSLSocketFactory factory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    assertThrows(SocketException.class, () -> factory.createSocket(),
        "Creating unconnected sockets is not supported, as they're very difficult to wrap");

    Socket someSocket = AFUNIXSocketPair.open().getFirst().socket();
    SSLSocket sslSocket = null;

    try {
      sslSocket = (SSLSocket) factory.createSocket(someSocket, null, false);
      assertNotNull(sslSocket);
      assertTrue(sslSocket.isConnected());
    } catch (UnsupportedOperationException e) {
      // on Android
    }

    sslSocket = (SSLSocket) factory.createSocket(someSocket, "some.ignored.host.identifier", 123,
        false);
    assertNotNull(sslSocket);
    assertTrue(sslSocket.isConnected());

    assertNotEquals(0, factory.getDefaultCipherSuites().length);
    assertNotEquals(0, factory.getSupportedCipherSuites().length);
  }

  @ExecutionEnvironmentRequirement(windows = Rule.PROHIBITED)
  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testSocketFactoryMethodsForCodeCoverageOnly(TestSSLConfiguration configuration)
      throws Exception {
    SSLSocketFactory factory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .buildAndDestroyBuilder().getSocketFactory();

    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (ServerSocket ss = new ServerSocket(0, 50, loopback)) {
      factory.createSocket(loopback.getHostAddress(), ss.getLocalPort());
      factory.createSocket(loopback, ss.getLocalPort());
      factory.createSocket(loopback.getHostAddress(), ss.getLocalPort(), loopback, 0);
      factory.createSocket(loopback, ss.getLocalPort(), loopback, 0);
    }
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testServerSocketFactoryMethods(TestSSLConfiguration configuration) throws Exception {
    SSLServerSocketFactory factory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .buildAndDestroyBuilder().getServerSocketFactory();

    assertNotEquals(0, factory.getDefaultCipherSuites().length);
    assertNotEquals(0, factory.getSupportedCipherSuites().length);

    assertNotNull(factory.createServerSocket());

    SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket();
    assertFalse(serverSocket.isBound());

    // Binding SSLServerSocket to a junixsocket address is currently not possible.
    // Use getSocketFactory() instead, and get an SSLSocket for the accepted Socket instead.
    assertThrows(SocketException.class, () -> serverSocket.bind(AFUNIXSocketAddress
        .ofNewTempFile()));
  }

  @ExecutionEnvironmentRequirement(windows = Rule.PROHIBITED)
  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testServerSocketFactoryMethodsForCodeCoverageOnly(TestSSLConfiguration configuration)
      throws Exception {
    SSLServerSocketFactory factory = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .buildAndDestroyBuilder().getServerSocketFactory();

    assertNotNull(factory.createServerSocket(0));
    assertNotNull(factory.createServerSocket(0, 0));
    assertNotNull(factory.createServerSocket(0, 0, InetAddress.getLoopbackAddress()));
  }

  @ParameterizedTest
  @EnumSource(TestSSLConfiguration.class)
  public void testSSLEngineMethods(TestSSLConfiguration configuration) throws Exception {
    SSLContext context = configuration.configure(SSLContextBuilder.forServer()) //
        .withKeyStore(SSLContextBuilderTest.class.getResource("juxserver.p12"), () -> "serverpass"
            .toCharArray()) //
        .buildAndDestroyBuilder();

    SSLEngine engine;

    context.init(null, null, null);

    engine = context.createSSLEngine();
    assertNotNull(engine);

    engine = context.createSSLEngine("non-authoritative-name", 123);
    assertNotNull(engine);

    context.getClientSessionContext(); // just trigger
    context.getServerSessionContext(); // just trigger
  }
}
