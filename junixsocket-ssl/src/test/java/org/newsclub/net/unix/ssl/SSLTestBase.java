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

import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Named;
import org.newsclub.net.unix.AFSocket;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

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

  private static final Provider PROVIDER_BOUNCYCASTLE_JCE = ReflectionUtil.instantiateIfPossible(
      "org.bouncycastle.jce.provider.BouncyCastleProvider");

  private static final Provider PROVIDER_BOUNCYCASTLE_JSSE = ReflectionUtil.instantiateIfPossible(
      "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider");
  private static final Provider PROVIDER_BOUNCYCASTLE_JSSE_FIPS = ReflectionUtil
      .instantiateIfPossible("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider", true);

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
  public interface TestSSLContextBuilderConfigurator {
    SSLContextBuilder configure(SSLContextBuilder builder) throws Exception;
  }

  private static void removeAllConfigurableProviders() {
    removeProvider("BC", PROVIDER_BOUNCYCASTLE_JCE);
    removeProvider("BCJSSE", PROVIDER_BOUNCYCASTLE_JSSE);
  }

  private static void removeProvider(String name, Provider ifThis) {
    Provider p = Security.getProvider(name);
    if (p == ifThis && p != null) { // NOPMD.CompareObjectsWithEquals
      Security.removeProvider(name);
    }
  }

  @SuppressFBWarnings("SE_BAD_FIELD")
  public enum TestSSLConfiguration implements Named<TestSSLConfiguration> {
    DEFAULT((b) -> {
      removeAllConfigurableProviders();
      return b;
    }), //

    SYSTEM((b) -> {
      removeAllConfigurableProviders();
      b.withProvider((Provider) null);
      return b;
    }), //

    BOUNCYCASTLE_JCE((b) -> {
      if (PROVIDER_BOUNCYCASTLE_JCE == null) {
        throw new TestAbortedNotAnIssueException("BouncyCastle JCE provider unvailable");
      }
      removeAllConfigurableProviders();
      Security.addProvider(PROVIDER_BOUNCYCASTLE_JCE);
      return b;
    }), //
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
    }), //
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
    });

    private final TestSSLContextBuilderConfigurator builderConfigurator;

    TestSSLConfiguration(TestSSLContextBuilderConfigurator configurator) {
      this.builderConfigurator = configurator;
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
