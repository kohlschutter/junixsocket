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
package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.AssertUtil;

@SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
public class AFUNIXSocketAddressTest {

  @Test
  public void testSchemesAvailable() throws Exception {
    AssertUtil.assertSetContains(AFAddressFamily.uriSchemes(), //
        Arrays.asList("unix", "http+unix", "https+unix"));
  }

  @Test
  public void testFileScheme() throws Exception {
    assertEquals(AFUNIXSocketAddress.of(new File("/tmp/yo")), AFUNIXSocketAddress.of(URI.create(
        "file:/tmp/yo")));
    assertEquals(AFUNIXSocketAddress.of(new File("/tmp/yo")), AFUNIXSocketAddress.of(URI.create(
        "file:///tmp/yo")));
    assertEquals(AFUNIXSocketAddress.of(new File("/tmp/yo")), AFUNIXSocketAddress.of(URI.create(
        "file://localhost/tmp/yo")));
  }

  @Test
  public void testUnixScheme() throws Exception {
    assertEquals(AFUNIXSocketAddress.of(new File("/tmp/yo")), AFUNIXSocketAddress.of(URI.create(
        "unix:/tmp/yo")));
    assertEquals(AFUNIXSocketAddress.of(new File("/tmp/yo")), AFUNIXSocketAddress.of(URI.create(
        "unix:///tmp/yo")));
    assertEquals(AFUNIXSocketAddress.of(new File("/tmp/yo")), AFUNIXSocketAddress.of(URI.create(
        "unix://localhost/tmp/yo")));
  }

  @Test
  public void testHttpUnix() throws Exception {
    assertEquals(AFUNIXSocketAddress.of(new File("/var/run/docker.sock")), AFUNIXSocketAddress.of(
        URI.create("http+unix://%2Fvar%2Frun%2Fdocker.sock/info")));
    assertEquals(AFUNIXSocketAddress.of(new File("/var/run/docker.sock"), 8123), AFUNIXSocketAddress
        .of(URI.create("http+unix://%2Fvar%2Frun%2Fdocker.sock:8123/info")));
    assertEquals(AFUNIXSocketAddress.of(new File("/var/run/docker.sock"), 8123), AFUNIXSocketAddress
        .of(URI.create(
            "http+unix://foo:bar%40@%2Fvar%2Frun%2Fdocker.sock:8123/info?q1=a1&q2=a2#frag%40")));
    assertEquals(AFUNIXSocketAddress.of(new File("test"), 80), AFUNIXSocketAddress.of(URI.create(
        "http+unix://test:80/info")));
  }

  private String fixWindowsEncodedPaths(String path) {
    return path.replace("%5C", "%2F");
  }

  private void assertsEqualUnixURIs(URI u1, URI u2) {
    assertEquals(fixWindowsEncodedPaths(u1.toString()), fixWindowsEncodedPaths(u2.toString()));
  }

  private void assertParseURI(URI u) throws Exception {
    AFUNIXSocketAddress addr = (AFUNIXSocketAddress) AFSocketAddress.of(u);
    URI u2 = addr.toURI(u.getScheme(), u);
    assertsEqualUnixURIs(u, u2);
  }

  @Test
  public void testParseURIandBack() throws Exception {
    assertParseURI(URI.create(
        "http+unix://foo:bar%40@%2Fvar%2Frun%2Fdocker.sock:8123/info?q1=a1&q2=a2+%40#frag%40"));
    assertParseURI(URI.create(
        "http+unix://%2Fvar%2Frun%2Fdocker.sock:8123/info?q1=a1&q2=a2+%40#frag%40"));
    assertParseURI(URI.create(
        "http+unix://%2Fvar%2Frun%2Fdocker.sock/info?q1=a1&q2=a2+%40#frag%40"));
    assertParseURI(URI.create("http+unix://%2Fvar%2Frun%2Fdocker.sock/info?q1="));
    assertParseURI(URI.create("http+unix://%2Fvar%2Frun%2Fdocker.sock:1234"));
  }

  @Test
  public void testURITemplate() throws Exception {
    URI socketURI = URI.create("unix://%2Fvar%2Frun%2Fdocker.sock");
    URI httpURI = URI.create("http://localhost/some/path?q=");
    AFSocketAddress a = AFSocketAddress.of(socketURI);
    assertEquals("https+unix://%2Fvar%2Frun%2Fdocker.sock/some/path?q=", fixWindowsEncodedPaths(a
        .toURI("https+unix", httpURI).toString()));
  }

  @Test
  public void testURITemplateWithPortNumber() throws Exception {
    URI socketURI = URI.create("unix://%2Fvar%2Frun%2Fdocker.sock");
    URI httpURI = URI.create("http://localhost:8123/some/path?q=");
    AFSocketAddress a = AFSocketAddress.of(socketURI);
    assertEquals("https+unix://%2Fvar%2Frun%2Fdocker.sock/some/path?q=", fixWindowsEncodedPaths(a
        .toURI("https+unix", httpURI).toString()));
  }

  @Test
  public void testSocatString() throws Exception {
    String socatString = AFUNIXSocketAddress.of(new File("/tmp/yo")).toSocatAddressString(null,
        AFSocketProtocol.DEFAULT);
    if (socatString == null) {
      assertFalse(AFSocket.supports(AFSocketCapability.CAPABILITY_UNIX_DOMAIN));
    } else {
      assertTrue(socatString.contains(":"));
    }
  }

  @Test
  public void testAbstractNamespace() throws Exception {
    assertNotNull(AFUNIXSocketAddress.inAbstractNamespace("test"));
    assertNotNull(AFUNIXSocketAddress.inAbstractNamespace("test", 1234));
  }

  @Test
  public void testSerialize() throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {

      AFUNIXSocketAddress addr = AFUNIXSocketAddress.of(URI.create("file:/tmp/yo"));
      oos.writeObject(addr);
      oos.flush();

      try (ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(bos
          .toByteArray()))) {
        AFUNIXSocketAddress addr2 = (AFUNIXSocketAddress) oin.readObject();
        assertEquals(addr, addr2);
        assertEquals(addr.getAddressFamily(), addr2.getAddressFamily());
      }
    }
  }
}
