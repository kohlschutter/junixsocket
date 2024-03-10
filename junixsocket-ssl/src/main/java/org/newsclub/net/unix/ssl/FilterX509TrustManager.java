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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * An {@link X509TrustManager} that may intercept {@link CertificateException}s.
 *
 * @author Christian Kohlschütter
 */
public abstract class FilterX509TrustManager implements X509TrustManager {
  private final X509TrustManager wrapped;

  /**
   * Constructs this instance with the given wrapped {@link X509TrustManager}.
   *
   * @param wrapped Thw wrapped instance.
   */
  protected FilterX509TrustManager(X509TrustManager wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public final void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    try {
      wrapped.checkClientTrusted(chain, authType);
    } catch (CertificateException e) {
      onCertificateException(true, e, chain, authType);
      return;
    }
    onCertificateTrusted(true, chain, authType);
  }

  @Override
  public final void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    try {
      wrapped.checkServerTrusted(chain, authType);
    } catch (CertificateException e) {
      onCertificateException(false, e, chain, authType);
      return;
    }
    onCertificateTrusted(false, chain, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return wrapped.getAcceptedIssuers();
  }

  /**
   * Called whenever the wrapped {@link X509TrustManager} throws an exception when checking client
   * or server certificate chains.
   *
   * @param checkClient If {@code true}, we're checking a client certificate chain, if {@code false}
   *          a server's.
   * @param e The caught exception
   * @param chain The (potentially partial) certificate chain
   * @param authType The authType.
   * @throws CertificateException if desired.
   */
  protected abstract void onCertificateException(boolean checkClient, CertificateException e,
      X509Certificate[] chain, String authType) throws CertificateException;

  /**
   * Called whenever the wrapped {@link X509TrustManager} trusted a given client or server
   * certificate chains.
   *
   * @param checkClient If {@code true}, we're checking a client certificate chain, if {@code false}
   *          a server's.
   * @param chain The (potentially partial) certificate chain
   * @param authType The authType.
   * @throws CertificateException if desired.
   */
  protected abstract void onCertificateTrusted(boolean checkClient, X509Certificate[] chain,
      String authType) throws CertificateException;
}
