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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

public final class AFUNIXSocketAddressTest {

  @Test
  public void testPort() throws IOException {
    assertEquals(0, new AFUNIXSocketAddress(new File("/tmp/whatever")).getPort());
    assertEquals(123, new AFUNIXSocketAddress(new File("/tmp/whatever"), 123).getPort());
    assertEquals(44444, new AFUNIXSocketAddress(new File("/tmp/whatever"), 44444).getPort());
    try {
      new AFUNIXSocketAddress(new File("/tmp/whatever"), -1);
      fail("Expected IllegalArgumentException for illegal port");
    } catch (final IllegalArgumentException e) {
      // expected
    }
    try {
      new AFUNIXSocketAddress(new File("/tmp/whatever"), 65536);
    } catch (final IllegalArgumentException e) {
      fail("AFUNIXSocketAddress supports ports larger than 65535");
    }
  }

  @Test
  public void testPath() throws Exception {
    // as of junixsocket 2.4.0, a different canonical path doesn't matter
    assertEquals("/tmp/whatever", new AFUNIXSocketAddress(new File("/tmp/whatever")).getPath());
    assertEquals("whatever", new AFUNIXSocketAddress(new File("whatever")).getPath());
    assertArrayEquals("/tmp/whatever".getBytes(Charset.defaultCharset()), new AFUNIXSocketAddress(
        new File("/tmp/whatever")).getPathAsBytes());
  }

  @Test
  public void testAbstractNamespace() throws Exception {
    AFUNIXSocketAddress address = AFUNIXSocketAddress.inAbstractNamespace("test\n\u000b\u0000");
    byte[] addressBytes = {0, 't', 'e', 's', 't', '\n', '\u000b', '\u0000'};
    assertArrayEquals(addressBytes, address.getPathAsBytes());
    assertEquals(0, address.getPort());
    assertEquals("@test..@", address.getPath());
    assertEquals("org.newsclub.net.unix.AFUNIXSocketAddress[port=0;path=\\x00test\\x0a\\x0b\\x00]",
        address.toString());
  }

  @Test
  public void testEmptyAddress() throws Exception {
    assertThrows(SocketException.class, () -> new AFUNIXSocketAddress(new byte[0]));
  }
}
