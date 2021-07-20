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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Describes an {@link InetSocketAddress} that actually uses AF_UNIX sockets instead of AF_INET.
 * 
 * The ability to specify a port number is not specified by AF_UNIX sockets, but we need it
 * sometimes, for example for RMI-over-AF_UNIX.
 * 
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.ShortMethodName")
public final class AFUNIXSocketAddress extends InetSocketAddress {
  private static final long serialVersionUID = 1L;

  private static final int SOCKADDR_UN_LENGTH = NativeUnixSocket.sockAddrUnLength();
  private static final Map<ByteBuffer, AFUNIXSocketAddress> ADDRESS_CACHE = new HashMap<>();
  private static final Charset ADDRESS_CHARSET = Charset.defaultCharset();

  private final byte[] bytes;
  private InetAddress inetAddress = null; // only created on demand

  static final ThreadLocal<ByteBuffer> SOCKETADDRESS_BUFFER_TL = new ThreadLocal<ByteBuffer>() {

    @Override
    protected ByteBuffer initialValue() {
      return AFUNIXSocketAddress.newSockAddrUnDirectBuffer();
    }
  };

  /**
   * Just a marker for "don't actually bind" (checked with "=="). Used in combination with a
   * superclass' bind method, which should trigger "setBound()", etc.
   */
  static final AFUNIXSocketAddress INTERNAL_DUMMY_BIND = new AFUNIXSocketAddress(0);
  static final AFUNIXSocketAddress INTERNAL_DUMMY_CONNECT = new AFUNIXSocketAddress(1);
  static final AFUNIXSocketAddress INTERNAL_DUMMY_DONT_CONNECT = new AFUNIXSocketAddress(2);

  /**
   * Creates a new {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the
   * given file.
   * 
   * @param socketFile The socket to connect to.
   * @throws SocketException if the operation fails.
   * @deprecated Use {@link AFUNIXSocketAddress#of(File)} instead.
   */
  public AFUNIXSocketAddress(final File socketFile) throws SocketException {
    this(socketFile, 0);
  }

  /**
   * Creates a new {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the
   * given file, assigning the given port to it.
   * 
   * @param socketFile The socket to connect to.
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @throws SocketException if the operation fails.
   * @deprecated Use {@link AFUNIXSocketAddress#of(File, int)} instead.
   */
  public AFUNIXSocketAddress(final File socketFile, int port) throws SocketException {
    this(socketFile.getPath().getBytes(ADDRESS_CHARSET), port);
  }

  /**
   * Creates a new {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the
   * given byte sequence.
   * 
   * NOTE: By specifying a byte array that starts with a zero byte, you indicate that the abstract
   * namespace is to be used. This feature is not available on all target platforms.
   * 
   * @param socketAddress The socket address (as bytes).
   * @throws SocketException if the operation fails.
   * @see AFUNIXSocketAddress#inAbstractNamespace(String)
   * @deprecated Use {@link #of(byte[])} instead.
   */
  public AFUNIXSocketAddress(final byte[] socketAddress) throws SocketException {
    this(socketAddress, 0);
  }

  /**
   * Creates a new {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the
   * given byte sequence, assigning the given port to it.
   * 
   * NOTE: By specifying a byte array that starts with a zero byte, you indicate that the abstract
   * namespace is to be used. This feature is not available on all target platforms.
   *
   * @param socketAddress The socket address (as bytes).
   * @param port The port associated with this socket, or {@code 0} when no port should be assigned.
   * @throws SocketException if the operation fails.
   * @see AFUNIXSocketAddress#inAbstractNamespace(String,int)
   * @deprecated Use {@link #of(byte[], int)} instead.
   */
  public AFUNIXSocketAddress(final byte[] socketAddress, int port) throws SocketException {
    this(port, socketAddress);
  }

  private AFUNIXSocketAddress(int port, final byte[] socketAddress) throws SocketException {
    /*
     * Initializing the superclass with an unresolved hostname helps us pass the #equals and
     * #hashCode checks, which unfortunately are declared final in InetSocketAddress.
     * 
     * Using a resolved address (with the address bit initialized) would be ideal, but resolved
     * addresses can only be IPv4 or IPv6 (at least as of Java 16 and earlier).
     */
    super(AFUNIXInetAddress.createUnresolvedHostname(socketAddress), 0);
    if (port < -1) {
      throw new IllegalArgumentException("port out of range");
    }
    if (port > 0) {
      NativeUnixSocket.setPort1(this, port);
    }

    if (socketAddress.length == 0) {
      throw new SocketException("Illegal address length: " + socketAddress.length);
    }

    this.bytes = socketAddress.clone();
  }

  private AFUNIXSocketAddress(int port) {
    super(InetAddress.getLoopbackAddress(), port);
    this.bytes = new byte[0];
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
    AFUNIXSocketAddress instance;

    if (port == -1) {
      port = 0;
    }
    if (port != 0) {
      return new AFUNIXSocketAddress(port, socketAddress);
    }

    synchronized (AFUNIXSocketAddress.class) {
      ByteBuffer direct = SOCKETADDRESS_BUFFER_TL.get();
      NativeUnixSocket.bytesToSockAddrUn(direct, socketAddress);

      direct.rewind();
      direct.limit(SOCKADDR_UN_LENGTH);

      instance = ADDRESS_CACHE.get(direct);
      if (instance == null) {
        instance = new AFUNIXSocketAddress(port, socketAddress);

        ByteBuffer key = newSockAddrUnKeyBuffer();
        key.put(direct);
        ADDRESS_CACHE.put(key, instance);
      }
    }

    return instance;
  }

  /**
   * Returns an {@link AFUNIXSocketAddress} that points to the AF_UNIX socket specified by the given
   * path.
   * 
   * @param socketPath The socket to connect to.
   * @return A corresponding {@link AFUNIXSocketAddress} instance.
   * @throws SocketException if the operation fails.
   */
  public static AFUNIXSocketAddress of(final Path socketPath) throws SocketException {
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
  public static AFUNIXSocketAddress of(final Path socketPath, int port) throws SocketException {
    return of(socketPath.toString().getBytes(ADDRESS_CHARSET), port);
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
    Objects.requireNonNull(address);
    return new AFUNIXSocketAddress(port, AFUNIXInetAddress.unwrapAddress(address));
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
    if (!isSupportedAddress(address)) {
      throw new SocketException("Unsupported address");
    }
    return (AFUNIXSocketAddress) address;
  }

  /**
   * Returns the plain bytes (as returned by {@link #getBytes()}) of an AF_UNIX socket address.
   * 
   * @param address The address to unwrap.
   * @return The address bytes (the length trimmed to the address's length).
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  static byte[] unwrapAddress(SocketAddress address) throws SocketException {
    // FIXME: add support for UnixDomainSocketAddress
    Objects.requireNonNull(address);
    if (!isSupportedAddress(address)) {
      throw new SocketException("Unsupported address");
    }
    return ((AFUNIXSocketAddress) address).getBytes();
  }

  static void unwrapAddressDirectBufferInternal(ByteBuffer socketAddressBuffer,
      SocketAddress address) throws SocketException {
    byte[] addr = unwrapAddress(address);
    NativeUnixSocket.bytesToSockAddrUn(socketAddressBuffer, addr);
  }

  static AFUNIXSocketAddress ofInternal(ByteBuffer socketAddressBuffer) throws SocketException {
    synchronized (AFUNIXSocketAddress.class) {
      AFUNIXSocketAddress address = ADDRESS_CACHE.get(socketAddressBuffer);
      if (address != null) {
        return address;
      } else {
        byte[] sockAddrUnToBytes = NativeUnixSocket.sockAddrUnToBytes(socketAddressBuffer);
        if (sockAddrUnToBytes == null) {
          return null;
        } else {
          return of(sockAddrUnToBytes);
        }
      }
    }
  }

  /**
   * Returns a "special" {@link InetAddress} that contains information about this
   * {@link AFUNIXSocketAddress}.
   * 
   * IMPORTANT: This {@link InetAddress} does not properly compare (using
   * {@link InetAddress#equals(Object)} and {@link InetAddress#hashCode()}). It should be used
   * exclusively to circumvent existing APIs like {@link DatagramSocket} that only accept/return
   * {@link InetAddress} and not arbitrary {@link SocketAddress} types.
   * 
   * @return The "special" {@link InetAddress}.
   */
  public InetAddress wrapAddress() {
    return AFUNIXInetAddress.wrapAddress(bytes);
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
    return new AFUNIXSocketAddress(addr, port);
  }

  byte[] getBytes() {
    return bytes; // NOPMD
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
        + prettyPrint(bytes) + "]";
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
    return this.bytes.clone();
  }

  /**
   * Checks if the address is in the abstract namespace.
   * 
   * @return {@code true} if the address is in the abstract namespace.
   */
  public boolean isInAbstractNamespace() {
    return bytes.length > 0 && bytes[0] == 0;
  }

  /**
   * Checks if the address can be resolved to a {@link File}.
   * 
   * @return {@code true} if the address has a filename.
   */
  public boolean hasFilename() {
    return bytes.length > 0 && bytes[0] != 0;
  }

  /**
   * Returns the {@link File} corresponding with this address, if possible.
   * 
   * A {@link FileNotFoundException} is thrown if there is no filename associated with the address,
   * which applies to addresses in the abstract namespace, for example.
   * 
   * @return The filename.
   * @throws FileNotFoundException if the address is not associated with a filename.
   */
  public File getFile() throws FileNotFoundException {
    if (isInAbstractNamespace()) {
      throw new FileNotFoundException("Socket is in abstract namespace");
    } else if (bytes.length == 0) {
      throw new FileNotFoundException("No name");
    }
    return new File(new String(bytes, ADDRESS_CHARSET));
  }

  static AFUNIXSocketAddress preprocessSocketAddress(SocketAddress endpoint,
      AFUNIXSocketFactory socketFactory) throws SocketException {
    if (!(endpoint instanceof AFUNIXSocketAddress)) {
      if (socketFactory != null) {
        if (endpoint instanceof InetSocketAddress) {
          InetSocketAddress isa = (InetSocketAddress) endpoint;

          String hostname = isa.getHostString();
          if (socketFactory.isHostnameSupported(hostname)) {
            try {
              endpoint = socketFactory.addressFromHost(hostname, isa.getPort());
            } catch (SocketException e) {
              throw e;
            }
          }
        }
      }
    }

    if (!(endpoint instanceof AFUNIXSocketAddress)) {
      throw new IllegalArgumentException("Can only connect to endpoints of type "
          + AFUNIXSocketAddress.class.getName() + ", got: " + endpoint);
    }

    return (AFUNIXSocketAddress) endpoint;
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
    return AFUNIXInetAddress.isSupportedAddress(addr);
  }

  /**
   * Checks if a {@link SocketAddress} can be unwrapped to an {@link AFUNIXSocketAddress}.
   * 
   * @param addr The instance to check.
   * @return {@code true} if so.
   * @see #unwrap(InetAddress, int)
   */
  public static boolean isSupportedAddress(SocketAddress addr) {
    return (addr instanceof AFUNIXSocketAddress);
  }

  static InetAddress getInetAddress(FileDescriptor fdesc, boolean peerName) {
    if (!fdesc.valid()) {
      return null;
    }
    byte[] addr = NativeUnixSocket.sockname(fdesc, peerName);
    if (addr == null) {
      return null;
    }
    return AFUNIXInetAddress.wrapAddress(addr);
  }

  static AFUNIXSocketAddress getSocketAddress(FileDescriptor fdesc, boolean peerName) {
    return getSocketAddress(fdesc, peerName, 0);
  }

  static AFUNIXSocketAddress getSocketAddress(FileDescriptor fdesc, boolean peerName, int port) {
    if (!fdesc.valid()) {
      return null;
    }
    byte[] addr = NativeUnixSocket.sockname(fdesc, peerName);
    if (addr == null) {
      return null;
    }
    try {
      // FIXME we could infer the "port" from the path if the socket factory supports that
      return AFUNIXSocketAddress.unwrap(AFUNIXInetAddress.wrapAddress(addr), port);
    } catch (SocketException e) {
      throw new IllegalStateException(e);
    }
  }

  static ByteBuffer newSockAddrUnDirectBuffer() {
    return ByteBuffer.allocateDirect(SOCKADDR_UN_LENGTH);
  }

  static ByteBuffer newSockAddrUnKeyBuffer() {
    return ByteBuffer.allocate(SOCKADDR_UN_LENGTH);
  }

  synchronized InetAddress getInetAddress() {
    if (inetAddress == null) {
      inetAddress = AFUNIXInetAddress.wrapAddress(bytes);
    }
    return inetAddress;
  }
}
