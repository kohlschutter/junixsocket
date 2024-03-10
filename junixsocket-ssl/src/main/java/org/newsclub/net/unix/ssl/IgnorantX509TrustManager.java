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
 * An ignorant {@link X509TrustManager} that doesn't check certificates at all.
 * <p>
 * <b>IMPORTANT:</b> Not checking certificates is a bad idea. The rationale for providing this class
 * nevertheless is that it's easier to search for usages of this class than to search for usages of
 * some lookalikes.
 *
 * @author Christian Kohlschütter
 */
public final class IgnorantX509TrustManager implements X509TrustManager {
  private static final IgnorantX509TrustManager INSTANCE = new IgnorantX509TrustManager();
  private static final X509Certificate[] EMPTY_ACCEPTED_ISSUERS = new X509Certificate[0];

  private IgnorantX509TrustManager() {
  }

  /**
   * Returns the singleton instance.
   *
   * @return The instance.
   */
  public static IgnorantX509TrustManager getInstance() {
    return INSTANCE;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return EMPTY_ACCEPTED_ISSUERS;
  }
}
