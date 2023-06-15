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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link AFSocketAddress} for VSOCK sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFVSOCKSocketAddress extends AFSocketAddress {
  private static final long serialVersionUID = 1L;

  private static final Pattern PAT_VSOCK_URI_HOST_AND_PORT = Pattern.compile(
      "^(?<port>any|[0-9a-fx\\-]+)(\\.(?<cid>any|hypervisor|local|host|[0-9a-fx\\-]+))?(?:\\:(?<javaPort>[0-9]+))?$");

  private static AFAddressFamily<AFVSOCKSocketAddress> afVsock;

  /**
   * "Any address for binding".
   */
  public static final int VMADDR_CID_ANY = -1;

  /**
   * Reserved for services built into the hypervisor.
   */
  public static final int VMADDR_CID_HYPERVISOR = 0;

  /**
   * The well-known address for local communication (loopback).
   */
  public static final int VMADDR_CID_LOCAL = 1;

  /**
   * The well-known address of the host.
   */
  public static final int VMADDR_CID_HOST = 2;

  /**
   * Any port number for binding.
   */
  public static final int VMADDR_PORT_ANY = -1;

  private AFVSOCKSocketAddress(int port, final byte[] socketAddress, ByteBuffer nativeAddress)
      throws SocketException {
    super(port, socketAddress, nativeAddress, addressFamily());
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} that refers to a given VSOCK port and CID; the "java
   * port" is set to -1.
   *
   * @param port The VSOCK port
   * @param cid The CID.
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofPortAndCID(int port, int cid) throws SocketException {
    return ofPortAndCID(-1, port, cid);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} that refers to a given VSOCK port on the hypervisor;
   * the "java port" is set to -1.
   *
   * @param port The VSOCK port
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofHypervisorPort(int port) throws SocketException {
    return ofPortAndCID(port, VMADDR_CID_HYPERVISOR);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress}, especially useful for binding, that refers to "any"
   * port on the hypervisor; the "java port" is set to -1.
   *
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofAnyHypervisorPort() throws SocketException {
    return ofPortAndCID(VMADDR_PORT_ANY, VMADDR_CID_HYPERVISOR);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} that refers to the given port with the local/loopback
   * CID; the "java port" is set to -1.
   *
   * @param port The VSOCK port.
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofLocalPort(int port) throws SocketException {
    return ofPortAndCID(port, VMADDR_CID_LOCAL);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress}, especially useful for binding, that refers to "any"
   * port with the local/loopback CID; the "java port" is set to -1.
   *
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofAnyLocalPort() throws SocketException {
    return ofPortAndCID(VMADDR_PORT_ANY, VMADDR_CID_LOCAL);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} that refers to a given VSOCK port on the host; the
   * "java port" is set to -1.
   *
   * @param port The VSOCK port
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofHostPort(int port) throws SocketException {
    return ofPortAndCID(port, VMADDR_CID_HOST);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress}, especially useful for binding, that refers to "any"
   * port on the host; the "java port" is set to -1.
   *
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofAnyHostPort() throws SocketException {
    return ofPortAndCID(VMADDR_PORT_ANY, VMADDR_CID_HOST);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress}, especially useful for binding, that refers to "any"
   * port and CID; the "java port" is set to -1.
   *
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofAnyPort() throws SocketException {
    return ofPortAndCID(VMADDR_PORT_ANY, VMADDR_CID_ANY);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress}, especially useful for binding, that refers to the
   * given port with "any CID"; the "java port" is set to -1.
   *
   * @param port The VSOCK port.
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofPortWithAnyCID(int port) throws SocketException {
    return ofPortAndCID(port, VMADDR_CID_ANY);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} that refers to a given port and CID.
   *
   * @param javaPort The Java port number.
   * @param vsockPort The vsock port.
   * @param cid The CID.
   * @return A corresponding {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFVSOCKSocketAddress ofPortAndCID(int javaPort, int vsockPort, int cid)
      throws SocketException {
    return resolveAddress(toBytes(vsockPort, cid), javaPort, addressFamily());
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} given a special {@link InetAddress} that encodes the
   * byte sequence of an AF_VSOCK socket address, like those returned by {@link #wrapAddress()}.
   *
   * @param address The "special" {@link InetAddress}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFVSOCKSocketAddress unwrap(InetAddress address, int port) throws SocketException {
    return AFSocketAddress.unwrap(address, port, addressFamily());
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} given a special {@link InetAddress} hostname that
   * encodes the byte sequence of an AF_VSOCK socket address, like those returned by
   * {@link #wrapAddress()}.
   *
   * @param hostname The "special" hostname, as provided by {@link InetAddress#getHostName()}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFVSOCKSocketAddress unwrap(String hostname, int port) throws SocketException {
    return AFSocketAddress.unwrap(hostname, port, addressFamily());
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} given a generic {@link SocketAddress}.
   *
   * @param address The address to unwrap.
   * @return The {@link AFVSOCKSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFVSOCKSocketAddress unwrap(SocketAddress address) throws SocketException {
    Objects.requireNonNull(address);
    if (!isSupportedAddress(address)) {
      throw new SocketException("Unsupported address");
    }
    return (AFVSOCKSocketAddress) address;
  }

  /**
   * Returns the "VSOCK port" part of this address.
   *
   * @return The VSOCK port identifier
   * @see #getPort()
   */
  public int getVSOCKPort() {
    ByteBuffer bb = ByteBuffer.wrap(getBytes());
    int a = bb.getInt(1 * 4);
    return a;
  }

  /**
   * Returns the "VSOCK CID" part of this address.
   *
   * @return The VSOCK CID identifier.
   */
  public int getVSOCKCID() {
    ByteBuffer bb = ByteBuffer.wrap(getBytes());
    int a = bb.getInt(2 * 4);
    return a;
  }

  /**
   * Returns the "VSOCK reserved1" part of this address.
   *
   * @return The "reserved1" identifier, which should be 0.
   */
  public int getVSOCKReserved1() {
    ByteBuffer bb = ByteBuffer.wrap(getBytes());
    int a = bb.getInt(0 * 4);
    return a;
  }

  @Override
  public String toString() {
    int port = getPort();

    byte[] bytes = getBytes();
    if (bytes.length != (3 * 4)) {
      return getClass().getName() + "[" + (port == 0 ? "" : "port=" + port) + ";UNKNOWN" + "]";
    }

    ByteBuffer bb = ByteBuffer.wrap(bytes);
    int reserved1 = bb.getInt();
    int vsockPort = bb.getInt();
    int cid = bb.getInt();

    String vsockPortString;
    if (vsockPort >= -1) {
      vsockPortString = Integer.toString(vsockPort);
    } else {
      vsockPortString = String.format(Locale.ENGLISH, "0x%08x", vsockPort);
    }

    String typeString = (reserved1 == 0 ? "" : "reserved1=" + reserved1 + ";") + "vsockPort="
        + vsockPortString + ";cid=" + cid;

    return getClass().getName() + "[" + (port == 0 ? "" : "port=" + port + ";") + typeString + "]";
  }

  @Override
  public boolean hasFilename() {
    return false;
  }

  @Override
  public File getFile() throws FileNotFoundException {
    throw new FileNotFoundException("no file");
  }

  /**
   * Checks if an {@link InetAddress} can be unwrapped to an {@link AFVSOCKSocketAddress}.
   *
   * @param addr The instance to check.
   * @return {@code true} if so.
   * @see #wrapAddress()
   * @see #unwrap(InetAddress, int)
   */
  public static boolean isSupportedAddress(InetAddress addr) {
    return AFSocketAddress.isSupportedAddress(addr, addressFamily());
  }

  /**
   * Checks if a {@link SocketAddress} can be unwrapped to an {@link AFVSOCKSocketAddress}.
   *
   * @param addr The instance to check.
   * @return {@code true} if so.
   * @see #unwrap(InetAddress, int)
   */
  public static boolean isSupportedAddress(SocketAddress addr) {
    return (addr instanceof AFVSOCKSocketAddress);
  }

  @SuppressWarnings("cast")
  private static byte[] toBytes(int port, int cid) {
    ByteBuffer bb = ByteBuffer.allocate(3 * 4);
    bb.putInt(0); // svm_reserved1
    bb.putInt(port); // svm_port
    bb.putInt(cid); // svm_cid
    return (byte[]) bb.flip().array();
  }

  /**
   * Returns the corresponding {@link AFAddressFamily}.
   *
   * @return The address family instance.
   */
  @SuppressWarnings("null")
  public static synchronized AFAddressFamily<AFVSOCKSocketAddress> addressFamily() {
    if (afVsock == null) {
      afVsock = AFAddressFamily.registerAddressFamily("vsock", //
          AFVSOCKSocketAddress.class, new AFSocketAddressConfig<AFVSOCKSocketAddress>() {

            @Override
            protected AFVSOCKSocketAddress parseURI(URI u, int port) throws SocketException {
              return AFVSOCKSocketAddress.of(u, port);
            }

            @Override
            protected AFSocketAddressConstructor<AFVSOCKSocketAddress> addressConstructor() {
              return AFVSOCKSocketAddress::new;
            }

            @Override
            protected String selectorProviderClassname() {
              return "org.newsclub.net.unix.vsock.AFVSOCKSelectorProvider";
            }

            @Override
            protected Set<String> uriSchemes() {
              return new HashSet<>(Arrays.asList("vsock", "http+vsock", "https+vsock"));
            }
          });
      try {
        Class.forName("org.newsclub.net.unix.vsock.AFVSOCKSelectorProvider");
      } catch (ClassNotFoundException e) {
        // ignore
      }
    }
    return afVsock;
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} for the given URI, if possible.
   *
   * @param uri The URI.
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  @SuppressWarnings("PMD.ShortMethodName")
  public static AFVSOCKSocketAddress of(URI uri) throws SocketException {
    return of(uri, -1);
  }

  /**
   * Returns an {@link AFVSOCKSocketAddress} for the given URI, if possible.
   *
   * @param uri The URI.
   * @param overridePort The port to forcibly use, or {@code -1} for "don't override".
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  @SuppressWarnings({
      "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.ExcessiveMethodLength",
      "PMD.NcssCount", "PMD.NPathComplexity", "PMD.ShortMethodName"})
  public static AFVSOCKSocketAddress of(URI uri, int overridePort) throws SocketException {
    switch (uri.getScheme()) {
      case "vsock":
      case "http+vsock":
      case "https+vsock":
        break;
      default:
        throw new SocketException("Unsupported URI scheme: " + uri.getScheme());
    }

    String host = uri.getHost();
    if (host == null) {
      host = uri.getAuthority();
      if (host != null) {
        int at = host.indexOf('@');
        if (at >= 0) {
          host = host.substring(at + 1);
        }
      }
    }
    if (host == null) {
      throw new SocketException("Cannot get hostname from URI: " + uri);
    }

    try {
      Matcher m = PAT_VSOCK_URI_HOST_AND_PORT.matcher(host);
      if (!m.matches()) {
        throw new SocketException("Invalid VSOCK URI: " + uri);
      }

      String cidStr = m.group("cid");
      String portStr = m.group("port");
      String javaPortStr = m.group("javaPort");

      int cid;
      switch (cidStr == null ? "" : cidStr) {
        case "":
        case "any":
          cid = VMADDR_CID_ANY;
          break;
        case "hypervisor":
          cid = VMADDR_CID_HYPERVISOR;
          break;
        case "local":
          cid = VMADDR_CID_LOCAL;
          break;
        case "host":
          cid = VMADDR_CID_HOST;
          break;
        default:
          cid = parseInt(cidStr);
          break;
      }

      int port;
      switch (portStr == null ? "" : portStr) {
        case "any":
        case "":
          port = VMADDR_PORT_ANY;
          break;
        default:
          port = parseInt(portStr);
          break;
      }

      int javaPort = overridePort != -1 ? overridePort : uri.getPort();
      if (javaPortStr != null && !javaPortStr.isEmpty()) {
        javaPort = parseInt(javaPortStr);
      }

      return ofPortAndCID(javaPort, port, cid);
    } catch (IllegalArgumentException e) {
      throw (SocketException) new SocketException("Invalid VSOCK URI: " + uri).initCause(e);
    }
  }

  @Override
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.CompareObjectsWithEquals"})
  public URI toURI(String scheme, URI template) throws IOException {
    switch (scheme) {
      case "vsock":
      case "http+vsock":
      case "https+vsock":
        break;
      default:
        return super.toURI(scheme, template);
    }

    byte[] bytes = getBytes();
    if (bytes.length != (3 * 4)) {
      return super.toURI(scheme, template);
    }

    StringBuilder sb = new StringBuilder();

    String portStr;
    int port;
    switch ((port = getVSOCKPort())) {
      case VMADDR_PORT_ANY:
        portStr = "any";
        break;
      default:
        portStr = Integer.toUnsignedString(port);
        break;
    }

    sb.append(portStr);
    sb.append('.');
    String cidStr;
    int cid;
    switch ((cid = getVSOCKCID())) {
      case VMADDR_CID_ANY:
        cidStr = "any";
        break;
      case VMADDR_CID_HYPERVISOR:
        cidStr = "hypervisor";
        break;
      case VMADDR_CID_LOCAL:
        cidStr = "local";
        break;
      case VMADDR_CID_HOST:
        cidStr = "host";
        break;
      default:
        cidStr = Integer.toUnsignedString(cid);
        break;
    }

    sb.append(cidStr);

    return new HostAndPort(sb.toString(), getPort()).toURI(scheme, template);
  }

  private static int parseInt(String v) {
    if (v.startsWith("0x")) {
      return Integer.parseUnsignedInt(v.substring(2), 16);
    } else if (v.startsWith("-")) {
      return Integer.parseInt(v);
    } else {
      return Integer.parseUnsignedInt(v);
    }
  }

  /**
   * Checks if the given address could cover another address.
   *
   * By default, this is only true if both addresses are regarded equal using
   * {@link #equals(Object)}.
   *
   * However, implementations may support "wildcard" addresses, and this method would compare a
   * wildcard address against some non-wildcard address, for example.
   *
   * @param covered The other address that could be covered by this address.
   * @return {@code true} if the other address could be covered.
   */
  @Override
  public boolean covers(AFSocketAddress covered) {
    if (super.covers(covered)) {
      return true;
    } else if (covered instanceof AFVSOCKSocketAddress) {
      AFVSOCKSocketAddress other = (AFVSOCKSocketAddress) covered;

      if (getVSOCKCID() == VMADDR_CID_ANY) {
        if (getVSOCKPort() == VMADDR_PORT_ANY) {
          return true;
        } else {
          return getVSOCKPort() == other.getVSOCKPort();
        }
      } else if (getVSOCKPort() == VMADDR_PORT_ANY) {
        return getVSOCKCID() == other.getVSOCKCID();
      }
    }

    return equals(covered);
  }
}
