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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

public class AFUNIXSocketPairTest {
  // CPD-OFF

  @Test
  public void testSocketPair() throws Exception {
    AFUNIXSocketPair<AFUNIXSocketChannel> pair = AFUNIXSocketPair.open();

    AFUNIXSocketChannel sc1 = pair.getSocket1();
    AFUNIXSocketChannel sc2 = pair.getSocket2();

    assertTrue(sc1.isConnected());
    assertTrue(sc2.isConnected());

    assertNotEquals(pair.getSocket1(), pair.getSocket2());
    assertNotEquals(pair.getSocket1().socket(), pair.getSocket2().socket());

    assertEquals(sc1.getPeerCredentials(), sc2.getPeerCredentials());

    ByteBuffer bb = ByteBuffer.allocate(4096);
    bb.putInt(0x04030201);
    bb.flip();
    sc1.write(bb);

    ByteBuffer bb2 = ByteBuffer.allocate(4096);
    sc2.read(bb2);
    bb2.flip();
    assertEquals(0x04030201, bb2.getInt());

    assertNull(pair.getSocket1().getLocalAddress());
    assertNull(pair.getSocket2().getLocalAddress());
    assertNull(pair.getSocket1().getRemoteAddress());
    assertNull(pair.getSocket1().getRemoteAddress());
  }

  @Test
  public void testDatagramPair() throws Exception {
    AFUNIXSocketPair<AFUNIXDatagramChannel> pair = AFUNIXSocketPair.openDatagram();

    AFUNIXDatagramChannel sc1 = pair.getSocket1();
    AFUNIXDatagramChannel sc2 = pair.getSocket2();

    assertTrue(sc1.isConnected());
    assertTrue(sc2.isConnected());

    assertNotEquals(pair.getSocket1(), pair.getSocket2());
    assertNotEquals(pair.getSocket1().socket(), pair.getSocket2().socket());

    assertEquals(sc1.getPeerCredentials(), sc2.getPeerCredentials());

    ByteBuffer bb = ByteBuffer.allocate(4096);
    bb.putInt(0x04030201);
    bb.flip();
    sc1.write(bb);

    ByteBuffer bb2 = ByteBuffer.allocate(4096);
    sc2.read(bb2);
    bb2.flip();
    assertEquals(0x04030201, bb2.getInt());

    assertNull(pair.getSocket1().getLocalAddress());
    assertNull(pair.getSocket2().getLocalAddress());
    assertNull(pair.getSocket1().getRemoteAddress());
    assertNull(pair.getSocket1().getRemoteAddress());
  }
}
