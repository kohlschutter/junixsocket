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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFTIPCSocketAddress.Scope;

import com.kohlschutter.testutil.AssertUtil;

public class AFTIPCSocketAddressTest {

  @Test
  public void testSchemesAvailable() throws Exception {
    AssertUtil.assertSetContains(AFAddressFamily.uriSchemes(), //
        Arrays.asList("tipc", "http+tipc", "https+tipc"));
  }

  @Test
  public void testParseFail() throws Exception {
    assertThrows(SocketException.class, () -> AFTIPCSocketAddress.of(URI.create("tipc://invalid")));
    assertThrows(SocketException.class, () -> AFTIPCSocketAddress.of(URI.create(
        "tipc://socket.23/")));
  }

  private AFTIPCSocketAddress validateAddress(String uri) throws IOException {
    URI u = URI.create(uri);
    AFTIPCSocketAddress address = AFTIPCSocketAddress.of(u);
    URI u2 = address.toURI(u.getScheme(), null);
    assertEquals(address, AFTIPCSocketAddress.of(u2));
    return address;
  }

  @Test
  public void testSocketURI() throws Exception {
    assertEquals(AFTIPCSocketAddress.ofSocket(1, 2), validateAddress("tipc://socket.1.2"));
    assertEquals(AFTIPCSocketAddress.ofSocket(1, 2), validateAddress("tipc://socket.1.2.0"));
    assertEquals(AFTIPCSocketAddress.ofSocket(1, 2), validateAddress("tipc://0-socket.1.2.0"));
    assertEquals(AFTIPCSocketAddress.ofSocket(1, 2), validateAddress("tipc://0-3.1.2.0"));
    assertEquals(AFTIPCSocketAddress.ofSocket(4, 1, 2), validateAddress("tipc://0-socket.1.2.0:4"));

    assertEquals(AFTIPCSocketAddress.ofSocket(-1, -1), validateAddress(
        "tipc://socket.4294967295.4294967295"));
    assertEquals(AFTIPCSocketAddress.ofSocket(-1, -1), validateAddress(
        "tipc://socket.0xffffffff.0xffffffff"));
  }

  @Test
  public void testServiceURI() throws Exception {
    assertEquals(AFTIPCSocketAddress.ofService(1, 2), validateAddress("tipc://1.2"));

    assertEquals(AFTIPCSocketAddress.ofService(1, 2), validateAddress("tipc://service.1.2"));
    assertEquals(AFTIPCSocketAddress.ofService(1, 2), validateAddress("tipc://service.1.2.0"));
    assertEquals(AFTIPCSocketAddress.ofService(Scope.SCOPE_NOT_SPECIFIED, 1, 2), validateAddress(
        "tipc://0-service.1.2.0"));
    assertEquals(AFTIPCSocketAddress.ofService(1, 2), validateAddress("tipc://2-2.1.2.0"));
    assertEquals(AFTIPCSocketAddress.ofService(Scope.SCOPE_CLUSTER, 1, 2, 3), validateAddress(
        "tipc://2-2.1.2.3"));
    assertEquals(AFTIPCSocketAddress.ofService(4, Scope.SCOPE_CLUSTER, 1, 2, 3), validateAddress(
        "tipc://2-2.1.2.3:4"));

    assertEquals(AFTIPCSocketAddress.ofService(Scope.SCOPE_CLUSTER, -1, -1, -1), validateAddress(
        "tipc://service.4294967295.4294967295.4294967295"));
    assertEquals(AFTIPCSocketAddress.ofService(12345, Scope.SCOPE_CLUSTER, -1, -1, -1),
        validateAddress("tipc://service.4294967295.4294967295.4294967295:12345"));
    assertEquals(AFTIPCSocketAddress.ofService(-1, -1), validateAddress(
        "tipc://service.0xffffffff.0xffffffff"));
  }

  @Test
  public void testServiceRangeURI() throws Exception {
    assertEquals(AFTIPCSocketAddress.ofServiceRange(1, 2, 3), validateAddress(
        "tipc://service-range.1.2.3"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(1, 2, 2), validateAddress(
        "tipc://service-range.1.2"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(1, 2, 3), validateAddress(
        "tipc://service-range.1.2.3"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(Scope.SCOPE_NOT_SPECIFIED, 1, 2, 3),
        validateAddress("tipc://0-service-range.1.2.3"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(1, 2, 3), validateAddress("tipc://2-1.1.2.3"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(4, Scope.SCOPE_CLUSTER, 1, 2, 3),
        validateAddress("tipc://service-range.1.2.3:4"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(4, Scope.SCOPE_CLUSTER, 1, 2, 3),
        validateAddress("tipc://cluster-service-range.1.2.3:4"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(4, Scope.SCOPE_NODE, 1, 2, 3), validateAddress(
        "tipc://node-service-range.1.2.3:4"));

    assertEquals(AFTIPCSocketAddress.ofServiceRange(12345, Scope.SCOPE_CLUSTER, -1, -1, -1),
        validateAddress("tipc://service-range.4294967295.4294967295.4294967295:12345"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(-1, -1, -1), validateAddress(
        "tipc://service-range.0xffffffff.0xffffffff"));
    assertEquals(AFTIPCSocketAddress.ofServiceRange(-1, -1, -1), validateAddress(
        "tipc://service-range.0xffffffff.0xffffffff.0xffffffff"));
  }

  @Test
  public void testGeneric() throws Exception {
    assertEquals(AFTIPCSocketAddress.ofServiceRange(4, Scope.ofValue(56), 1, 2, 3), validateAddress(
        "tipc://56-service-range.1.2.3:4"));
  }

  @Test
  public void testSocatString() throws Exception {
    String socatString;
    try {
      socatString = AFTIPCSocketAddress.ofService(123, 456).toSocatAddressString(
          AFSocketType.SOCK_STREAM, AFSocketProtocol.DEFAULT);
      assertNotNull(socatString);
    } catch (SocketException e) {
      if (AFSocket.supports(AFSocketCapability.CAPABILITY_TIPC)) {
        throw e;
      } else {
        // expected
        return;
      }
    }
    assertTrue(socatString.contains(":"));
  }
}
