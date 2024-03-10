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

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

public class ProviderTest extends SSLTestBase {
  @Test
  public void testDumpDefaultProviders() throws Exception {
    removeAllConfigurableProviders();
    System.out.println();
    System.out.println("Available Security providers:");
    List<Provider> supportTLS = new ArrayList<>();
    for (Provider p : Security.getProviders()) {
      System.out.println("- " + p);
      try {
        SSLContext.getInstance("TLS", p);
        supportTLS.add(p);
      } catch (NoSuchAlgorithmException e) {
        continue;
      }
    }
    System.out.println();
    System.out.println("Available Security providers that support TLS:");
    for (Provider p : supportTLS) {
      System.out.println("- " + p);
    }
    System.out.println();
  }
}
