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
package org.newsclub.net.unix.selftest;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Provides references to all tests that should be included in junixsocket-selftest.
 *
 * @author Christian Kohlschütter
 */
public class SelftestProvider {
  private final org.newsclub.net.unix.SelftestProvider commonSelftests =
      new org.newsclub.net.unix.SelftestProvider();
  private final org.newsclub.net.unix.tipc.SelftestProvider tipcSelftests =
      new org.newsclub.net.unix.tipc.SelftestProvider();
  private final org.newsclub.net.unix.vsock.SelftestProvider vsockSelftests =
      new org.newsclub.net.unix.vsock.SelftestProvider();
  private final org.newsclub.net.unix.rmi.SelftestProvider rmiSelftests =
      new org.newsclub.net.unix.rmi.SelftestProvider();
  private final org.newsclub.net.unix.darwin.SelftestProvider darwinSelftests =
      new org.newsclub.net.unix.darwin.SelftestProvider();

  public Map<String, Class<?>[]> tests() throws Exception {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    tests.putAll(commonSelftests.tests());
    tests.putAll(tipcSelftests.tests());
    tests.putAll(vsockSelftests.tests());
    tests.putAll(rmiSelftests.tests());
    tests.putAll(darwinSelftests.tests());

    return tests;
  }

  public Set<String> modulesDisabledByDefault() {
    Set<String> set = new HashSet<>();
    set.addAll(commonSelftests.modulesDisabledByDefault());
    set.addAll(tipcSelftests.modulesDisabledByDefault());
    set.addAll(vsockSelftests.modulesDisabledByDefault());
    set.addAll(rmiSelftests.modulesDisabledByDefault());
    set.addAll(darwinSelftests.modulesDisabledByDefault());
    return set;
  }

  public void printAdditionalProperties(PrintWriter out) {
    commonSelftests.printAdditionalProperties(out);
    tipcSelftests.printAdditionalProperties(out);
    vsockSelftests.printAdditionalProperties(out);
    rmiSelftests.printAdditionalProperties(out);
    darwinSelftests.printAdditionalProperties(out);
  }
}
