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
import org.newsclub.net.unix.AFSYSTEMSocketAddress.SysAddr;

import com.kohlschutter.testutil.AssertUtil;

public class AFSYSTEMSocketAddressTest {

  @Test
  public void testSchemesAvailable() throws Exception {
    AssertUtil.assertSetContains(AFAddressFamily.uriSchemes(), //
        Arrays.asList("afsystem"));
  }

  @Test
  public void testParseFail() throws Exception {
    assertThrows(SocketException.class, () -> AFSYSTEMSocketAddress.of(URI.create(
        "afsystem://invalid")));
    assertThrows(SocketException.class, () -> AFSYSTEMSocketAddress.of(URI.create(
        "afsystem://socket.23/")));
  }

  private AFSYSTEMSocketAddress validateAddress(String uri) throws IOException {
    URI u = URI.create(uri);
    AFSYSTEMSocketAddress address = AFSYSTEMSocketAddress.of(u);
    URI u2 = address.toURI(u.getScheme(), null);
    assertEquals(address, AFSYSTEMSocketAddress.of(u2));
    return address;
  }

  @Test
  public void testURI() throws Exception {
    assertEquals(AFSYSTEMSocketAddress.ofSysAddrIdUnit(SysAddr.AF_SYS_CONTROL, 3, 4),
        validateAddress("afsystem://2.3.4"));
  }

  @Test
  public void testSocatString() throws Exception {
    String socatString;
    try {
      socatString = AFSYSTEMSocketAddress.ofSysAddrIdUnit(SysAddr.AF_SYS_CONTROL, 3, 4)
          .toSocatAddressString(AFSocketType.SOCK_STREAM, AFSocketProtocol.DEFAULT);
      assertNotNull(socatString);
    } catch (SocketException e) {
      if (AFSocket.supports(AFSocketCapability.CAPABILITY_DARWIN)) {
        throw e;
      } else {
        // expected
        return;
      }
    }
    assertTrue(socatString.contains(":"));
  }
}
