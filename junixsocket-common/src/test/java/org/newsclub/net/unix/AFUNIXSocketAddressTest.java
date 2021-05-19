/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

public final class AFUNIXSocketAddressTest {

  @Test
  public void testPort() throws IOException {
    assertEquals(0, AFUNIXSocketAddress.of(new File("/tmp/whatever")).getPort());
    assertEquals(123, AFUNIXSocketAddress.of(new File("/tmp/whatever"), 123).getPort());
    assertEquals(44444, AFUNIXSocketAddress.of(new File("/tmp/whatever"), 44444).getPort());

    try {
      AFUNIXSocketAddress.of(new File("/tmp/whatever"), -2);
      fail("Expected IllegalArgumentException for illegal port");
    } catch (final IllegalArgumentException e) {
      // expected
    }
    try {
      AFUNIXSocketAddress.of(new File("/tmp/whatever"), 65536);
    } catch (final IllegalArgumentException e) {
      fail("AFUNIXSocketAddress supports ports larger than 65535");
    }
  }

  @Test
  public void testPath() throws Exception {
    // as of junixsocket 2.4.0, a different canonical path doesn't matter
    assertEquals("/tmp/whatever", AFUNIXSocketAddress.of(new File("/tmp/whatever")).getPath());
    assertEquals("whatever", AFUNIXSocketAddress.of(new File("whatever")).getPath());
    assertArrayEquals("/tmp/whatever".getBytes(Charset.defaultCharset()), AFUNIXSocketAddress.of(
        new File("/tmp/whatever")).getPathAsBytes());
  }

  @Test
  public void testAbstractNamespace() throws Exception {
    AFUNIXSocketAddress address = AFUNIXSocketAddress.inAbstractNamespace(
        "test\n\u000b\u0000\u007f");
    byte[] addressBytes = {0, 't', 'e', 's', 't', '\n', '\u000b', '\u0000', '\u007f'};
    assertArrayEquals(addressBytes, address.getPathAsBytes());
    assertEquals(0, address.getPort());
    assertEquals("@test..@.", address.getPath());
    assertEquals(
        "org.newsclub.net.unix.AFUNIXSocketAddress[port=0;path=\\x00test\\x0a\\x0b\\x00\\x7f]",
        address.toString());
    assertTrue(address.isInAbstractNamespace());
    assertFalse(address.hasFilename());
    assertThrows(FileNotFoundException.class, () -> address.getFile());
  }

  @Test
  public void testEmptyAddress() throws Exception {
    assertThrows(SocketException.class, () -> AFUNIXSocketAddress.of(new byte[0]));
  }

  @Test
  public void testByteConstructor() throws Exception {
    assertEquals("@", AFUNIXSocketAddress.of(new byte[] {0}).getPath());
    assertEquals("@..", AFUNIXSocketAddress.of(new byte[] {0, (byte) 128, (byte) 255}).getPath());
    assertEquals("ü", AFUNIXSocketAddress.of("ü".getBytes(Charset.defaultCharset())).getPath());
    assertEquals(new File("ü"), AFUNIXSocketAddress.of(new File("ü")).getFile());
    assertEquals("ü", AFUNIXSocketAddress.of(new File("ü")).getPath());
  }

  @Test
  public void testInetAddress() throws Exception {
    AFUNIXSocketAddress addr1 = AFUNIXSocketAddress.of(new File("/tmp/testing"));
    assertTrue(AFUNIXSocketAddress.isSupportedAddress(addr1));
    assertNull(addr1.getAddress()); // sadly
    assertTrue(AFUNIXSocketAddress.isSupportedAddress(addr1.wrapAddress()));
    AFUNIXSocketAddress addr2 = AFUNIXSocketAddress.unwrap(addr1.wrapAddress(), 0);
    assertEquals(addr1, addr2);
    assertTrue(AFUNIXSocketAddress.isSupportedAddress(addr2));
    assertTrue(AFUNIXSocketAddress.isSupportedAddress(addr2.wrapAddress()));
    assertNull(addr2.getAddress()); // sadly
  }
}
