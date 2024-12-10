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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.KnownJavaBugIOException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Helper class to simplify building {@link SSLContext} instances.
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class SSLContextBuilder {
  private static final Provider PROVIDER_PKCS12 = AFSocket.isRunningOnAndroid()
      ? bouncyCastleInstanceIfPossible() : null;
  private static final Provider PROVIDER_JSSE = AFSocket.isRunningOnAndroid()
      ? bouncyCastleJSSEInstanceIfPossible() : null;
  private static final String DEFAULT_PROVIDER = AFSocket.isRunningOnAndroid()
      && PROVIDER_JSSE != null
          ? "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider,org.bouncycastle.jce.provider.BouncyCastleProvider"
          : null;
  private final boolean clientMode;

  private String protocol = "TLS";
  private SecureRandom secureRandom = null;

  private URL keyStoreUrl;
  private SSLSupplier<char[]> keyStorePassword;

  private URL trustManagerUrl;
  private SSLSupplier<char[]> trustManagerPassword;

  private Function<SSLParameters, SSLParameters> parametersFunction = null;

  private SSLSupplier<KeyStore> keyStoreSupplier = SSLContextBuilder::newKeyStorePKCS12;
  private SSLFunction<KeyManagerFactory, KeyManager[]> keyManager = null;
  private SSLFunction<TrustManagerFactory, TrustManager[]> trustManager = null;
  private Object provider = DEFAULT_PROVIDER;

  private SocketFactory socketFactory = SocketFactory.getDefault();

  /**
   * Creates a new {@link SSLContextBuilder} instance.
   */
  private SSLContextBuilder(boolean clientMode) {
    this.clientMode = clientMode;
  }

  /**
   * Creates an {@link SSLContextBuilder} to be used in a server context.
   *
   * @return The builder instance.
   */
  public static SSLContextBuilder forServer() {
    return new SSLContextBuilder(false);
  }

  /**
   * Creates an {@link SSLContextBuilder} to be used in a client context.
   *
   * @return The builder instance.
   */
  public static SSLContextBuilder forClient() {
    return new SSLContextBuilder(true);
  }

  /**
   * Configures this builder to use the given {@link SocketFactory} to create the underlying
   * insecure sockets.
   *
   * @param sf The {@link SocketFactory}.
   * @return This builder.
   */
  public SSLContextBuilder withSocketFactory(SocketFactory sf) {
    this.socketFactory = sf == null ? SocketFactory.getDefault() : sf;
    return this;
  }

  /**
   * Configures this builder to use the given protocol. Note that "{@code TLS}" is the default.
   *
   * @param p The protocol to use, e.g. {@code TLSv1.2}.
   * @return This builder.
   */
  public SSLContextBuilder withProtocol(String p) {
    this.protocol = p;
    return this;
  }

  /**
   * Configures this builder to use the given provider, {@code null} being the default.
   *
   * @param p The provider to use, e.g. {@code BouncyCastleJsseProvider}, or {@code null} for system
   *          default.
   * @return This builder.
   */
  public SSLContextBuilder withProvider(Provider p) {
    this.provider = p;
    return this;
  }

  /**
   * Configures this builder to use the given provider, identified by ID, {@code null} being the
   * default.
   * <p>
   * In addition to the standard JSSE IDs, you can specify one or more Provider classnames as a
   * comma-separated list. These providers will be added via {@link Security#addProvider(Provider)}.
   * The first entry is then attempted to be resolved using {@link Provider#getName()}, with any
   * optionally remaining {@link Provider}s simply being added to the list of available providers,
   * in case they're actually required by the first one. It is expected that the classes have a
   * public no-arg constructor.
   * <p>
   * This is the case, for example, with BouncyCastle. Specify
   * {@code org.bouncycastle.jsse.provider.BouncyCastleJsseProvider,org.bouncycastle.jce.provider.BouncyCastleProvider}
   * to enable TLS-secured communication with PKCS12 keys, for example.
   *
   * @param id The provider to use, e.g. {@code BCJSSE}, or {@code null}/{@code ""} for system
   *          default.
   * @return This builder.
   */
  public SSLContextBuilder withProvider(String id) {
    this.provider = id.isEmpty() ? null : id;
    return this;
  }

  /**
   * Configures this builder to use the given supplier for {link KeyManager}{@code []}.
   *
   * Note that setting any value other than {@code null} means that the parameters specified with
   * {@link #withKeyStore(File, SSLSupplier)}, etc. are ignored.
   *
   * @param s The supplier to use, or {@code null} to use the built-in default.
   * @return This builder.
   * @throws IllegalStateException if {@link #withKeyStore(File, SSLSupplier)}, etc., was already
   *           called.
   */
  public SSLContextBuilder withKeyManagers(SSLFunction<KeyManagerFactory, KeyManager[]> s) {
    if (keyStoreUrl != null) {
      throw new IllegalStateException("withKeyStore was already called");
    }
    this.keyManager = s;
    return this;
  }

  /**
   * Configures this builder to use the given supplier for {@link TrustManager}{@code []}.
   *
   * Note that setting any value other than {@code null} means that the parameters specified with
   * {@link #withTrustStore(File, SSLSupplier)}, etc. are ignored.
   *
   * @param s The supplier to use, or {@code null} to use the built-in default.
   * @return This builder.
   */
  public SSLContextBuilder withTrustManagers(SSLFunction<TrustManagerFactory, TrustManager[]> s) {
    if (trustManagerUrl != null) {
      throw new IllegalStateException("withTrustStore was already called");
    }
    this.trustManager = s;
    return this;
  }

  /**
   * Configures this builder to use the given protocol. Note that "{@code null}" is the default,
   * which means that it's up the SSL implementation what {@link SecureRandom} to use.
   *
   * @param s The instance to use, e.g. {@code null}.
   * @return This builder.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public SSLContextBuilder withSecureRandom(SecureRandom s) {
    this.secureRandom = s;
    return this;
  }

  /**
   * Configures this builder to use the given supplier to provide {@link KeyStore} instances.
   *
   * If {@code null} is specified, the default supplier is used, which is configured for
   * {@code PKCS12}-type keystores. In that case, on Android, it is expected that the
   * <em>BouncyCastle</em> SSL provider ({@code org.bouncycastle.jce.provider.BouncyCastleProvider})
   * is on the classpath.
   *
   * @param supplier The supplier, or {@code null} for default.
   * @return This builder.
   */
  public SSLContextBuilder withKeyStoreSupplier(SSLSupplier<KeyStore> supplier) {
    this.keyStoreSupplier = supplier == null ? SSLContextBuilder::newKeyStorePKCS12 : supplier;
    return this;
  }

  /**
   * Configures this builder to use the given keystore, identified by path and password.
   *
   * @param path The path to the keystore.
   * @param password The supplier that returns the password to unlock the keystore; the password
   *          will be overwritten with blanks immediately after use.
   * @return This builder.
   * @throws FileNotFoundException on error.
   * @throws MalformedURLException on error.
   * @throws IllegalStateException if {@link #withKeyManagers(SSLFunction)} was already called.
   * @see #withKeyManagers(SSLFunction)
   */
  public SSLContextBuilder withKeyStore(File path, SSLSupplier<char[]> password)
      throws FileNotFoundException, MalformedURLException {
    return withKeyStore(path.toURI().toURL(), password);
  }

  /**
   * Configures this builder to use the given keystore, identified by URL and password.
   *
   * @param url The {@link URL} specifying the location of the keystore
   * @param password The supplier that returns the password to unlock the keystore; the password
   *          will be overwritten with blanks immediately after use.
   * @return This builder.
   * @throws FileNotFoundException on error.
   * @see #withKeyManagers(SSLFunction)
   */
  public SSLContextBuilder withKeyStore(URL url, SSLSupplier<char[]> password)
      throws FileNotFoundException {
    if (this.keyManager != null) {
      throw new IllegalStateException("withKeyManagers was already called");
    }
    this.keyStoreUrl = Objects.requireNonNull(url);
    this.keyStorePassword = password;
    this.keyManager = this::buildKeyManagers;

    return this;
  }

  /**
   * Configures this builder to use the given truststore, identified by path and password.
   *
   * @param path The path to the truststore.
   * @param password The supplier that returns the password to unlock the keystore; the password
   *          will be overwritten with blanks immediately after use.
   * @return This builder.
   * @throws FileNotFoundException on error.
   * @throws MalformedURLException on error.
   * @see #withTrustManagers(SSLFunction)
   */
  public SSLContextBuilder withTrustStore(File path, SSLSupplier<char[]> password)
      throws FileNotFoundException, MalformedURLException {
    return withTrustStore(path.toURI().toURL(), password);
  }

  /**
   * Configures this builder to use the given truststore, identified by path and password.
   *
   * @param url The {@link URL} specifying the location of the truststore.
   * @param password The supplier that returns the password to unlock the keystore; the password
   *          will be overwritten with blanks immediately after use.
   * @return This builder.
   * @throws FileNotFoundException on error.
   * @see #withTrustManagers(SSLFunction)
   */
  public SSLContextBuilder withTrustStore(URL url, SSLSupplier<char[]> password)
      throws FileNotFoundException {
    if (this.trustManager != null) {
      throw new IllegalStateException("withTrustManagers was already called");
    }
    this.trustManagerUrl = Objects.requireNonNull(url, "trustStore");
    this.trustManagerPassword = password;
    return this;
  }

  /**
   * Configures this builder to use the given function to configure the default SSL parameters.
   *
   * The function is called with the context's default SSL parameters instance.
   *
   * The function may modify and return the given instance, or return a completely different
   * instance.
   *
   * @param function The function to configure SSL parameters.
   * @return This builder.
   */
  @SuppressWarnings("overloads")
  public SSLContextBuilder withDefaultSSLParameters(
      Function<SSLParameters, SSLParameters> function) {
    if (parametersFunction != null) {
      throw new IllegalStateException("Default parameters already set");
    }
    this.parametersFunction = function;
    return this;
  }

  /**
   * Configures this builder to use the given consumer to configure the default SSL parameters.
   *
   * The consumer is called with the context's default SSL parameters instance.
   *
   * The consumer may modify or just inspect the given instance.
   *
   * @param consumer The function to configure SSL parameters.
   * @return This builder.
   */
  @SuppressWarnings("overloads")
  public SSLContextBuilder withDefaultSSLParameters(Consumer<SSLParameters> consumer) {
    return withDefaultSSLParameters((p) -> {
      consumer.accept(p);
      return p;
    });
  }

  private KeyManagerFactory buildKeyManagerFactory() throws GeneralSecurityException {
    return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
  }

  private TrustManagerFactory buildTrustManagerFactory() throws GeneralSecurityException {
    return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  }

  @SuppressFBWarnings("URLCONNECTION_SSRF_FD")
  private KeyManager[] buildKeyManagers(KeyManagerFactory kmf) throws GeneralSecurityException,
      IOException, UnrecoverableKeyException {
    if (keyStoreUrl == null) {
      // use default
      return null; // NOPMD.ReturnEmptyCollectionRatherThanNull
    }

    KeyStore ks = keyStoreSupplier.get();

    char[] password = keyStorePassword == null ? null : keyStorePassword.get();
    try (InputStream in = keyStoreUrl.openStream()) {
      ks.load(in, password);
      kmf.init(ks, password);
    } catch (IOException e) {
      throw wrapIOExceptionIfJDKBug(e);
    } finally {
      clear(password);
    }

    return kmf.getKeyManagers();
  }

  /**
   * For a given {@link IOException} thrown from within {@link SSLContextBuilder}, check if it is
   * due to a known JDK bug, and if so, wrap that exception in a {@link KnownJavaBugIOException}
   * with a proper explanation.
   *
   * @param e The exception to check/wrap.
   * @return The exception, or a {@link KnownJavaBugIOException}.
   */
  public static IOException wrapIOExceptionIfJDKBug(IOException e) {
    String msg = e.getMessage();
    if (msg == null) {
      return e;
    }
    if (msg.contains("data isn't an object ID (tag = 48)")) {
      return knownJDKBug(e, "JDK-8202837", "8u312", "11.0.3");
    } else if (msg.contains("HmacPBESHA256 not available")) {
      return knownJDKBug(e, "JDK-8267701", "8u301", "11.0.12",
          "alternatively try running Java with -Dkeystore.pkcs12.legacy");
    } else if (msg.contains("HmacPBESHA256")) {
      if ("IBM J9 VM".equals(System.getProperty("java.vm.name"))) {
        String vmVersion = System.getProperty("java.runtime.version", "");
        if (vmVersion.startsWith("8.0.") && vmVersion.compareTo("8.0.8.") < 0) { // NOPMD
          return new KnownJavaBugIOException(
              "Bug JDK-8267701 detected -- please upgrade your Java release to at least IBM SDK 8.0.8.0; "
                  + "details here: https://www.ibm.com/support/pages/troubleshooting-unable-open-pkcs12-keystores-due-unrecoverablekeyexception",
              e);
        }
      }
      return new KnownJavaBugIOException(
          "Bug JDK-8267701 detected -- please upgrade your Java release; "
              + "alternatively try running Java with -Dkeystore.pkcs12.legacy", e);
    }
    return e;
  }

  private static KnownJavaBugIOException knownJDKBug(Exception e, String bugId, String java8Version,
      String java11Version) {
    return knownJDKBug(e, bugId, java8Version, java11Version, null);
  }

  private static KnownJavaBugIOException knownJDKBug(Exception e, String bugId, String java8Version,
      String java11Version, String hint) {
    if (hint == null) {
      hint = "";
    } else {
      hint = "; " + hint;
    }
    String specVersion = System.getProperty("java.specification.version", "");
    if (specVersion.startsWith("1.")) {
      return new KnownJavaBugIOException("Bug " + bugId + " detected -- please upgrade to Java "
          + java8Version + " or newer" + hint, e);
    } else if ("9".equals(specVersion) || "10".equals(specVersion) || "11".equals(specVersion)) {
      return new KnownJavaBugIOException("Bug " + bugId + " detected -- please upgrade to Java "
          + java11Version + " or newer" + hint, e);
    } else {
      return new KnownJavaBugIOException("Bug " + bugId
          + " detected -- please upgrade your Java release" + hint, e);
    }
  }

  @SuppressFBWarnings("URLCONNECTION_SSRF_FD")
  private TrustManager[] buildTrustManagers(TrustManagerFactory tmf) throws IOException,
      GeneralSecurityException {

    if (trustManagerUrl == null) {
      // use default
      return null; // NOPMD.ReturnEmptyCollectionRatherThanNull
    }

    KeyStore ks = keyStoreSupplier.get();

    char[] password = trustManagerPassword == null ? null : trustManagerPassword.get();
    try (InputStream in = trustManagerUrl.openStream()) {
      ks.load(in, password);
    } catch (IOException e) {
      throw wrapIOExceptionIfJDKBug(e);
    } finally {
      clear(password);
    }

    tmf.init(ks);

    return tmf.getTrustManagers();
  }

  private static void clear(char[] password) {
    if (password == null) {
      return;
    }
    Arrays.fill(password, ' ');
  }

  /**
   * Try to initialize the SSLContext with a provider specified by classname.
   *
   * If {@code providerString} contains a comma-separated list, all providers in the list are
   * initialized (if possible), but the first one used to initialize the {@link SSLContext}.
   *
   * @param e The original exception when trying to resolve the provider by ID.
   * @param protocol The desired protocol.
   * @param providerString The provider string with the classname(s).
   * @return The context.
   * @throws NoSuchAlgorithmException on error.
   * @throws NoSuchProviderException on error.
   */
  @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
  private static SSLContext tryInitContextFallback(NoSuchProviderException e, String protocol,
      String providerString) throws NoSuchAlgorithmException, NoSuchProviderException {
    try {
      List<Provider> providers = new ArrayList<>();
      for (String pn : providerString.split(",")) {
        pn = pn.trim(); // NOPMD.AvoidReassigningLoopVariables
        if (pn.isEmpty()) {
          continue;
        }
        Class<?> klazz = Class.forName(pn);
        if (!Provider.class.isAssignableFrom(klazz)) {
          continue;
        }
        Provider p = (Provider) klazz.getConstructor().newInstance();
        providers.add(p);

        try {
          Security.addProvider(p);
        } catch (SecurityException | NullPointerException e1) {
          e.addSuppressed(e1);
        }
      }
      if (providers.isEmpty()) {
        throw e;
      } else {
        return SSLContext.getInstance(protocol, Objects.requireNonNull(providers.get(0)).getName());
      }
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException
        | ClassNotFoundException | NullPointerException e1) {
      e.addSuppressed(e1);
      throw e; // NOPMD.PreserveStackTrace
    }
  }

  /**
   * Builds an {@link SSLContext} using the current builder state.
   * <p>
   * <b>IMPORTANT:</b> Use {@link #buildAndDestroyBuilder()} to ensure sensitive information, such
   * as passwords, are properly destroyed/removed from memory.
   *
   * @return The new {@link SSLContext} instance.
   * @throws GeneralSecurityException on error.
   * @throws IOException on error.
   * @see #buildAndDestroyBuilder()
   */
  @SuppressWarnings("PatternMatchingInstanceof") // ErrorProne
  public SSLContext build() throws GeneralSecurityException, IOException {
    SSLContext sslContext;
    if (provider == null) {
      sslContext = SSLContext.getInstance(protocol);
    } else if (provider instanceof String) {
      String providerString = (String) provider;
      try {
        sslContext = SSLContext.getInstance(protocol, providerString);
      } catch (NoSuchProviderException e) {
        sslContext = tryInitContextFallback(e, protocol, providerString);
      }
    } else {
      sslContext = SSLContext.getInstance(protocol, (Provider) provider);
    }

    SSLFunction<KeyManagerFactory, KeyManager[]> km = this.keyManager;
    if (km == null) {
      km = this::buildKeyManagers;
    }

    SSLFunction<TrustManagerFactory, TrustManager[]> tm = this.trustManager;
    if (tm == null) {
      tm = this::buildTrustManagers;
    }

    KeyManager[] kms = km.apply(buildKeyManagerFactory());
    TrustManager[] tms = tm.apply(buildTrustManagerFactory());

    BuilderSSLContext.initContext(sslContext, kms, tms, secureRandom);

    return new BuilderSSLContext(clientMode, sslContext, parametersFunction, socketFactory);
  }

  /**
   * Builds an {@link SSLContext} using the current builder state, and destroys the builder's state,
   * to reduce the chance of information leakage.
   *
   * @return The new {@link SSLContext} instance.
   * @throws GeneralSecurityException on error.
   * @throws IOException on error.
   * @throws DestroyFailedException on error.
   * @see #destroy()
   */
  public SSLContext buildAndDestroyBuilder() throws GeneralSecurityException, IOException,
      DestroyFailedException {
    try {
      SSLContext context = build();
      destroy();
      return context;
    } catch (IOException e) {
      throw wrapIOExceptionIfJDKBug(e);
    }
  }

  /**
   * Destroys the state of this builder and all key-/trust-related settings specified.
   *
   * @throws DestroyFailedException on error (thrown at the end, to increase level of destruction).
   */
  @SuppressWarnings("PatternMatchingInstanceof") // ErrorProne
  public void destroy() throws DestroyFailedException {
    DestroyFailedException dfe = null;

    for (Object o : new Object[] {
        keyStorePassword, trustManagerPassword, keyManager, trustManager}) {
      if (o instanceof Destroyable) {
        Destroyable d = (Destroyable) o;
        if (!d.isDestroyed()) {
          try {
            d.destroy();
          } catch (DestroyFailedException e) {
            if (dfe == null) {
              dfe = e;
            } else {
              dfe.addSuppressed(e);
            }
          }
        }
      }
    }

    this.keyStoreUrl = null;
    this.keyStorePassword = null;
    this.keyManager = null;

    this.trustManagerUrl = null;
    this.trustManagerPassword = null;
    this.trustManager = null;

    this.parametersFunction = null;
    this.secureRandom = null;

    if (dfe != null) {
      throw dfe;
    }
  }

  private static Provider resolveProviderIfPossible(String className) {
    Class<?> klazz;
    try {
      klazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null; // NOPMD
    }
    try {
      return (Provider) klazz.getConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException
        | ClassCastException e) {
      throw new IllegalStateException("Cannot instantiate provider '" + className
          + "', despite being on the classpath", e);
    }
  }

  private static Provider bouncyCastleInstanceIfPossible() {
    return resolveProviderIfPossible("org.bouncycastle.jce.provider.BouncyCastleProvider");
  }

  private static Provider bouncyCastleJSSEInstanceIfPossible() {
    return resolveProviderIfPossible("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider");
  }

  /**
   * Returns a new {@code PKCS12} {@link KeyStoreException} instance.
   *
   * @return The keystore instance.
   * @throws KeyStoreException on error.
   */
  public static KeyStore newKeyStorePKCS12() throws KeyStoreException {
    if (PROVIDER_PKCS12 == null) {
      return KeyStore.getInstance("PKCS12");
    } else {
      return KeyStore.getInstance("PKCS12", PROVIDER_PKCS12);
    }
  }
}
