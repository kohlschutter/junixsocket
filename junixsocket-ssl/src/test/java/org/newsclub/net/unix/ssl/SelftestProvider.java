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

import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides references to all "junixsocket-ssl" tests that should be included in
 * junixsocket-selftest.
 *
 * @author Christian Kohlschütter
 */
public class SelftestProvider {
  public Map<String, Class<?>[]> tests() {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    tests.put("junixsocket-ssl", new Class<?>[] { //
        ProviderTest.class, // should be at the top
        SSLContextBuilderTest.class, //
        SNIHostnameCaptureTest.class, //
        ValidatingX509TrustManagerTest.class, //
    });

    return tests;
  }

  public Set<String> modulesDisabledByDefault() {
    return Collections.emptySet();
  }

  public void printAdditionalProperties(PrintWriter out) {
  }
}
