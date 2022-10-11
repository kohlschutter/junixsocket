package org.newsclub.net.unix.selftest;

import java.util.Collections;
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

  public Map<String, Class<?>[]> tests() throws Exception {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    tests.putAll(commonSelftests.tests());
    tests.putAll(tipcSelftests.tests());
    tests.putAll(vsockSelftests.tests());
    tests.putAll(rmiSelftests.tests());

    return tests;
  }

  public Set<String> modulesDisabledByDefault() {
    Set<String> set = new HashSet<>();
    set.addAll(commonSelftests.modulesDisabledByDefault());
    set.addAll(tipcSelftests.modulesDisabledByDefault());
    set.addAll(vsockSelftests.modulesDisabledByDefault());
    set.addAll(rmiSelftests.modulesDisabledByDefault());
    return set;
  }
}
