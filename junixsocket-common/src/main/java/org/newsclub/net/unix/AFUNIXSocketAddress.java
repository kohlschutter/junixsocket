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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Describes an {@link InetSocketAddress} that actually uses AF_UNIX sockets instead of AF_INET.
 *
 * The ability to specify a port number is not specified by AF_UNIX sockets, but we need it
 * sometimes, for example for RMI-over-AF_UNIX.
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.ShortMethodName")
public final class AFUNIXSocketAddress extends AFSocketAddress {
  private static final long serialVersionUID = 1L;

  private static final Charset ADDRESS_CHARSET = Charset.defaultCharset();

  @SuppressWarnings("null")
  static final AFAddressFamily<@NonNull AFUNIXSocketAddress> AF_UNIX = AFAddressFamily
      .registerAddressFamily("un", //
          AFUNIXSocketAddress.class, new AFSocketAddressConfig<AFUNIXSocketAddress>() {

            @Override
            public AFUNIXSocketAddress parseURI(URI u, int port) throws SocketException {
              return AFUNIXSocketAddress.of(u, port);
            }

            @Override
            protected AFSocketAddressConstructor<AFUNIXSocketAddress> addressConstructor() {
              return AFUNIXSocketAddress::new;
            }

            @Override
            protected String selectorProviderClassname() {
              return AFUNIXSelectorProvider.class.getName();
            }

            @Override
            protected Set<String> uriSchemes() {
              return new HashSet<>(Arrays.asList("unix", "http+unix", "https+unix"));
            }
          });

  private AFUNIXSocketAddress(int port, final byte[] socketAddress, ByteBuffer nativeAddress)
      throws SocketException {
    super(port, socketAddress, nativeAddress, AF_UNIX);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * file and port. <b>Legacy constructor, do not use!</b>
   *
   * @param socketFile The socket to connect to.
   * @throws SocketException if the operation fails.
   * @deprecated Use {@link #of(File)} instead.
   * @see #of(File)
   */
  @Deprecated
  public AFUNIXSocketAddress(File socketFile) throws SocketException {
    this(socketFile, 0);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * file. <b>Legacy constructor, do not use!</b>
   *
   * @param socketFile The socket to connect to.
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @throws SocketException if the operation fails.
   * @deprecated Use {@link #of(File, int)} instead.
   * @see #of(File, int)
   */
  @Deprecated
  public AFUNIXSocketAddress(File socketFile, int port) throws SocketException {
    this(port, of(socketFile, port).getPathAsBytes(), of(socketFile, port)
        .getNativeAddressDirectBuffer());
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * file.
   *
   * @param socketFile The socket to connect to.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(final File socketFile) throws SocketException {
    return of(socketFile, 0);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * file, assigning the given port to it.
   *
   * @param socketFile The socket to connect to.
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(final File socketFile, int port) throws SocketException {
    return of(socketFile.getPath().getBytes(ADDRESS_CHARSET), port);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * byte sequence.
   *
   * NOTE: By specifying a byte array that starts with a zero byte, you indicate that the abstract
   * namespace is to be used. This feature is not available on all target platforms.
   *
   * @param socketAddress The socket address (as bytes).
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   * @see AFUNIXSocketAddress#inAbstractNamespace(String)
   */
  public static AFUNIXSocketAddress of(final byte[] socketAddress) throws SocketException {
    return of(socketAddress, 0);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * byte sequence, assigning the given port to it.
   *
   * NOTE: By specifying a byte array that starts with a zero byte, you indicate that the abstract
   * namespace is to be used. This feature is not available on all target platforms.
   *
   * @param socketAddress The socket address (as bytes).
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   * @see AFUNIXSocketAddress#inAbstractNamespace(String,int)
   */
  public static AFUNIXSocketAddress of(final byte[] socketAddress, int port)
      throws SocketException {
    return AFSocketAddress.resolveAddress(socketAddress, port, AF_UNIX);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * path.
   *
   * @param socketPath The socket to connect to.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(Path socketPath) throws SocketException {
    return of(socketPath, 0);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * path, assigning the given port to it.
   *
   * @param socketPath The socket to connect to.
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(Path socketPath, int port) throws SocketException {
    if (!PathUtil.isPathInDefaultFileSystem(socketPath)) {
      throw new SocketException("Path is not in the default file system");
    }

    return of(socketPath.toString().getBytes(ADDRESS_CHARSET), port);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} for the given URI, if possible.
   *
   * @param u The URI.
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(URI u) throws SocketException {
    return of(u, -1);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} for the given URI, if possible.
   *
   * @param u The URI.
   * @param overridePort The port to forcibly use, or {@code -1} for "don't override".
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(URI u, int overridePort) throws SocketException {
    switch (u.getScheme()) {
      case "file":
      case "unix":
        String path = u.getPath();
        if (path == null || path.isEmpty()) {
          String auth = u.getAuthority();
          if (auth != null && !auth.isEmpty() && u.getRawSchemeSpecificPart().indexOf('@') == -1) {
            path = auth;
          } else {
            throw new SocketException("Cannot find UNIX socket path component from URI: " + u);
          }
        }
        return of(new File(path), overridePort != -1 ? overridePort : u.getPort());
      case "http+unix":
      case "https+unix":
        HostAndPort hp = HostAndPort.parseFrom(u);
        return of(new File(hp.getHostname()), overridePort != -1 ? overridePort : hp.getPort());
      default:
        throw new SocketException("Invalid URI");
    }
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to a temporary, non-existent but accessible
   * path in the file system.
   *
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocketAddress ofNewTempFile() throws IOException {
    return ofNewTempPath(0);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to a temporary, non-existent but accessible
   * path in the file system, assigning the given port to it.
   *
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocketAddress ofNewTempPath(int port) throws IOException {
    return of(newTempPath(true), port);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} based on the given {@link SocketAddress}.
   *
   * This either simply casts an existing {@link AFUNIXSocketAddress}, or converts a
   * {@code UnixDomainSocketAddress} to it.
   *
   * @param address The address to convert.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(SocketAddress address) throws IOException {
    AFUNIXSocketAddress addr = unwrap(Objects.requireNonNull(address));
    if (addr == null) {
      throw new SocketException("Could not convert SocketAddress to AFUNIXSocketAddress");
    }
    return addr;
  }

  static File newTempPath(boolean deleteOnExit) throws IOException {
    File f = File.createTempFile("jux", ".sock");
    if (deleteOnExit) {
      f.deleteOnExit(); // always delete on exit to clean-up sockets created under that name
    }
    if (!f.delete() && f.exists()) {
      throw new IOException("Could not delete temporary file that we just created: " + f);
    }
    return f;
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} given a special {@link InetAddress} that encodes the
   * byte sequence of an AF_UNIX socket address, like those returned by {@link #wrapAddress()}.
   *
   * @param address The "special" {@link InetAddress}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFUNIXSocketAddress unwrap(InetAddress address, int port) throws SocketException {
    return AFSocketAddress.unwrap(address, port, AF_UNIX);
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} given a generic {@link SocketAddress}.
   *
   * @param address The address to unwrap.
   * @return The {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFUNIXSocketAddress unwrap(SocketAddress address) throws SocketException {
    // FIXME: add support for UnixDomainSocketAddress
    Objects.requireNonNull(address);
    Supplier<AFUNIXSocketAddress> supplier = supportedAddressSupplier(address);
    if (supplier == null) {
      throw new SocketException("Unsupported address");
    }
    return supplier.get();
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} given a special {@link InetAddress} hostname that
   * encodes the byte sequence of an AF_UNIX socket address, like those returned by
   * {@link #wrapAddress()}.
   *
   * @param hostname The "special" hostname, as provided by {@link InetAddress#getHostName()}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFUNIXSocketAddress unwrap(String hostname, int port) throws SocketException {
    return AFSocketAddress.unwrap(hostname, port, AF_UNIX);
  }

  /**
   * Convenience method to create an {@link AFUNIXSocketAddress} in the abstract namespace.
   *
   * The returned socket address will use the byte representation of this identifier (using the
   * system's default character encoding), prefixed with a null byte (to indicate the abstract
   * namespace is used).
   *
   * @param name The identifier in the abstract namespace, without trailing zero or @.
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress inAbstractNamespace(String name) throws SocketException {
    return inAbstractNamespace(name, 0);
  }

  /**
   * Convenience method to create an {@link AFUNIXSocketAddress} in the abstract namespace.
   *
   * The returned socket address will use the byte representation of this identifier (using the
   * system's default character encoding), prefixed with a null byte (to indicate the abstract
   * namespace is used).
   *
   * @param name The identifier in the abstract namespace, without trailing zero or @.
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress inAbstractNamespace(String name, int port)
      throws SocketException {
    byte[] bytes = name.getBytes(ADDRESS_CHARSET);
    byte[] addr = new byte[bytes.length + 1];
    System.arraycopy(bytes, 0, addr, 1, bytes.length);
    return AFUNIXSocketAddress.of(addr, port);
  }

  private static String prettyPrint(byte[] data) {
    final int dataLength = data.length;
    if (dataLength == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(dataLength + 16);
    for (int i = 0; i < dataLength; i++) {
      byte c = data[i];
      if (c >= 32 && c < 127) {
        sb.append((char) c);
      } else {
        sb.append("\\x");
        sb.append(String.format(Locale.ENGLISH, "%02x", c));
      }
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    int port = getPort();
    return getClass().getName() + "[" + (port == 0 ? "" : "port=" + port + ";") + "path="
        + prettyPrint(getBytes()) + "]";
  }

  /**
   * Returns the path to the UNIX domain socket, as a human-readable string using the default
   * encoding.
   *
   * For addresses in the abstract namespace, the US_ASCII encoding is used; zero-bytes are
   * converted to '@', other non-printable bytes are converted to '.'
   *
   * @return The path.
   * @see #getPathAsBytes()
   */
  public String getPath() {
    byte[] bytes = getBytes();
    if (bytes.length == 0) {
      return "";
    } else if (bytes[0] != 0) {
      return new String(bytes, ADDRESS_CHARSET);
    }

    byte[] by = bytes.clone();
    for (int i = 0; i < by.length; i++) {
      byte b = by[i];
      if (b == 0) {
        by[i] = '@';
      } else if (b >= 32 && b < 127) {
        // print as-is
      } else {
        by[i] = '.';
      }
    }
    return new String(by, StandardCharsets.US_ASCII);
  }

  /**
   * Returns the {@link Charset} used to encode/decode {@link AFUNIXSocketAddress}es.
   *
   * This is usually the system default charset, unless that is {@link StandardCharsets#US_ASCII}
   * (7-bit), in which case {@link StandardCharsets#ISO_8859_1} is used instead.
   *
   * @return The charset.
   */
  public static Charset addressCharset() {
    return ADDRESS_CHARSET;
  }

  /**
   * Returns the path to the UNIX domain socket, as bytes.
   *
   * @return The path.
   * @see #getPath()
   */
  public byte[] getPathAsBytes() {
    return getBytes().clone();
  }

  /**
   * Checks if the address is in the abstract namespace.
   *
   * @return {@code true} if the address is in the abstract namespace.
   */
  public boolean isInAbstractNamespace() {
    byte[] bytes = getBytes();
    return bytes.length > 0 && bytes[0] == 0;
  }

  @Override
  public boolean hasFilename() {
    byte[] bytes = getBytes();
    return bytes.length > 0 && bytes[0] != 0;
  }

  @Override
  public File getFile() throws FileNotFoundException {
    if (isInAbstractNamespace()) {
      throw new FileNotFoundException("Socket is in abstract namespace");
    }
    byte[] bytes = getBytes();

    if (bytes.length == 0) {
      throw new FileNotFoundException("No name");
    }
    return new File(new String(bytes, ADDRESS_CHARSET));
  }

  /**
   * Checks if an {@link InetAddress} can be unwrapped to an {@link AFUNIXSocketAddress}.
   *
   * @param addr The instance to check.
   * @return {@code true} if so.
   * @see #wrapAddress()
   * @see #unwrap(InetAddress, int)
   */
  public static boolean isSupportedAddress(InetAddress addr) {
    return AFInetAddress.isSupportedAddress(addr, AF_UNIX);
  }

  /**
   * Checks if a {@link SocketAddress} can be unwrapped to an {@link AFUNIXSocketAddress}.
   *
   * @param addr The instance to check.
   * @return {@code true} if so.
   * @see #unwrap(InetAddress, int)
   */
  public static boolean isSupportedAddress(SocketAddress addr) {
    return supportedAddressSupplier(addr) != null;
  }

  /**
   * Checks if the given address can be unwrapped to an {@link AFUNIXSocketAddress}, and if so,
   * returns a supplier function; if not, {@code null} is returned.
   *
   * @param addr The address.
   * @return The supplier, or {@code null}.
   */
  static Supplier<AFUNIXSocketAddress> supportedAddressSupplier(SocketAddress addr) {
    if (addr == null) {
      return null;
    } else if (addr instanceof AFUNIXSocketAddress) {
      return () -> ((AFUNIXSocketAddress) addr);
    } else {
      return SocketAddressUtil.supplyAFUNIXSocketAddress(addr);
    }
  }

  /**
   * Returns the corresponding {@link AFAddressFamily}.
   *
   * @return The address family instance.
   */
  @SuppressWarnings("null")
  public static AFAddressFamily<AFUNIXSocketAddress> addressFamily() {
    return AFUNIXSelectorProvider.getInstance().addressFamily();
  }

  @Override
  public URI toURI(String scheme, URI template) throws IOException {
    switch (scheme) {
      case "unix":
      case "file":
        try {
          if (getPort() > 0 && !"file".equals(scheme)) {
            return new URI(scheme, null, "localhost", getPort(), getPath(), null, (String) null);
          } else {
            return new URI(scheme, null, null, -1, getPath(), null, null);
          }
        } catch (URISyntaxException e) {
          throw new IOException(e);
        }
      case "http+unix":
      case "https+unix":
        HostAndPort hp = new HostAndPort(getPath(), getPort());
        return hp.toURI(scheme, template);
      default:
        return super.toURI(scheme, template);
    }
  }

  @Override
  public AFUNIXSocket newConnectedSocket() throws IOException {
    return (AFUNIXSocket) super.newConnectedSocket();
  }

  @Override
  public AFUNIXServerSocket newBoundServerSocket() throws IOException {
    return (AFUNIXServerSocket) super.newBoundServerSocket();
  }

  @Override
  public AFUNIXServerSocket newForceBoundServerSocket() throws IOException {
    return (AFUNIXServerSocket) super.newForceBoundServerSocket();
  }
}
