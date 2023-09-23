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
