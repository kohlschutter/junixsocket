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

import com.kohlschutter.testutil.AssertUtil;

public class AFVSOCKSocketAddressTest {

  @Test
  public void testSchemesAvailable() throws Exception {
    AssertUtil.assertSetContains(AFAddressFamily.uriSchemes(), //
        Arrays.asList("vsock", "http+vsock", "https+vsock"));
  }

  @Test
  public void testParseFail() throws Exception {
    assertThrows(SocketException.class, () -> AFVSOCKSocketAddress.of(URI.create(
        "vsock://invalid")));
    assertThrows(SocketException.class, () -> AFVSOCKSocketAddress.of(URI.create(
        "vsock://socket.23/")));
  }

  private AFVSOCKSocketAddress validateAddress(String uri) throws IOException {
    URI u = URI.create(uri);
    AFVSOCKSocketAddress address = AFVSOCKSocketAddress.of(u);
    URI u2 = address.toURI(u.getScheme(), null);
    assertEquals(address, AFVSOCKSocketAddress.of(u2));
    return address;
  }

  @Test
  public void testSocketURI() throws Exception {
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(123, 456), validateAddress("vsock://123.456"));
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(123, 0), validateAddress(
        "vsock://123.hypervisor"));
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(0, 0), validateAddress("vsock://0.hypervisor"));
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(0, 0), validateAddress("vsock://0.hypervisor"));
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(-1, 0), validateAddress(
        "vsock://any.hypervisor"));
    assertEquals(validateAddress("vsock://-1.0"), validateAddress("vsock://any.hypervisor"));
    assertEquals(validateAddress("vsock://0xffffffff.0"), validateAddress(
        "vsock://any.hypervisor"));
    assertEquals(validateAddress("vsock://456.any"), validateAddress("vsock://456.-1"));
    assertEquals(AFVSOCKSocketAddress.ofAnyPort(), validateAddress("vsock://any"));
    assertEquals(AFVSOCKSocketAddress.ofAnyLocalPort(), validateAddress("vsock://-1.local"));
    assertEquals(AFVSOCKSocketAddress.ofAnyLocalPort(), validateAddress("vsock://any.local"));
    assertEquals(AFVSOCKSocketAddress.ofLocalPort(456), validateAddress("vsock://456.local"));
    assertEquals(AFVSOCKSocketAddress.ofLocalPort(456), validateAddress("vsock://456.local"));
    assertEquals(AFVSOCKSocketAddress.ofHostPort(456), validateAddress("vsock://456.host"));
    assertEquals(AFVSOCKSocketAddress.ofAnyHostPort(), validateAddress("vsock://any.host"));
  }

  @Test
  public void testNamedCIDs() throws Exception {
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(-1, AFVSOCKSocketAddress.VMADDR_CID_HYPERVISOR),
        AFVSOCKSocketAddress.ofAnyHypervisorPort());
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(-1, AFVSOCKSocketAddress.VMADDR_CID_LOCAL),
        AFVSOCKSocketAddress.ofAnyLocalPort());
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(-1, AFVSOCKSocketAddress.VMADDR_CID_HOST),
        AFVSOCKSocketAddress.ofAnyHostPort());

    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(567, AFVSOCKSocketAddress.VMADDR_CID_HYPERVISOR),
        AFVSOCKSocketAddress.ofHypervisorPort(567));
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(101112, AFVSOCKSocketAddress.VMADDR_CID_LOCAL),
        AFVSOCKSocketAddress.ofLocalPort(101112));
    assertEquals(AFVSOCKSocketAddress.ofPortAndCID(2345, AFVSOCKSocketAddress.VMADDR_CID_HOST),
        AFVSOCKSocketAddress.ofHostPort(2345));
  }

  @Test
  public void testSocatString() throws Exception {
    String socatString;
    try {
      socatString = AFVSOCKSocketAddress.ofPortAndCID(123, 4).toSocatAddressString(
          AFSocketType.SOCK_STREAM, AFSocketProtocol.DEFAULT);
      assertNotNull(socatString);
    } catch (SocketException e) {
      if (AFSocket.supports(AFSocketCapability.CAPABILITY_VSOCK)) {
        throw e;
      } else {
        // expected
        return;
      }
    }
    assertTrue(socatString.contains(":"));
  }
}
