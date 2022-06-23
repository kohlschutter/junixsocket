/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
package org.newsclub.net.unix.domain;

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
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.SocketTestBase;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

// @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public final class SocketAddressTest extends SocketTestBase<AFUNIXSocketAddress> {

  public SocketAddressTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

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
    assertEquals("/tmp/whatever", AFUNIXSocketAddress.of(new File("/tmp/whatever")).getPath()
        .replace(File.separatorChar, '/'));
    assertEquals("whatever", AFUNIXSocketAddress.of(new File("whatever")).getPath().replace(
        File.separatorChar, '/'));

    byte[] bytes = AFUNIXSocketAddress.of(new File("/tmp/whatever")).getPathAsBytes();
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == (byte) File.separatorChar) {
        bytes[i] = '/';
      }
    }
    assertArrayEquals("/tmp/whatever".getBytes(Charset.defaultCharset()), bytes);
  }

  @Test
  public void testAbstractNamespace() throws Exception {
    AFUNIXSocketAddress address = AFUNIXSocketAddress.inAbstractNamespace(
        "test\n\u000b\u0000\u007f");
    if ("z/OS".equals(System.getProperty("os.name"))) {
      // FIXME: check bytes in EBCDIC
    } else {
      byte[] addressBytes = {0, 't', 'e', 's', 't', '\n', '\u000b', '\u0000', '\u007f'};
      assertArrayEquals(addressBytes, address.getPathAsBytes());
    }
    assertEquals(0, address.getPort());
    if ("z/OS".equals(System.getProperty("os.name"))) {
      // FIXME: check bytes in EBCDIC
    } else {
      assertEquals("@test..@.", address.getPath());
      assertEquals("[path=\\x00test\\x0a\\x0b\\x00\\x7f]", address.toString().replaceFirst(
          "^.*?\\[", "["));
    }
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

    assertEquals("abc.123", AFUNIXSocketAddress.of("abc.123".getBytes(AFUNIXSocketAddress
        .addressCharset())).getPath());
    assertEquals(new File("abc.123"), AFUNIXSocketAddress.of(new File("abc.123")).getFile());
    assertEquals("abc.123", AFUNIXSocketAddress.of(new File("abc.123")).getPath());
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
