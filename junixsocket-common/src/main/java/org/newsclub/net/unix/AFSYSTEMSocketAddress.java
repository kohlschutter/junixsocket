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
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An {@link AFSocketAddress} for AF_SYSTEM sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFSYSTEMSocketAddress extends AFSocketAddress {
  private static final long serialVersionUID = 1L;

  private static AFAddressFamily<AFSYSTEMSocketAddress> afSystem;
  private static final String SELECTOR_PROVIDER_CLASS =
      "org.newsclub.net.unix.darwin.system.AFSYSTEMSelectorProvider";

  /**
   * The AF_SYSTEM system address.
   *
   * @author Christian Kohlschütter
   */
  @NonNullByDefault
  public static final class SysAddr extends NamedInteger {
    private static final long serialVersionUID = 1L;

    /**
     * The sysaddr AF_SYS_CONTROL, also known as AF_SYS_KERN_CONTROL.
     */
    public static final SysAddr AF_SYS_CONTROL;

    private static final @NonNull SysAddr[] VALUES = init(new @NonNull SysAddr[] {
        AF_SYS_CONTROL = new SysAddr("AF_SYS_CONTROL", 2) //
    });

    private SysAddr(int id) {
      super(id);
    }

    private SysAddr(String name, int id) {
      super(name, id);
    }

    /**
     * Returns a {@link SysAddr} for the given custom value.
     * 
     * @param v The value.
     * @return The {@link SysAddr} object.
     */
    public static SysAddr ofValue(int v) {
      return ofValue(VALUES, SysAddr::new, v);
    }
  }

  private AFSYSTEMSocketAddress(int port, final byte[] socketAddress, ByteBuffer nativeAddress)
      throws SocketException {
    super(port, socketAddress, nativeAddress, addressFamily());
  }

  /**
   * Returns an {@link AFSYSTEMSocketAddress} that refers to a given AF_SYSTEM socket address (i.e.,
   * referring to a particular socket instance instead of a service address). A Java-only "IP port
   * number" is stored along the instance for compatibility reasons.
   *
   * @param javaPort The emulated "port" number (not part of AF_SYSTEM).
   * @param sysAddr 16-bit system address (e.g., AF_SYS_KERNCONTROL)
   * @param id Controller unique identifier
   * @param unit Developer private unit number, 0 means "unspecified".
   * @return A corresponding {@link AFSYSTEMSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFSYSTEMSocketAddress ofSysAddrIdUnit(int javaPort, SysAddr sysAddr, int id,
      int unit) throws SocketException {
    return resolveAddress(toBytes(sysAddr, id, unit), javaPort, addressFamily());
  }

  /**
   * Returns an {@link AFSYSTEMSocketAddress} that refers to a given AF_SYSTEM socket address (i.e.,
   * referring to a particular socket instance instead of a service address).
   *
   * @param sysAddr 16-bit system address (e.g., AF_SYS_KERNCONTROL)
   * @param id Controller unique identifier
   * @param unit Developer private unit number
   * @return A corresponding {@link AFSYSTEMSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFSYSTEMSocketAddress ofSysAddrIdUnit(SysAddr sysAddr, int id, int unit)
      throws SocketException {
    return ofSysAddrIdUnit(0, sysAddr, id, unit);
  }

  /**
   * Returns an {@link AFSYSTEMSocketAddress} given a special {@link InetAddress} that encodes the
   * byte sequence of an AF_SYSTEM socket address, like those returned by {@link #wrapAddress()}.
   *
   * @param address The "special" {@link InetAddress}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFSYSTEMSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFSYSTEMSocketAddress unwrap(InetAddress address, int port) throws SocketException {
    return AFSocketAddress.unwrap(address, port, addressFamily());
  }

  /**
   * Returns an {@link AFSYSTEMSocketAddress} given a special {@link InetAddress} hostname that
   * encodes the byte sequence of an AF_SYSTEM socket address, like those returned by
   * {@link #wrapAddress()}.
   *
   * @param hostname The "special" hostname, as provided by {@link InetAddress#getHostName()}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFSYSTEMSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFSYSTEMSocketAddress unwrap(String hostname, int port) throws SocketException {
    return AFSocketAddress.unwrap(hostname, port, addressFamily());
  }

  /**
   * Returns an {@link AFSYSTEMSocketAddress} given a generic {@link SocketAddress}.
   *
   * @param address The address to unwrap.
   * @return The {@link AFSYSTEMSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFSYSTEMSocketAddress unwrap(SocketAddress address) throws SocketException {
    Objects.requireNonNull(address);
    if (!isSupportedAddress(address)) {
      throw new SocketException("Unsupported address");
    }
    return (AFSYSTEMSocketAddress) address;
  }

  @Override
  public String toString() {
    int port = getPort();

    byte[] bytes = getBytes();
    if (bytes.length != (8 * 4)) {
      return getClass().getName() + "[" + (port == 0 ? "" : "port=" + port) + ";UNKNOWN" + "]";
    }

    ByteBuffer bb = ByteBuffer.wrap(bytes);
    SysAddr sysAddr = SysAddr.ofValue(bb.getInt());
    int id = bb.getInt();
    int unit = bb.getInt();

    return getClass().getName() + "[" + (port == 0 ? "" : "port=" + port + ";") + sysAddr + ";id="
        + id + ";unit=" + unit + "]";
  }

  /**
   * Returns the "SysAddr" part of the address.
   *
   * @return The SysAddr part.
   */
  public @NonNull SysAddr getSysAddr() {
    ByteBuffer bb = ByteBuffer.wrap(getBytes());
    return SysAddr.ofValue(bb.getInt(0));
  }

  /**
   * Returns the "id" part of the address.
   *
   * @return The id part.
   */
  public int getId() {
    ByteBuffer bb = ByteBuffer.wrap(getBytes());
    return bb.getInt(4);
  }

  /**
   * Returns the "unit" part of the address.
   *
   * @return The unit part.
   */
  public int getUnit() {
    ByteBuffer bb = ByteBuffer.wrap(getBytes());
    return bb.getInt(8);
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
   * Checks if an {@link InetAddress} can be unwrapped to an {@link AFSYSTEMSocketAddress}.
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
   * Checks if a {@link SocketAddress} can be unwrapped to an {@link AFSYSTEMSocketAddress}.
   *
   * @param addr The instance to check.
   * @return {@code true} if so.
   * @see #unwrap(InetAddress, int)
   */
  public static boolean isSupportedAddress(SocketAddress addr) {
    return (addr instanceof AFSYSTEMSocketAddress);
  }

  @SuppressWarnings("cast")
  private static byte[] toBytes(SysAddr sysAddr, int id, int unit) {
    ByteBuffer bb = ByteBuffer.allocate(8 * 4);
    bb.putInt(sysAddr.value());
    bb.putInt(id);
    bb.putInt(unit);
    bb.putInt(0);
    bb.putInt(0);
    bb.putInt(0);
    bb.putInt(0);
    bb.putInt(0);
    return (byte[]) bb.flip().array();
  }

  /**
   * Returns the corresponding {@link AFAddressFamily}.
   *
   * @return The address family instance.
   */
  @SuppressWarnings("null")
  public static synchronized AFAddressFamily<AFSYSTEMSocketAddress> addressFamily() {
    if (afSystem == null) {
      afSystem = AFAddressFamily.registerAddressFamily("system", //
          AFSYSTEMSocketAddress.class, new AFSocketAddressConfig<AFSYSTEMSocketAddress>() {

            @Override
            protected AFSYSTEMSocketAddress parseURI(URI u, int port) throws SocketException {
              return AFSYSTEMSocketAddress.of(u, port);
            }

            @Override
            protected AFSocketAddressConstructor<AFSYSTEMSocketAddress> addressConstructor() {
              return AFSYSTEMSocketAddress::new;
            }

            @Override
            protected String selectorProviderClassname() {
              return SELECTOR_PROVIDER_CLASS;
            }

            @Override
            protected Set<String> uriSchemes() {
              return new HashSet<>(Arrays.asList("afsystem"));
            }
          });
      try {
        Class.forName(SELECTOR_PROVIDER_CLASS);
      } catch (ClassNotFoundException e) {
        // ignore
      }
    }
    return afSystem;
  }

  /**
   * Returns an {@link AFSYSTEMSocketAddress} for the given URI, if possible.
   *
   * @param uri The URI.
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  @SuppressWarnings("PMD.ShortMethodName")
  public static AFSYSTEMSocketAddress of(URI uri) throws SocketException {
    return of(uri, -1);
  }

  /**
   * Returns an {@link AFSYSTEMSocketAddress} for the given URI, if possible.
   *
   * @param uri The URI.
   * @param overridePort The port to forcibly use, or {@code -1} for "don't override".
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  @SuppressWarnings({
      "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.ExcessiveMethodLength",
      "PMD.NcssCount", "PMD.NPathComplexity", "PMD.ShortMethodName"})
  public static AFSYSTEMSocketAddress of(URI uri, int overridePort) throws SocketException {
    switch (uri.getScheme()) {
      case "afsystem":
        break;
      default:
        throw new SocketException("Unsupported URI scheme: " + uri.getScheme());
    }

    String host;
    if ((host = uri.getHost()) == null) {
      String ssp = uri.getSchemeSpecificPart();
      if (ssp == null || !ssp.startsWith("//")) {
        throw new SocketException("Unsupported URI: " + uri);
      }
      ssp = ssp.substring(2);
      int i = ssp.indexOf('/');
      host = i == -1 ? ssp : ssp.substring(0, i);
      if (host.isEmpty()) {
        throw new SocketException("Unsupported URI: " + uri);
      }
    }

    ByteBuffer bb = ByteBuffer.allocate(8 * 4);
    for (String p : host.split("\\.")) {
      int v;
      try {
        v = Integer.parseUnsignedInt(p);
      } catch (NumberFormatException e) {
        throw (SocketException) new SocketException("Unsupported URI: " + uri).initCause(e);
      }
      bb.putInt(v);
    }
    bb.flip();
    if (bb.remaining() > 8 * 4) {
      throw new SocketException("Unsupported URI: " + uri);
    }
    /* SysAddr sa = */ SysAddr.ofValue(bb.getInt());
    /* int id = */ bb.getInt();
    /* int unit = */ bb.getInt();

    while (bb.remaining() > 0) {
      if (bb.getInt() != 0) {
        throw new SocketException("Unsupported URI: " + uri);
      }
    }

    return

    resolveAddress(bb.array(), uri.getPort(), addressFamily());
  }

  @Override
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.CompareObjectsWithEquals"})
  public URI toURI(String scheme, URI template) throws IOException {
    switch (scheme) {
      case "afsystem":
        break;
      default:
        return super.toURI(scheme, template);
    }

    ByteBuffer bb = ByteBuffer.wrap(getBytes());
    StringBuilder sb = new StringBuilder();
    while (bb.remaining() > 0) {
      sb.append(Integer.toUnsignedString(bb.getInt()));
      if (bb.remaining() > 0) {
        sb.append('.');
      }
    }

    return new HostAndPort(sb.toString(), getPort()).toURI(scheme, template);
  }
}
