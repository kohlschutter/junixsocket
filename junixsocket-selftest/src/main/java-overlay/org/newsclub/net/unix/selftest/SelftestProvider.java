package org.newsclub.net.unix.selftest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides references to all tests that should be included in junixsocket-selftest.
 * 
 * @author Christian Kohlschütter
 */
public class SelftestProvider {
  public Map<String, Class<?>[]> tests() throws Exception {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    tests.putAll(new org.newsclub.net.unix.SelftestProvider().tests());
    tests.putAll(new org.newsclub.net.unix.rmi.SelftestProvider().tests());

    return tests;
  }
}
