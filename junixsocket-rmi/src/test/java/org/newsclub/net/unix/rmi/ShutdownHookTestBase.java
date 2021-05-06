package org.newsclub.net.unix.rmi;

import org.junit.jupiter.api.AfterAll;

public class ShutdownHookTestBase {
  static {
    System.setProperty("org.newsclub.net.unix.rmi.collect-shutdown-hooks", "true");
  }

  @AfterAll
  public static void tearDownClass() throws Exception {
    ShutdownHookSupport.runHooks();
  }
}
