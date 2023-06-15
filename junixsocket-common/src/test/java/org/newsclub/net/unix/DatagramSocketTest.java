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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class DatagramSocketTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected DatagramSocketTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  private void assertUnconnectedDatagramSocket(DatagramSocket ds) {
    assertFalse(ds.isClosed());
    assertFalse(ds.isConnected());
    assertEquals(0, ds.getLocalPort());
    assertUnconnectedUnbound(ds);
  }

  protected void assertUnconnectedUnbound(DatagramSocket ds) {
    assertFalse(ds.isBound());
    assertTrue(ds.getLocalAddress().isAnyLocalAddress());
    assertNull(ds.getLocalSocketAddress());
  }

  private static void assertClosedDatagramSocket(DatagramSocket ds) {
    assertTrue(ds.isClosed());
    assertNull(ds.getLocalAddress());
    assertNull(ds.getLocalSocketAddress());
    assertEquals(-1, ds.getLocalPort());
  }

  private void assertBoundDatagramSocket(DatagramSocket ds, SocketAddress boundAddr)
      throws SocketException {
    assertTrue(ds.isBound());
    assertFalse(ds.isClosed());
    assertEquals(0, ds.getLocalPort());
    assertFalse(ds.getLocalAddress().isAnyLocalAddress());
    assertBoundAddrIdenticalToLocalAddress(ds, boundAddr);
  }

  protected void assertBoundAddrIdenticalToLocalAddress(DatagramSocket ds, SocketAddress boundAddr)
      throws SocketException {
    assertEquals(boundAddr, unwrap(ds.getLocalAddress(), 0));
    assertEquals(boundAddr, ds.getLocalSocketAddress());
  }

  private void assertConnectedDatagramSocket(DatagramSocket ds, SocketAddress boundAddr,
      SocketAddress remoteAddr) throws SocketException {
    assertTrue(ds.isConnected());
    assertFalse(ds.isClosed());
    assertRemoteAddress(ds, remoteAddr);
    if (boundAddr != null) {
      assertBoundDatagramSocket(ds, boundAddr);
    } else {
      assertEquals(0, ds.getLocalPort());
    }
  }

  protected void assertRemoteAddress(DatagramSocket ds, SocketAddress remoteAddr)
      throws SocketException {
    assertEquals(remoteAddr, ds.getRemoteSocketAddress());
  }

  private static void assertDatagramPacketAddress(DatagramPacket dp, SocketAddress addr0) {
    AFSocketAddress addr = (AFSocketAddress) addr0;
    assertEquals(addr.wrapAddress(), dp.getAddress());
    assertEquals(new InetSocketAddress(addr.wrapAddress(), addr.getPort()), dp.getSocketAddress());
    assertEquals(0, dp.getPort());
  }

  @Test
  public void testBindConnect() throws SocketException, IOException, InterruptedException {
    AFSocketAddress ds1Addr = (AFSocketAddress) newTempAddressForDatagram();
    AFSocketAddress ds2Addr = (AFSocketAddress) newTempAddressForDatagram();

    try (DatagramSocket ds1 = newDatagramSocket(); DatagramSocket ds2 = newDatagramSocket()) {
      assertUnconnectedDatagramSocket(ds1);

      ds1.bind(ds1Addr);
      ds1Addr = (AFSocketAddress) ds1.getLocalSocketAddress();
      assertNull(ds1.getRemoteSocketAddress());

      assertBoundDatagramSocket(ds1, ds1Addr);

      if (!ds2.isBound()) {
        ds2.bind(ds2Addr);
        ds2Addr = (AFSocketAddress) ds2.getLocalSocketAddress();
      }

      assertNotEquals(ds1Addr, ds2Addr);

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

      dp1.setLength(100); // maximum package length
      ds1.receive(dp1);
      assertEquals(12, dp1.getLength());
      assertEquals(ds2Addr.wrapAddress(), dp1.getAddress());

      ds1.close();
      assertClosedDatagramSocket(ds1);

      ds2.close();
      assertClosedDatagramSocket(ds2);
    }
  }

  @Test
  public void testReadTimeout() throws IOException {
    SocketAddress dsAddr = newTempAddressForDatagram();

    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      try (DatagramSocket ds = newDatagramSocket()) {
        ds.setSoTimeout(50);
        ds.bind(dsAddr);
        assertThrows(SocketTimeoutException.class, () -> {
          ds.receive(AFDatagramUtil.datagramWithCapacity(64));
        });
      }
    });
  }

  @Test
  public void testPeekTimeout() throws IOException {
    SocketAddress dsAddr = newTempAddressForDatagram();

    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      try (AFDatagramSocket<?> ds = (AFDatagramSocket<?>) newDatagramSocket()) {
        ds.setSoTimeout(50);
        ds.bind(dsAddr);
        assertThrows(SocketTimeoutException.class, () -> {
          DatagramPacket dp = AFDatagramUtil.datagramWithCapacity(64);
          ds.peek(dp);
        });
      }
    });
  }
}
