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
package org.newsclub.net.mysql;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
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
  private static final Set<String> MISSING = new HashSet<>();

  private static void requiresClass(String className) {
    try {
      Class.forName(className);
    } catch (ClassNotFoundException e) {
      MISSING.add(className);
    }
  }

  static {
    requiresClass("com.mysql.jdbc.SocketFactory");
    requiresClass("com.mysql.cj.protocol.SocketFactory");
  }

  public Map<String, Class<?>[]> tests() {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    tests.put("junixsocket-mysql", new Class<?>[] { //
        AFUNIXDatabaseSocketFactoryTest.class, AFUNIXDatabaseSocketFactoryCJTest.class,});

    return tests;
  }

  public Set<String> modulesDisabledByDefault() {
    if (MISSING.isEmpty()) {
      return Collections.emptySet();
    } else {
      return Collections.singleton("junixsocket-mysql");
    }
  }

  public void printAdditionalProperties(PrintWriter out) {
    if (MISSING.isEmpty()) {
      out.println("junixsocket-mysql: All requirements are met");
    } else {
      out.println("junixsocket-mysql: The following class requirements are not met: " + MISSING);
    }
  }
}
