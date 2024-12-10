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

final class TestCredentials {
  private static final String JUXSERVER_PASSWORD = "serverpass";

  private static final String JUXCLIENT_PASSWORD = "clientpass";

  private static final String JUXSERVER_TRUSTSTORE_PASSWORD = "servertrustpass";

  private static final String JUXCLIENT_TRUSTSTORE_PASSWORD = "clienttrustpass";

  private TestCredentials() {
  }

  static char[] getJuxServerPassword() {
    return JUXSERVER_PASSWORD.toCharArray();
  }

  static char[] getJuxClientPassword() {
    return JUXCLIENT_PASSWORD.toCharArray();
  }

  static char[] getJuxClientTrustStorePassword() {
    return JUXCLIENT_TRUSTSTORE_PASSWORD.toCharArray();
  }

  static char[] getJuxServerTrustStorePassword() {
    return JUXSERVER_TRUSTSTORE_PASSWORD.toCharArray();
  }
}
