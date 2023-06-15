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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * A workaround to create an {@link InetAddress} for an {@link AFSocketAddress}.
 *
 * {@link DatagramPacket} internally requires InetAddress compatibility. Even if it pretends to
 * accept {@link SocketAddress}es, it refuses anything other than {@link InetSocketAddress}
 * <em>and</em> then even stores host and port separately.
 *
 * This implementation deserializes a specially crafted {@link InetAddress} with a hostname that
 * encodes the raw bytes of an {@link AFSocketAddress}. We do this because the deserialization code
 * path does not attempt DNS resolution (which would fail one way or another).
 *
 * The hostnames we use end with ".junixsocket", to distinguish them from regular hostnames.
 *
 * @author Christian Kohlschütter
 */
class AFInetAddress {
  private static final byte[] SERIALIZED_INET_ADDRESS_START = {
      (byte) 0xac, (byte) 0xed, // STREAM_MAGIC
      0x00, 0x05, // STREAM_VERSION
      0x73, // TC_OBJECT
      0x72, // TC_CLASSDESC,
      0x00, 0x14, // length
      'j', 'a', 'v', 'a', '.', 'n', 'e', 't', '.', //
      'I', 'n', 'e', 't', 'A', 'd', 'd', 'r', 'e', 's', 's', // "java.net.InetAddress"

      0x2d, (byte) 0x9b, 0x57, (byte) 0xaf, (byte) 0x9f, (byte) 0xe3, (byte) 0xeb, (byte) 0xdb, //
      // serialVersionUID for java.net.InetAddress

      0x03, // classDescFlags SC_WRITE_METHOD | SC_SERIALIZABLE
      0x00, 0x03, // fieldCount (3)
      'I', // int field
      0x00, 0x07, // length (7)
      'a', 'd', 'd', 'r', 'e', 's', 's', // "address"
      'I', // int field
      0x00, 0x06, // length (6)
      'f', 'a', 'm', 'i', 'l', 'y', // "family"
      'L', // Object field
      0x00, 0x08, // length (8)
      'h', 'o', 's', 't', 'N', 'a', 'm', 'e', // "hostName"
      0x74, // (className1) TC_STRING
      0x00, 0x12, // length (18)
      'L', 'j', 'a', 'v', 'a', '/', 'l', 'a', 'n', 'g', '/', //
      'O', 'b', 'j', 'e', 'c', 't', ';', // "Ljava/lang/Object;"
      0x78, // TC_ENDBLOCKDATA,
      0x70, // (superClassDesc) TC_NULL
      0x7f, 0x00, 0x00, (byte) 0xaf, // "address" value: (int) 127.0.0.175 (0x7f0000af)
      0x00, 0x00, 0x00, 0x01, // "family" value: (int) 1 (IPv4)
      0x74, // "hostName" value is a TC_STRING
      0x00, // high-byte of string length (always 0 in our case)
      // low-byte of string length and string itself will be appended later
      // followed by 0x78 (TC_ENDBLOCKDATA)
  };

  private static final char PREFIX = '[';
  private static final String MARKER_HEX_ENCODING = "%%";
  static final String INETADDR_SUFFIX = ".junixsocket";

  /**
   * Encodes a junixsocket socketAddress into a string that is (somewhat) guaranteed to not be
   * resolved by java.net code.
   *
   * Implementation detail: The "[" prefix (with the corresponding "]" suffix missing from the
   * input) should cause an early {@link UnknownHostException} be thrown, which is caught within
   * {@link InetSocketAddress#InetSocketAddress(String, int)}, causing the hostname be marked as
   * "unresolved" (without an address set).
   *
   * @param socketAddress The socket address.
   * @return A string, to be used when calling
   *         {@link InetSocketAddress#InetSocketAddress(String, int)}, etc.
   */
  static final String createUnresolvedHostname(byte[] socketAddress, AFAddressFamily<?> af) {
    StringBuilder sb = new StringBuilder(1 + socketAddress.length + INETADDR_SUFFIX.length() + 8);
    sb.append(PREFIX);
    try {
      sb.append(URLEncoder.encode(new String(socketAddress, StandardCharsets.ISO_8859_1),
          StandardCharsets.ISO_8859_1.toString()));
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
    sb.append('.');
    sb.append(af.getJuxString());
    sb.append(INETADDR_SUFFIX);

    String str = sb.toString();

    if (str.length() < 64 || str.getBytes(StandardCharsets.UTF_8).length <= 255) {
      return str;
    }

    sb.setLength(0);
    sb.append(PREFIX);
    sb.append(MARKER_HEX_ENCODING);
    for (int i = 0, n = socketAddress.length; i < n; i++) {
      sb.append(String.format(Locale.ENGLISH, "%02x", socketAddress[i]));
    }

    sb.append('.');
    sb.append(af.getJuxString());
    sb.append(INETADDR_SUFFIX);
    return sb.toString();
  }

  /**
   * Creates an InetAddress that is considered "resolved" internally (using a static loopback
   * address), without actually having to resolve the address via DNS, thus still carrying the
   * "hostname" field containing a hostname as returned by
   * {@link #createUnresolvedHostname(byte[])}.
   *
   * @param socketAddress The socket address.
   * @return The {@link InetAddress}.
   */
  static final InetAddress wrapAddress(byte[] socketAddress, AFAddressFamily<?> af) {
    Objects.requireNonNull(af);
    if (socketAddress == null || socketAddress.length == 0) {
      return null;
    }

    byte[] bytes = createUnresolvedHostname(socketAddress, af).getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 255) {
      throw new IllegalStateException("junixsocket address is too long to wrap as InetAddress");
    }
    byte[] serializedData = new byte[SERIALIZED_INET_ADDRESS_START.length + 1 + bytes.length + 1];
    System.arraycopy(SERIALIZED_INET_ADDRESS_START, 0, serializedData, 0,
        SERIALIZED_INET_ADDRESS_START.length);
    serializedData[SERIALIZED_INET_ADDRESS_START.length] = (byte) bytes.length;
    System.arraycopy(bytes, 0, serializedData, SERIALIZED_INET_ADDRESS_START.length + 1,
        bytes.length);
    serializedData[serializedData.length - 1] = 0x78; // TC_ENDBLOCKDATA

    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData))) {
      return (InetAddress) ois.readObject();
    } catch (ClassNotFoundException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static final byte[] unwrapAddress(InetAddress addr, AFAddressFamily<?> af)
      throws SocketException {
    Objects.requireNonNull(addr);

    if (!isSupportedAddress(addr, af)) {
      throw new SocketException("Unsupported address");
    }

    String hostname = addr.getHostName();
    try {
      return unwrapAddress(hostname, af);
    } catch (IllegalArgumentException e) {
      throw (SocketException) new SocketException("Unsupported address").initCause(e);
    }
  }

  static final byte[] unwrapAddress(String hostname, AFAddressFamily<?> af) throws SocketException {
    Objects.requireNonNull(hostname);
    if (!hostname.endsWith(INETADDR_SUFFIX)) {
      throw new SocketException("Unsupported address");
    }

    final int end = hostname.length() - INETADDR_SUFFIX.length();
    char c;
    int domDot = -1;
    for (int i = end - 1; i >= 0; i--) {
      c = hostname.charAt(i);
      if (c == '.') {
        domDot = i;
        break;
      }
    }

    String juxString = hostname.substring(domDot + 1, end);
    if (AFAddressFamily.getAddressFamily(juxString) != af) { // NOPMD
      throw new SocketException("Incompatible address");
    }

    String encodedHostname = hostname.substring(1, domDot);
    if (encodedHostname.startsWith(MARKER_HEX_ENCODING)) {
      // Hex-only encoding
      int len = encodedHostname.length();
      if ((len & 1) == 1) {
        throw new IllegalStateException("Length of hex-encoded wrapping must be even");
      }
      byte[] unwrapped = new byte[(len - 2) / 2];
      for (int i = 2, n = encodedHostname.length(), o = 0; i < n; i += 2, o++) {
        int v = Integer.parseInt(encodedHostname.substring(i, i + 2), 16);
        unwrapped[o] = (byte) (v & 0xFF);
      }
      return unwrapped;
    } else {
      // URL-encoding
      try {
        return URLDecoder.decode(encodedHostname, StandardCharsets.ISO_8859_1.toString()).getBytes(
            StandardCharsets.ISO_8859_1);
      } catch (UnsupportedEncodingException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  static boolean isSupportedAddress(InetAddress addr, AFAddressFamily<?> af) {
    if (addr instanceof Inet4Address && addr.isLoopbackAddress()) {
      String hostname = addr.getHostName();
      return hostname.endsWith(af.getJuxInetAddressSuffix());
    }
    return false;
  }
}
