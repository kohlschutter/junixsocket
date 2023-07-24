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
package org.newsclub.net.unix.darwin.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

public class IPUtilTest {

  @Test
  public void testIPHeaderChecksum() {
    // example from https://www.thegeekstuff.com/2012/05/ip-header-checksum/
    ByteBuffer bb = ByteBuffer.wrap(new byte[] {
        0x45, 0x00, //
        0x00, 0x3c, //
        0x1c, 0x46, //
        0x40, 0x00, //
        0x40, 0x06, //
        0x00, 0x00, //
        (byte) 0xac, 0x10, //
        0x0a, 0x63, //
        (byte) 0xac, 0x10, //
        0x0a, 0x0c});

    assertEquals(0xB1E6, IPUtil.checksumIPv4header(bb, 0, bb.remaining()));
  }
}
