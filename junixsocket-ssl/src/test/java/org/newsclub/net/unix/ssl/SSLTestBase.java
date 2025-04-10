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

import java.io.FileNotFoundException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.newsclub.net.unix.AFSocket;

import com.google.errorprone.annotations.Immutable;
import com.kohlschutter.testutil.LoggerUtil;
import com.kohlschutter.testutil.TestAbortedNotAnIssueException;
import com.kohlschutter.util.ReflectionUtil;

@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class SSLTestBase {
  static final Class<? extends Throwable> BOUNCYCASTLE_TLS_EXCEPTION = ReflectionUtil
      .throwableByNameForAssertion("org.bouncycastle.tls.TlsException");

  private static final String SYSTEM_PROVIDER_NAME = new Supplier<String>() {
    @Override
    public String get() {
      removeAllConfigurableProviders();
      try {
        return TestSSLConfiguration.SYSTEM.name() + " (" + SSLContext.getDefault().getProvider()
            .getName() + ")";
      } catch (NoSuchAlgorithmException e) {
        return TestSSLConfiguration.SYSTEM.name() + " (" + e.getMessage() + ")";
      }
    }
  }.get();

  private static final Provider PROVIDER_BOUNCYCASTLE_JCE = //
      ReflectionUtil.instantiateIfPossible(Provider.class,
          "org.bouncycastle.jce.provider.BouncyCastleProvider");
  private static final Provider PROVIDER_BOUNCYCASTLE_JSSE = //
      ReflectionUtil.instantiateIfPossible(Provider.class,
          "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider");
  private static final Provider PROVIDER_BOUNCYCASTLE_JSSE_FIPS = //
      ReflectionUtil.instantiateIfPossible(Provider.class,
          "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider", true);

  private static final Provider PROVIDER_IAIK_JCE = //
      ReflectionUtil.instantiateIfPossible(Provider.class, //
          "iaik.security.provider.IAIK");
  // Not supported yet (bug in IAIKJSSEProvider)
  private static final Provider PROVIDER_IAIK_JSSE = //
      ReflectionUtil.instantiateIfPossible(Provider.class,
          "iaik.security.jsse.provider.IAIKJSSEProvider");

  // Works on Java 8 only
  private static final Provider PROVIDER_OPENJSSE = //
      ReflectionUtil.instantiateIfPossible(Provider.class, //
          "org.openjsse.net.ssl.OpenJSSE");

  private static final Provider PROVIDER_CONSCRYPT = //
      ReflectionUtil.singletonIfPossible(Provider.class, //
          "org.conscrypt.Conscrypt", "newProvider");

  private static final Provider PROVIDER_WOLFSSL_JSSE = //
      ReflectionUtil.instantiateIfPossible(Provider.class,
          "com.wolfssl.provider.jsse.WolfSSLProvider");
  private static final Provider PROVIDER_WOLFCRYPT_JCE = //
      ReflectionUtil.instantiateIfPossible(Provider.class,
          "com.wolfssl.provider.jce.WolfCryptProvider");

  private static final String DEFAULT_PROVIDER_NAME = new Supplier<String>() {
    @Override
    public String get() {
      removeAllConfigurableProviders();
      try {
        if (PROVIDER_BOUNCYCASTLE_JSSE != null) {
          if (AFSocket.isRunningOnAndroid()) {
            return TestSSLConfiguration.DEFAULT.name() + " (" + PROVIDER_BOUNCYCASTLE_JSSE.getName()
                + ")";
          } else {
            return TestSSLConfiguration.DEFAULT.name() + " (" + SSLContext.getDefault()
                .getProvider().getName() + " with BouncyCastle JSSE available)";
          }
        } else {
          return TestSSLConfiguration.DEFAULT.name() + " (" + SSLContext.getDefault().getProvider()
              .getName() + ")";
        }
      } catch (NoSuchAlgorithmException e) {
        return TestSSLConfiguration.DEFAULT.name() + " (" + e.getMessage() + ")";
      }
    }
  }.get();

  SSLTestBase() {
  }

  @FunctionalInterface
  @Immutable
  public interface TestSSLContextBuilderConfigurator {
    SSLContextBuilder configure(SSLContextBuilder builder) throws Exception;
  }

  static void removeAllConfigurableProviders() {
    removeProvider("BCJSSE", PROVIDER_BOUNCYCASTLE_JSSE);
    removeProvider("BC", PROVIDER_BOUNCYCASTLE_JCE);
    removeProvider("IAIK_JSSE", PROVIDER_IAIK_JSSE);
    removeProvider("IAIK", PROVIDER_IAIK_JCE);
    removeProvider("OpenJSSE", PROVIDER_OPENJSSE);
    removeProvider("wolfJSSE", PROVIDER_WOLFSSL_JSSE);
    removeProvider("wolfJCE", PROVIDER_WOLFCRYPT_JCE);
    removeProvider("Conscrypt", PROVIDER_CONSCRYPT);
  }

  private static void removeProvider(String name, Provider provider) {
    if (provider == null) {
      return;
    }
    String providerName = provider.getName();
    if (!name.equals(providerName)) {
      System.err.println("WARNING: Unexpected provider name " + providerName + " for provider "
          + provider);
    }
    Provider p = Security.getProvider(providerName);
    if (p == provider) { // NOPMD.CompareObjectsWithEquals
      Security.removeProvider(providerName);
    }
  }

  @BeforeAll
  public static void beforeAll() {
    LoggerUtil.overrideDefaultConfiguration(SSLTestBase.class, "logging.properties");
  }

  @AfterAll
  public static void afterAll() {
    try {
      LoggerUtil.revertToDefaultConfiguration();
    } catch (IllegalStateException e) {
      if (e.getCause() instanceof FileNotFoundException) {
        // GraalVM -- ignore
      } else {
        throw e;
      }
    }
    removeAllConfigurableProviders();
  }

  public enum TestSSLConfiguration implements Named<TestSSLConfiguration> {
    DEFAULT((b) -> {
      removeAllConfigurableProviders();
      return b;
    }, false), //

    SYSTEM((b) -> {
      removeAllConfigurableProviders();
      b.withProvider((Provider) null);
      return b;
    }, false), //

    BOUNCYCASTLE_JCE((b) -> {
      if (PROVIDER_BOUNCYCASTLE_JCE == null) {
        throw new TestAbortedNotAnIssueException("BouncyCastle JCE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.addProvider(PROVIDER_BOUNCYCASTLE_JCE);
      return b;
    }, false), //
    BOUNCYCASTLE_JCE_AND_JSEE((b) -> {
      if (PROVIDER_BOUNCYCASTLE_JCE == null) {
        throw new TestAbortedNotAnIssueException("BouncyCastle JCE provider unvailable");
      }
      if (PROVIDER_BOUNCYCASTLE_JSSE == null) {
        throw new TestAbortedNotAnIssueException("BouncyCastle JSSE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.addProvider(PROVIDER_BOUNCYCASTLE_JCE);
      Security.addProvider(PROVIDER_BOUNCYCASTLE_JSSE);
      b.withProvider(PROVIDER_BOUNCYCASTLE_JSSE);
      return b;
    }, false), //
    BOUNCYCASTLE_JCE_AND_JSEE_FIPS((b) -> {
      if (PROVIDER_BOUNCYCASTLE_JCE == null) {
        throw new TestAbortedNotAnIssueException("BouncyCastle JCE provider unvailable");
      }
      if (PROVIDER_BOUNCYCASTLE_JSSE_FIPS == null) {
        throw new TestAbortedNotAnIssueException("BouncyCastle JSSE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.addProvider(PROVIDER_BOUNCYCASTLE_JCE);
      Security.addProvider(PROVIDER_BOUNCYCASTLE_JSSE_FIPS);
      b.withProvider(PROVIDER_BOUNCYCASTLE_JSSE_FIPS);
      b.withKeyStoreSupplier(() -> KeyStore.getInstance("PKCS12", PROVIDER_BOUNCYCASTLE_JCE));
      return b;
    }, false),

    IAIK_JCE((b) -> {
      if (PROVIDER_IAIK_JCE == null) {
        throw new TestAbortedNotAnIssueException("IAIK JCE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.insertProviderAt(PROVIDER_IAIK_JCE, 0);
      return b;
    }, false), //

    // NOTE: Currently broken because IAIK JSSE cannot switch to server mode
    // (iaik.security.ssl.SSLException "SSLServerContext required in server mode!")
    IAIK_JCE_AND_JSSE((b) -> {
      if (PROVIDER_IAIK_JCE == null) {
        throw new TestAbortedNotAnIssueException("IAIK JCE provider unvailable");
      }
      if (PROVIDER_IAIK_JSSE == null) {
        throw new TestAbortedNotAnIssueException("IAIK JSSE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.insertProviderAt(PROVIDER_IAIK_JCE, 0);
      Security.addProvider(PROVIDER_IAIK_JSSE);
      b.withProvider(PROVIDER_IAIK_JSSE);
      return b;
    }, false), //

    OPENJSSE((b) -> {
      if (PROVIDER_OPENJSSE == null) {
        throw new TestAbortedNotAnIssueException("OPENJSSE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.addProvider(PROVIDER_OPENJSSE);
      b.withProvider(PROVIDER_OPENJSSE);
      return b;
    }, false), //

    // Works mostly, but has some problems with our test certificates
    // ("verify problem on certificate (error code: -329)")
    WOLFCRYPT_WOLFSSL((b) -> {
      if (PROVIDER_WOLFCRYPT_JCE == null) {
        throw new TestAbortedNotAnIssueException("Wolfcrypt JCE provider unvailable");
      }
      if (PROVIDER_WOLFSSL_JSSE == null) {
        throw new TestAbortedNotAnIssueException("WolfSSL JSSE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.addProvider(PROVIDER_WOLFCRYPT_JCE);
      Security.addProvider(PROVIDER_WOLFSSL_JSSE);
      // b.withKeyStoreSupplier(() -> KeyStore.getInstance("PKCS12", PROVIDER_WOLFCRYPT_JSSE));
      b.withProvider(PROVIDER_WOLFSSL_JSSE);
      return b;
    }, false), //

    CONSCRYPT((b) -> {
      if (PROVIDER_CONSCRYPT == null) {
        throw new TestAbortedNotAnIssueException("Conscrypt provider unvailable");
      }

      removeAllConfigurableProviders();
      Security.addProvider(PROVIDER_CONSCRYPT);
      b.withProvider(PROVIDER_CONSCRYPT);
      return b;
    }, true), //
    ;

    private final boolean sniBroken;
    private final TestSSLContextBuilderConfigurator builderConfigurator;

    TestSSLConfiguration(TestSSLContextBuilderConfigurator configurator, boolean sniBroken) {
      this.builderConfigurator = configurator;
      this.sniBroken = sniBroken;
    }

    @Override
    public String getName() {
      switch (this) {
        case DEFAULT:
          return DEFAULT_PROVIDER_NAME;
        case SYSTEM:
          return SYSTEM_PROVIDER_NAME;
        default:
          return name();
      }
    }

    public boolean isSniBroken() {
      return sniBroken;
    }

    @Override
    public TestSSLConfiguration getPayload() {
      return this;
    }

    Exception handleException(Throwable e) throws Exception {
      if (this == TestSSLConfiguration.SYSTEM && AFSocket.isRunningOnAndroid()) {
        throw new TestAbortedNotAnIssueException("Known to fail on Android", e);
      } else if (e instanceof Error) {
        throw (Error) e;
      } else {
        throw (Exception) e;
      }
    }

    SSLContextBuilder configure(SSLContextBuilder builder) throws Exception {
      return builderConfigurator.configure(builder);
    }
  }
}
