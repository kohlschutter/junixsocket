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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

public class AFUNIXInetAddressTest {

  @Test
  public void testFromToBytes() throws IOException {
    byte[] bytes = {0, 1, 2, 3, 0x7f, (byte) 0xff};
    assertArrayEquals(bytes, AFUNIXInetAddress.unwrapAddress(AFUNIXInetAddress.wrapAddress(bytes)));
  }

  @Test
  public void testHostnameStringEndsWithJunixSocket() throws IOException {
    byte[] bytes = {0, 1, 2, 3, 0x7f, (byte) 0xff};
    assertTrue(AFUNIXInetAddress.wrapAddress(bytes).getHostName().endsWith(".junixsocket"));
  }

  @Test
  public void testHostnameString() throws IOException {
    assertEquals("[/tmp/test.sock.un.junixsocket", AFUNIXInetAddress.wrapAddress("/tmp/test.sock"
        .getBytes(Charset.defaultCharset())).getHostName());
  }

  @Test
  public void testIsLoopbackAddress() throws IOException {
    assertTrue(AFUNIXInetAddress.wrapAddress("/tmp/test.sock".getBytes(Charset.defaultCharset()))
        .isLoopbackAddress());
  }
}
