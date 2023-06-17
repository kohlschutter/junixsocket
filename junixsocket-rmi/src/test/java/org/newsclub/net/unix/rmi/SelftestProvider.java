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
package org.newsclub.net.unix.rmi;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides references to all "junixsocket-rmi" tests that should be included in
 * junixsocket-selftest.
 *
 * @author Christian Kohlschütter
 */
public class SelftestProvider {
  public Map<String, Class<?>[]> tests() {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    tests.put("junixsocket-rmi", new Class<?>[] { //
        RegistryTest.class, //
        RemoteCloseableTest.class, //
        RemoteFileDescriptorTest.class, //
        RMIPeerCredentialsTest.class, //
        JunixsocketVersionTest.class, //
    });

    return tests;
  }

  public Set<String> modulesDisabledByDefault() {
    return Collections.emptySet();
  }

  public void printAdditionalProperties(PrintWriter out) {
  }
}
