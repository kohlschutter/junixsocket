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

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A wrapping {@link X509TrustManager} that verifies each individual certificate in a chain, in
 * addition to the successful validation by the wrapped {@link X509TrustManager}.
 * <p>
 * This could be useful in some scenarios where self-signed certificates are being used, or where
 * the upstream {@link TrustManager} can't be fully trusted.
 *
 * @author Christian Kohlschütter
 */
public final class ValidatingX509TrustManager extends FilterX509TrustManager {
  /**
   * Constructs a {@link ValidatingX509TrustManager} instance that wraps the given
   * {@link X509TrustManager}.
   *
   * @param wrapped The wrapped trust manager.
   */
  public ValidatingX509TrustManager(X509TrustManager wrapped) {
    super(wrapped);
  }

  @Override
  protected void onCertificateException(boolean checkClient, CertificateException e,
      X509Certificate[] chain, String authType) throws CertificateException {
    throw e; // throw to prevent certificate from being accepted
  }

  @Override
  protected void onCertificateTrusted(boolean checkClient, X509Certificate[] chain, String authType)
      throws CertificateException {
    for (X509Certificate cert : chain) {
      cert.checkValidity(); // check expiry as well (off by default for self-signed certs)
    }
  }
}
