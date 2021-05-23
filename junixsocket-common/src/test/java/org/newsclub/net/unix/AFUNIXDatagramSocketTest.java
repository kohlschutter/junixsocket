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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.newsclub.net.unix.SocketTestBase.newTempFile;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class AFUNIXDatagramSocketTest {
  private static void assertUnconnectedDatagramSocket(AFUNIXDatagramSocket ds) {
    assertFalse(ds.isClosed());
    assertFalse(ds.isConnected());
    assertFalse(ds.isBound());
    assertTrue(ds.getLocalAddress().isAnyLocalAddress());
    assertNull(ds.getLocalSocketAddress());
    assertEquals(0, ds.getLocalPort());
  }

  private static void assertClosedDatagramSocket(AFUNIXDatagramSocket ds) {
    assertTrue(ds.isClosed());
    assertNull(ds.getLocalAddress());
    assertNull(ds.getLocalSocketAddress());
    assertEquals(-1, ds.getLocalPort());
  }

  private static void assertBoundDatagramSocket(AFUNIXDatagramSocket ds,
      AFUNIXSocketAddress boundAddr) throws SocketException {
    assertTrue(ds.isBound());
    assertFalse(ds.isClosed());
    assertEquals(0, ds.getLocalPort());
    assertFalse(ds.getLocalAddress().isAnyLocalAddress());
    assertEquals(boundAddr, AFUNIXSocketAddress.unwrap(ds.getLocalAddress(), 0));
    assertEquals(boundAddr, ds.getLocalSocketAddress());
  }

  private static void assertConnectedDatagramSocket(AFUNIXDatagramSocket ds,
      AFUNIXSocketAddress boundAddr, AFUNIXSocketAddress remoteAddr) throws SocketException {
    assertTrue(ds.isConnected());
    assertFalse(ds.isClosed());
    assertEquals(remoteAddr, ds.getRemoteSocketAddress());
    if (boundAddr != null) {
      assertBoundDatagramSocket(ds, boundAddr);
    } else {
      assertEquals(0, ds.getLocalPort());
    }
  }

  private static void assertDatagramPacketAddress(DatagramPacket dp, AFUNIXSocketAddress addr) {
    assertEquals(addr.wrapAddress(), dp.getAddress());
    assertEquals(new InetSocketAddress(addr.wrapAddress(), addr.getPort()), dp.getSocketAddress());
    assertEquals(0, dp.getPort());
  }

  @Test
  public void testBindConnect() throws SocketException, IOException, InterruptedException {
    AFUNIXSocketAddress ds1Addr = AFUNIXSocketAddress.of(newTempFile());
    AFUNIXSocketAddress ds2Addr = AFUNIXSocketAddress.of(newTempFile());
    assertNotEquals(ds1Addr, ds2Addr);

    try (AFUNIXDatagramSocket ds1 = AFUNIXDatagramSocket.newInstance();
        AFUNIXDatagramSocket ds2 = AFUNIXDatagramSocket.newInstance();) {
      assertUnconnectedDatagramSocket(ds1);

      ds1.bind(ds1Addr);
      assertNull(ds1.getRemoteSocketAddress());

      assertBoundDatagramSocket(ds1, ds1Addr);

      ds2.bind(ds2Addr);
      ds1.connect(ds2Addr);

      assertConnectedDatagramSocket(ds1, ds1Addr, ds2Addr);

      byte[] data = "Hello world!".getBytes(StandardCharsets.UTF_8);
      byte[] buf = new byte[512];
      System.arraycopy(data, 0, buf, 0, data.length);
      DatagramPacket dp1 = new DatagramPacket(buf, data.length);
      ds1.send(dp1);

      DatagramPacket dp2 = new DatagramPacket(new byte[1024], 3, 1021);
      dp2.setPort(123); // should be cleared
      ds2.receive(dp2);

      byte[] received = new byte[dp2.getLength()];
      System.arraycopy(dp2.getData(), dp2.getOffset(), received, 0, dp2.getLength());
      assertArrayEquals(data, received);

      assertDatagramPacketAddress(dp2, ds1Addr);

      // dp2.setSocketAddress(ds1Addr); // doesn't work :-(
      dp2.setAddress(ds1Addr.wrapAddress());
      ds2.send(dp2);

      dp1.setLength(0); // setting this before "receive" doesn't matter
      ds1.receive(dp1);
      assertEquals(ds2Addr.wrapAddress(), dp1.getAddress());

      ds1.close();
      assertClosedDatagramSocket(ds1);

      ds2.close();
      assertClosedDatagramSocket(ds2);
    }
  }

  @Test
  public void testPeek() throws Exception {
    AFUNIXSocketAddress ds2Addr = AFUNIXSocketAddress.of(newTempFile());

    try (AFUNIXDatagramSocket ds1 = AFUNIXDatagramSocket.newInstance();
        AFUNIXDatagramSocket ds2 = AFUNIXDatagramSocket.newInstance();) {
      ds2.bind(ds2Addr);
      DatagramPacket dp1 = //
          AFUNIXDatagramUtil.datagramWithCapacityAndPayload(1024, "Hello".getBytes(
              StandardCharsets.UTF_8));
      dp1.setAddress(ds2Addr.wrapAddress());
      ds1.send(dp1);
      DatagramPacket dp2 = AFUNIXDatagramUtil.datagramWithCapacity(1024);
      ds2.peek(dp2);
      assertEquals("Hello", new String(dp2.getData(), dp2.getOffset(), dp2.getLength(),
          StandardCharsets.UTF_8));
      DatagramPacket dp3 = AFUNIXDatagramUtil.datagramWithCapacity(1024);
      ds2.receive(dp3);
      assertEquals("Hello", new String(dp3.getData(), dp3.getOffset(), dp3.getLength(),
          StandardCharsets.UTF_8));
    }
  }

  @Test
  public void testReadTimeout() throws Exception {
    AFUNIXSocketAddress dsAddr = AFUNIXSocketAddress.of(newTempFile());

    try (AFUNIXDatagramSocket ds = AFUNIXDatagramSocket.newInstance()) {
      ds.setSoTimeout(50);
      ds.bind(dsAddr);
      assertThrows(SocketTimeoutException.class, () -> {
        ds.receive(AFUNIXDatagramUtil.datagramWithCapacity(64));
      });
    }
  }

  @Test
  public void testPeekTimeout() throws Exception {
    AFUNIXSocketAddress dsAddr = AFUNIXSocketAddress.of(newTempFile());

    try (AFUNIXDatagramSocket ds = AFUNIXDatagramSocket.newInstance()) {
      ds.setSoTimeout(50);
      ds.bind(dsAddr);
      assertThrows(SocketTimeoutException.class, () -> {
        ds.peek(AFUNIXDatagramUtil.datagramWithCapacity(64));
      });
    }
  }
}
