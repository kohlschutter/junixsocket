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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Helper class to simplify building {@link SSLContext} instances.
 *
 * @author Christian Kohlschütter
 */
public final class SSLContextBuilder {
  private final boolean clientMode;

  private String protocol = "TLS";
  private SecureRandom secureRandom = null;

  private URL keyStoreUrl;
  private SSLSupplier<char[]> keyStorePassword;

  private URL trustManagerUrl;
  private SSLSupplier<char[]> trustManagerPassword;

  private Function<SSLParameters, SSLParameters> parametersFunction = null;

  private SSLFunction<KeyManagerFactory, KeyManager[]> keyManager = null;
  private SSLFunction<TrustManagerFactory, TrustManager[]> trustManager = null;

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

  private KeyManager[] buildKeyManagers(KeyManagerFactory kmf) throws GeneralSecurityException,
      IOException, UnrecoverableKeyException {
    if (keyStoreUrl == null) {
      // use default
      return null; // NOPMD.ReturnEmptyCollectionRatherThanNull
    }

    KeyStore ks = KeyStore.getInstance("JKS");

    char[] password = keyStorePassword == null ? null : keyStorePassword.get();
    try (InputStream in = keyStoreUrl.openStream()) {
      ks.load(in, password);
      kmf.init(ks, password);
    } finally {
      clear(password);
    }

    return kmf.getKeyManagers();
  }

  private TrustManager[] buildTrustManagers(TrustManagerFactory tmf) throws IOException,
      GeneralSecurityException {

    if (trustManagerUrl == null) {
      // use default
      return null; // NOPMD.ReturnEmptyCollectionRatherThanNull
    }

    KeyStore ks = KeyStore.getInstance("JKS");

    char[] password = trustManagerPassword == null ? null : trustManagerPassword.get();
    try (InputStream in = trustManagerUrl.openStream()) {
      ks.load(in, password);
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
  public SSLContext build() throws GeneralSecurityException, IOException {
    SSLContext sslContext = SSLContext.getInstance(protocol);

    SSLFunction<KeyManagerFactory, KeyManager[]> km = this.keyManager;
    if (km == null) {
      km = this::buildKeyManagers;
    }

    SSLFunction<TrustManagerFactory, TrustManager[]> tm = this.trustManager;
    if (tm == null) {
      tm = this::buildTrustManagers;
    }

    sslContext.init( //
        km.apply(buildKeyManagerFactory()), //
        tm.apply(buildTrustManagerFactory()), //
        secureRandom);

    return new BuilderSSLContext(clientMode, sslContext, parametersFunction);
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
    SSLContext context = build();
    destroy();
    return context;
  }

  /**
   * Destroys the state of this builder and all key-/trust-related settings specified.
   *
   * @throws DestroyFailedException on error (thrown at the end, to increase level of destruction).
   */
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
}
