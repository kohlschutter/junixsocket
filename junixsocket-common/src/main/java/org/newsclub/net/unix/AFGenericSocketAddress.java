/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.newsclub.net.unix.pool.ObjectPool.Lease;

/**
 * An {@link AFSocketAddress} for unknown socket types.
 *
 * @author Christian Kohlschütter
 */
public final class AFGenericSocketAddress extends AFSocketAddress {
  private static final long serialVersionUID = 1L; // do not change!

  private static AFAddressFamily<AFGenericSocketAddress> family;
  private static final String SELECTOR_PROVIDER_CLASS =
      "org.newsclub.net.unix.generic.AFGenericSelectorProvider";

  private AFGenericSocketAddress(int port, final byte[] socketAddress,
      Lease<ByteBuffer> nativeAddress) throws SocketException {
    super(port, socketAddress, nativeAddress, addressFamily());
  }

  private static AFGenericSocketAddress newAFSocketAddress(int port, final byte[] socketAddress,
      Lease<ByteBuffer> nativeAddress) throws SocketException {
    return newDeserializedAFSocketAddress(port, socketAddress, nativeAddress, addressFamily(),
        AFGenericSocketAddress::new);
  }

  /**
   * Returns an {@link AFGenericSocketAddress} given a special {@link InetAddress} that encodes the
   * byte sequence of an unknown-type socket address, like those returned by {@link #wrapAddress()}.
   *
   * @param address The "special" {@link InetAddress}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFGenericSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFGenericSocketAddress unwrap(InetAddress address, int port)
      throws SocketException {
    return AFSocketAddress.unwrap(address, port, addressFamily());
  }

  /**
   * Returns an {@link AFGenericSocketAddress} given a special {@link InetAddress} hostname that
   * encodes the byte sequence of an unknown-type socket address, like those returned by
   * {@link #wrapAddress()}.
   *
   * @param hostname The "special" hostname, as provided by {@link InetAddress#getHostName()}.
   * @param port The port (use 0 for "none").
   * @return The {@link AFGenericSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFGenericSocketAddress unwrap(String hostname, int port) throws SocketException {
    return AFSocketAddress.unwrap(hostname, port, addressFamily());
  }

  /**
   * Returns an {@link AFGenericSocketAddress} given a generic {@link SocketAddress}.
   *
   * @param address The address to unwrap.
   * @return The {@link AFGenericSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  public static AFGenericSocketAddress unwrap(SocketAddress address) throws SocketException {
    Objects.requireNonNull(address);
    if (!isSupportedAddress(address)) {
      throw new SocketException("Unsupported address");
    }
    return (AFGenericSocketAddress) address;
  }

  @Override
  public String toString() {
    int port = getPort();

    return getClass().getName() + "[" + (port == 0 ? "" : "port=" + port + ";") + "bytes=" + Arrays
        .toString(getBytes()) + "]";
  }

  /**
   * Returns the native representation of this generic address, which is most likely not portable.
   * <p>
   * The address contains the sa_family identifier as the first byte, and, on some platforms only,
   * the address length, as the second byte.
   *
   * @return A new byte array containing the system-specific representation of that address.
   */
  public byte[] toBytes() {
    byte[] bytes = getBytes();
    return Arrays.copyOf(bytes, bytes.length);
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
   * Checks if an {@link InetAddress} can be unwrapped to an {@link AFGenericSocketAddress}.
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
   * Checks if a {@link SocketAddress} can be unwrapped to an {@link AFGenericSocketAddress}.
   *
   * @param addr The instance to check.
   * @return {@code true} if so.
   * @see #unwrap(InetAddress, int)
   */
  public static boolean isSupportedAddress(SocketAddress addr) {
    return (addr instanceof AFGenericSocketAddress);
  }

  /**
   * Returns the corresponding {@link AFAddressFamily}.
   *
   * @return The address family instance.
   */
  @SuppressWarnings("null")
  public static synchronized AFAddressFamily<AFGenericSocketAddress> addressFamily() {
    if (family == null) {
      family = AFAddressFamily.registerAddressFamily("generic", //
          AFGenericSocketAddress.class, new AFSocketAddressConfig<AFGenericSocketAddress>() {

            private final AFSocketAddressConstructor<AFGenericSocketAddress> addrConstr =
                isUseDeserializationForInit() ? AFGenericSocketAddress::newAFSocketAddress
                    : AFGenericSocketAddress::new;

            @Override
            protected AFGenericSocketAddress parseURI(URI u, int port) throws SocketException {
              return AFGenericSocketAddress.of(u, port);
            }

            @Override
            protected AFSocketAddressConstructor<AFGenericSocketAddress> addressConstructor() {
              return addrConstr;
            }

            @Override
            protected String selectorProviderClassname() {
              return SELECTOR_PROVIDER_CLASS;
            }

            @Override
            protected Set<String> uriSchemes() {
              return Collections.emptySet();
            }
          });
      try {
        Class.forName(SELECTOR_PROVIDER_CLASS);
      } catch (ClassNotFoundException e) {
        // ignore
      }
    }
    return family;
  }

  /**
   * Returns an {@link AFGenericSocketAddress} for the given URI, if possible.
   *
   * @param uri The URI.
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  @SuppressWarnings("PMD.ShortMethodName")
  public static AFGenericSocketAddress of(URI uri) throws SocketException {
    return of(uri, -1);
  }

  /**
   * Returns an {@link AFGenericSocketAddress} for the given URI, if possible.
   *
   * @param uri The URI.
   * @param overridePort The port to forcibly use, or {@code -1} for "don't override".
   * @return The address.
   * @throws SocketException if the operation fails.
   */
  @SuppressWarnings({
      "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.ExcessiveMethodLength",
      "PMD.NcssCount", "PMD.NPathComplexity", "PMD.ShortMethodName"})
  public static AFGenericSocketAddress of(URI uri, int overridePort) throws SocketException {
    throw new SocketException("Unsupported");
  }

  @Override
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.CompareObjectsWithEquals"})
  public URI toURI(String scheme, URI template) throws IOException {
    return super.toURI(scheme, template);
  }
}
