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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class SocketPairTest<A extends SocketAddress> extends SocketTestBase<A> {
  // CPD-OFF

  protected SocketPairTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  private static void assertCovered(SocketAddress addr, SocketAddress covered) {
    if (addr instanceof AFSocketAddress && covered instanceof AFSocketAddress) {
      assertCovered((AFSocketAddress) addr, (AFSocketAddress) covered);
    } else {
      assertEquals(addr, covered);
    }
  }

  private static void assertCovered(AFSocketAddress addr, AFSocketAddress covered) {
    if (!addr.covers(covered)) {
      fail("Address not covered by " + addr + ": " + covered);
    }
  }

  @Test
  public void testSocketPair() throws Exception {
    CloseablePair<? extends SocketChannel> pair = newSocketPair();

    SocketChannel sc1 = pair.getFirst();
    SocketChannel sc2 = pair.getSecond();

    Objects.requireNonNull(sc1);
    Objects.requireNonNull(sc2);

    assertTrue(sc1.isConnected());
    assertTrue(sc2.isConnected());

    assertNotEquals(pair.getFirst(), pair.getSecond());
    assertNotEquals(pair.getFirst().socket(), pair.getSecond().socket());

    if (sc1 instanceof AFUNIXSocketChannel) {
      assertEquals(((AFUNIXSocketChannel) sc1).getPeerCredentials(), ((AFUNIXSocketChannel) sc2)
          .getPeerCredentials());
    }

    ByteBuffer bb = ByteBuffer.allocate(4096);
    bb.putInt(0x04030201);
    bb.flip();
    sc1.write(bb);

    ByteBuffer bb2 = ByteBuffer.allocate(4096);
    sc2.read(bb2);
    bb2.flip();
    assertEquals(0x04030201, bb2.getInt());

    if (getServerBindAddress() instanceof AFUNIXSocketAddress) {
      assertNull(pair.getFirst().getLocalAddress());
      assertNull(pair.getSecond().getLocalAddress());
      assertNull(pair.getFirst().getRemoteAddress());
      assertNull(pair.getSecond().getRemoteAddress());
    } else {
      assertCovered(pair.getFirst().getLocalAddress(), pair.getSecond().getRemoteAddress());
      assertCovered(pair.getSecond().getLocalAddress(), pair.getFirst().getRemoteAddress());
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
  public void testDatagramPair() throws Exception {
    CloseablePair<? extends DatagramChannel> pair = newDatagramSocketPair();

    DatagramChannel sc1 = pair.getFirst();
    DatagramChannel sc2 = pair.getSecond();

    Objects.requireNonNull(sc1);
    Objects.requireNonNull(sc2);

    assertTrue(sc1.isConnected());
    assertTrue(sc2.isConnected());

    assertNotEquals(pair.getFirst(), pair.getSecond());
    assertNotEquals(pair.getFirst().socket(), pair.getSecond().socket());

    if (sc1 instanceof AFUNIXDatagramChannel) {
      assertEquals(((AFUNIXDatagramChannel) sc1).getPeerCredentials(), ((AFUNIXDatagramChannel) sc2)
          .getPeerCredentials());
    }

    ByteBuffer bb = ByteBuffer.allocate(4096);
    bb.putInt(0x04030201);
    bb.flip();
    sc1.write(bb);

    ByteBuffer bb2 = ByteBuffer.allocate(4096);
    sc2.read(bb2);
    bb2.flip();
    assertEquals(0x04030201, bb2.getInt());

    if (getServerBindAddress() instanceof AFUNIXSocketAddress) {
      assertNull(pair.getFirst().getLocalAddress());
      assertNull(pair.getSecond().getLocalAddress());
      assertNull(pair.getFirst().getRemoteAddress());
      assertNull(pair.getSecond().getRemoteAddress());
    } else {
      assertCovered(pair.getFirst().getLocalAddress(), pair.getSecond().getRemoteAddress());
      assertCovered(pair.getSecond().getLocalAddress(), pair.getFirst().getRemoteAddress());
    }
  }
}
