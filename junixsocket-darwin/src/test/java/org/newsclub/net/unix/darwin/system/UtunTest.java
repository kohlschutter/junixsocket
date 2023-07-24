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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSYSTEMSocketAddress;
import org.newsclub.net.unix.AFSYSTEMSocketAddress.SysAddr;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;

import com.kohlschutter.testutil.ExecutionEnvironmentRequirement;
import com.kohlschutter.testutil.ExecutionEnvironmentRequirement.Rule;

/**
 * Demo code to exercise AF_SYSTEM with UTUN_CONTROL.
 *
 * Creates a PtP VPN tunnel, sends a ping via Java SDK code, parses the ICMP echo request (ping)
 * packet, and responds with a hand-crafted ICMP echo reply (pong).
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class UtunTest {
  private static final Inet4Address UTUN_SRC_IP;
  private static final Inet4Address UTUN_DST_IP;

  static {
    try {
      UTUN_SRC_IP = (Inet4Address) InetAddress.getByName("169.254.3.4"); // "this host"
      UTUN_DST_IP = (Inet4Address) InetAddress.getByName("169.254.3.5"); // "other end"
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Dummy method to indicate the given parameter is not checked by our test code.
   *
   * @param v The parameter.
   * @return The parameter.
   */
  private static Object unchecked(Object v) {
    return v;
  }

  /**
   * Returns the given IPv4 address as an integer.
   *
   * @param addr The IPv4 address object.
   * @return The integer.
   */
  private static int getAddressAsInt(Inet4Address addr) {
    // In the JDK implementation of Inet4Address, this happens to be the hash code.
    return addr.hashCode();
  }

  @SuppressWarnings({
      "checkstyle:VariableDeclarationUsageDistance", "PMD.JUnitTestContainsTooManyAsserts",
      "PMD.AvoidBranchingStatementAsLastInLoop"})
  @Test
  @ExecutionEnvironmentRequirement(root = Rule.REQUIRED)
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_DARWIN)
  public void testTunnelPingPong() throws Exception {
    try (AFSYSTEMDatagramSocket socket = AFSYSTEMDatagramSocket.newInstance()) {
      int id = socket.getNodeIdentity(WellKnownKernelControlNames.UTUN_CONTROL);

      // NOTE: Connecting requires root privileges, but we could do that in a separate process
      // and send the socket FD via AF_UNIX to a non-privileged helper process.
      try {
        socket.connect(AFSYSTEMSocketAddress.ofSysAddrIdUnit(SysAddr.AF_SYS_CONTROL, id, 0));
      } catch (SocketException e) {
        assumeTrue(false, "Could not connect to UTUN_CONTROL: " + e);
        return;
      }

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {

        AFSYSTEMSocketAddress rsa = socket.getRemoteSocketAddress();
        Objects.requireNonNull(rsa);

        assertEquals(SysAddr.AF_SYS_CONTROL, rsa.getSysAddr());
        assertEquals(id, rsa.getId());
        assertNotEquals(0, rsa.getUnit()); // utunN: N=(unit-1), e.g., unit=9 -> utun8

        String utun = "utun" + (rsa.getUnit() - 1);
        // System.out.println(utun);

        int rcIfconfig = Runtime.getRuntime().exec(new String[] {
            "/sbin/ifconfig", utun, UTUN_SRC_IP.getHostAddress(), UTUN_DST_IP.getHostAddress()})
            .waitFor();
        assertEquals(0, rcIfconfig, "Could not set IP address for " + utun);

        AFSYSTEMDatagramChannel channel = socket.getChannel();
        ByteBuffer bb = ByteBuffer.allocateDirect(1500).order(ByteOrder.BIG_ENDIAN);

        CompletableFuture<Boolean> ping = CompletableFuture.supplyAsync(() -> {
          try {
            return UTUN_DST_IP.isReachable(1000);
          } catch (IOException e) {
            e.printStackTrace();
            return false;
          }
        });

        while (channel.read(bb) >= 0) {
          bb.flip();

          // Request: Domain (AF_INET) + IPv4 header + ICMP header + ICMP payload

          int totalSize = bb.remaining();
          // assertEquals(76, totalSize); // 4 byte domain header + 72 bytes packet length

          int domain = bb.getInt();
          assertEquals(IPUtil.DOMAIN_AF_INET, domain, "Expect domain 2 (AF_INET)");

          int ipHeaderStartPos = bb.position();

          int versionAndIHL = bb.get() & 0xFF;
          int version = versionAndIHL >> 4;
          assertEquals(4, version, "expect IPv4 packet");

          // see https://en.wikipedia.org/wiki/Internet_Protocol_version_4#Header

          int ihl = versionAndIHL & 0b1111;
          int ihlBytes = ihl * 32 /* bit */ / 8;
          assertTrue(ihlBytes >= 20, "expect (at least) 20 bytes header length");

          int tosDSCP = (bb.get() & 0xFF);
          unchecked(tosDSCP);

          int totalLen = (bb.getShort() & 0xFFFF);
          assertEquals(totalSize - 4, totalLen);

          int identification = (bb.getShort() & 0xFFFF);
          unchecked(identification);

          int flagsAndFragmentOffset = (bb.getShort() & 0xFFFF);
          int flags = flagsAndFragmentOffset >> 13;
          int fragmentOffset = flagsAndFragmentOffset & 0b1_1111_1111_1111;
          assertEquals(0, flags);
          assertEquals(0, fragmentOffset);

          int ttl = bb.get() & 0xFF;
          assertNotEquals(0, ttl); // e.g., 65

          int protocol = bb.get() & 0xFF;
          assertEquals(IPUtil.AF_INET_PROTOCOL_ICMP, protocol); // 1 == ICMP

          int headerChecksum = bb.getShort() & 0xFFFF;
          // see below for verification

          int srcIP = bb.getInt();
          int dstIP = bb.getInt();

          assertEquals(getAddressAsInt(UTUN_SRC_IP), srcIP); // 10.250.3.4
          assertEquals(getAddressAsInt(UTUN_DST_IP), dstIP); // 10.250.3.5

          // when ihl=5 -> ihlBytes=ihl*4=20, there are no more options
          // but let's check nevertheless...

          int remainingHeaderLength = ihlBytes - 20;
          if (remainingHeaderLength > 0) {
            System.err.println("Warning: Found unexpected Options section in IPv4 header; len="
                + remainingHeaderLength);
            bb.position(bb.position() + remainingHeaderLength);
          }

          // we're at the end of the IPv4 header

          int computedHeaderChecksum = IPUtil.checksumIPv4header(bb, ipHeaderStartPos, bb
              .position());
          assertEquals(computedHeaderChecksum, headerChecksum);

          int icmpSize = bb.remaining();
          // assertEquals(52, icmpSize); // ICMP header + optional data

          int icmpBeginPosition = bb.position();

          // begin ICMP header
          int icmpType = bb.get() & 0xFF;
          assertEquals(8, icmpType); // 8 = Echo Request

          int icmpCode = bb.get() & 0xFF;
          assertEquals(0, icmpCode); // Echo Request has no other Code

          int icmpChecksum = bb.getShort() & 0xFFFF; // checked below

          int icmpEchoIdentifier = bb.getShort() & 0xFFFF;
          int icmpEchoSequenceNumber = bb.getShort() & 0xFFFF;

          unchecked(icmpEchoIdentifier);
          assertEquals(1, icmpEchoSequenceNumber); // first echo packet

          int icmpChecksumComputed = //
              IPUtil.checksumICMPheader(bb, icmpBeginPosition, bb.position() + bb.remaining());
          assertEquals(icmpChecksumComputed, icmpChecksum);

          // Now it's time to craft an echo response
          // Response: "AF_INET" + IPv4 header + ICMP header + ICMP payload from echo request

          ByteBuffer response = ByteBuffer.allocate(IPUtil.DOMAIN_HEADER_LENGTH
              + IPUtil.IPV4_DEFAULT_HEADER_SIZE + icmpSize).order(ByteOrder.BIG_ENDIAN);
          response.putInt(IPUtil.DOMAIN_AF_INET);
          IPUtil.putIPv4Header(response, icmpSize, IPUtil.AF_INET_PROTOCOL_ICMP, dstIP, srcIP);

          int responsePayloadStart = response.position();
          IPUtil.checksumIPv4header(response, IPUtil.DOMAIN_HEADER_LENGTH, responsePayloadStart);

          IPUtil.putICMPEchoResponse(response, (short) icmpEchoIdentifier,
              (short) icmpEchoSequenceNumber, bb);
          assertEquals(0, bb.remaining()); // writeEchoResponse consumed the payload
          int responsePayloadEnd = response.position();

          IPUtil.checksumICMPheader(response, responsePayloadStart, responsePayloadEnd);

          response.flip();
          int written = channel.write(response);
          bb.clear();

          assertEquals(response.capacity(), written);

          assertTrue(ping.get(1, TimeUnit.SECONDS));

          return;
        }

        fail("Nothing received");
      });
    }
  }
}
