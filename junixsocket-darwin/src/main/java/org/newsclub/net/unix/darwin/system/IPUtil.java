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

import java.nio.ByteBuffer;

/**
 * Some IP protocol-related helper methods.
 *
 * @author Christian Kohlschütter
 */
public final class IPUtil {
  /**
   * The length (in bytes) of the "domain" header used in loopback packet systems like UTUN_CONTROL.
   */
  public static final int DOMAIN_HEADER_LENGTH = 4; // bytes

  /**
   * The identifier for AF_INET (at least on Darwin).
   */
  public static final int DOMAIN_AF_INET = 2;

  /**
   * The length (in bytes) of an IPv4 header without options.
   */
  public static final int IPV4_DEFAULT_HEADER_SIZE = 20; // bytes

  /**
   * The ICMP protocol.
   */
  public static final byte AF_INET_PROTOCOL_ICMP = 1;

  private IPUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Computes the checksum for an IPv4 header, and overwrites any existing checksum with the correct
   * one.
   *
   * @param bb The buffer containing the IPv4 header
   * @param start The beginning position of the header in the buffer.
   * @param end The end position (exclusive) of the header in the buffer.
   * @return The computed 16-bit checksum
   */
  public static int checksumIPv4header(ByteBuffer bb, int start, int end) {
    return checksumIPstyle(bb, start, end, 10);
  }

  /**
   * Computes the checksum for an ICMP header, and overwrites any existing checksum with the correct
   * one.
   *
   * Also see <a href="https://datatracker.ietf.org/doc/html/rfc792">RFC 792</a>.
   *
   * @param bb The buffer containing the ICMP header
   * @param start The beginning position of the header in the buffer.
   * @param end The end position (exclusive) of the header in the buffer.
   * @return The computed 16-bit checksum
   */
  public static int checksumICMPheader(ByteBuffer bb, int start, int end) {
    return checksumIPstyle(bb, start, end, 2);
  }

  /**
   * Computes the 16-bit checksum for some header used in IP networking, and overwrites any existing
   * checksum with the correct one.
   *
   * Also see <a href="https://datatracker.ietf.org/doc/html/rfc1071">RFC 1071</a>.
   *
   * @param bb The buffer containing the ICMP header
   * @param start The beginning position of the header in the buffer.
   * @param end The end position (exclusive) of the header in the buffer.
   * @param checksumOffset The offset from start for an existing 16-bit checksum that is to be
   *          ignored.
   * @return The computed 16-bit checksum
   */
  private static int checksumIPstyle(ByteBuffer bb, int start, int end, int checksumOffset) {
    final int checksumAt = start + checksumOffset;
    int sum = 0;

    if (checksumOffset >= end) {
      throw new IllegalArgumentException("checksumOffset");
    }

    // While we could pretend the checksum is 0 (by ignoring the computation at position
    // checksumAt), we zero it out here, and later put the correct checksum back in.
    // This should not only be faster than two for-loops or checking the position prior to adding,
    // it also puts the correct checksum in place, which can come in handy when composing packets.
    // It is also the recommended strategy as per RFC 1071. The downside is that we modify the
    // contents of the buffer, but that's OK since we control the API.
    bb.putShort(checksumAt, (short) 0);

    for (int i = start; i < end; i += 2) {
      int v = bb.getShort(i) & 0xFFFF;

      sum += v;

      int overflow = (sum & ~0xFFFF);
      if (overflow != 0) {
        // overflow -> add carry and trim to 16-bit
        sum = (sum + (overflow >>> 16)) & 0xFFFF;
      }
    }

    int checksum = (~sum) & 0xFFFF;

    // fix checksum
    bb.putShort(checksumAt, (short) checksum);

    return checksum;
  }

  /**
   * Put (write) an IPv4 header to the given byte buffer, using the given parameters.
   *
   * This should write exactly 20 bytes to the buffer. The buffer position then is at the end of the
   * header.
   *
   * @param bb The target byte buffer.
   * @param payloadLength The length of the payload (excluding the IPv4 header).
   * @param protocol The protocol identifier.
   * @param srcIP The source IPv4 address.
   * @param dstIP The destination IPv4 address.
   */
  public static void putIPv4Header(ByteBuffer bb, int payloadLength, byte protocol, int srcIP,
      int dstIP) {
    bb.put((byte) 0x45); // IPv4, 5*4=20 bytes header
    bb.put((byte) 0); // TOS/DSCP
    bb.putShort((short) (20 + payloadLength)); // total length = header + payload
    bb.putShort((short) 0); // identification
    bb.putShort((short) 0); // flags and fragment offset
    bb.put((byte) 65); // TTL
    bb.put(protocol); // protocol (e.g., ICMP)
    bb.putShort((short) 0); // header checksum (placeholder)
    bb.putInt(srcIP);
    bb.putInt(dstIP);
    // end of header (20 bytes)
  }

  /**
   * Put (write) an ICMP echo response header to the given byte buffer, using the given parameters.
   *
   * @param bb The target byte buffer.
   * @param echoIdentifier The identifier, from the ICMP echo request.
   * @param sequenceNumber The sequence number, from the ICMP echo request.
   * @param payload The payload, from the ICMP echo request.
   */
  public static void putICMPEchoResponse(ByteBuffer bb, short echoIdentifier, short sequenceNumber,
      ByteBuffer payload) {
    bb.put((byte) 0); // Echo response
    bb.put((byte) 0); // Echo has no other code
    bb.putShort((short) 0); // ICMP checksum (placeholder)
    bb.putShort(echoIdentifier); // ICMP echo identifier
    bb.putShort(sequenceNumber); // ICMP echo sequence number
    bb.put(payload);
  }
}
