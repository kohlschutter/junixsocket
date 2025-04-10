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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.newsclub.net.unix.pool.ObjectPool;
import org.newsclub.net.unix.pool.ObjectPool.Lease;

import com.google.errorprone.annotations.Immutable;
import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Some {@link SocketAddress} that is supported by junixsocket, such as {@link AFUNIXSocketAddress}.
 *
 * @author Christian Kohlschütter
 */
@Immutable
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.CyclomaticComplexity"})
public abstract class AFSocketAddress extends InetSocketAddress {
  private static final long serialVersionUID = 1L; // do not change!

  /**
   * Just a marker for "don't actually bind" (checked with "=="). Used in combination with a
   * superclass' bind method, which should trigger "setBound()", etc.
   */
  static final AFSocketAddress INTERNAL_DUMMY_BIND = new SentinelSocketAddress(0);
  static final AFSocketAddress INTERNAL_DUMMY_CONNECT = new SentinelSocketAddress(1);
  static final AFSocketAddress INTERNAL_DUMMY_DONT_CONNECT = new SentinelSocketAddress(2);

  private static final int SOCKADDR_NATIVE_FAMILY_OFFSET = NativeUnixSocket.isLoaded() //
      ? NativeUnixSocket.sockAddrNativeFamilyOffset() : -1;

  private static final int SOCKADDR_NATIVE_DATA_OFFSET = NativeUnixSocket.isLoaded() //
      ? NativeUnixSocket.sockAddrNativeDataOffset() : -1;

  private static final int SOCKADDR_MAX_LEN = NativeUnixSocket.isLoaded() //
      ? NativeUnixSocket.sockAddrLength(0) : 256;

  private static final Map<AFAddressFamily<?>, Map<Integer, Map<ByteBuffer, AFSocketAddress>>> ADDRESS_CACHE =
      new HashMap<>();

  static final ObjectPool<ByteBuffer> SOCKETADDRESS_BUFFER_TL = ObjectPool.newThreadLocalPool(
      () -> {
        return AFSocketAddress.newSockAddrDirectBuffer(SOCKADDR_MAX_LEN);
      }, (o) -> {
        o.clear();
        return true;
      });

  private static final boolean USE_DESERIALIZATION_FOR_INIT;

  static {
    String v = System.getProperty("org.newsclub.net.unix.AFSocketAddress.deserialize", "");
    USE_DESERIALIZATION_FOR_INIT = v.isEmpty() ? NativeLibraryLoader.isAndroid() : Boolean
        .parseBoolean(v);
  }

  /**
   * Some byte-level representation of this address, which can only be converted to a native
   * representation in combination with the domain ID.
   */
  private byte[] bytes;

  /**
   * An {@link InetAddress}-wrapped representation of this address. Only created upon demand.
   */
  private InetAddress inetAddress = null; // derived from bytes

  /**
   * The system-native representation of this address, or {@code null}.
   */
  @SuppressWarnings("PMD.ImmutableField")
  private transient ByteBuffer nativeAddress;

  /**
   * The address family.
   */
  private transient AFAddressFamily<?> addressFamily;

  /**
   * Creates a new socket address.
   *
   * @param port The port.
   * @param socketAddress The socket address in junixsocket-specific byte-array representation.
   * @param nativeAddress The socket address in system-native representation.
   * @param af The address family.
   * @throws SocketException on error.
   */
  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  protected AFSocketAddress(int port, final byte[] socketAddress, Lease<ByteBuffer> nativeAddress,
      AFAddressFamily<?> af) throws SocketException {
    /*
     * Initializing the superclass with an unresolved hostname helps us pass the #equals and
     * #hashCode checks, which unfortunately are declared final in InetSocketAddress.
     *
     * Using a resolved address (with the address bit initialized) would be ideal, but resolved
     * addresses can only be IPv4 or IPv6 (at least as of Java 16 and earlier).
     */
    super(AFInetAddress.createUnresolvedHostname(socketAddress, af), port >= 0 && port <= 0xffff
        ? port : 0);
    initAFSocketAddress(this, port, socketAddress, nativeAddress, af);
  }

  /**
   * Only for {@link SentinelSocketAddress}.
   *
   * @param clazz The {@link SentinelSocketAddress} class.
   * @param port A sentinel port number.
   */
  @SuppressWarnings("PMD.UnusedFormalParameter")
  AFSocketAddress(Class<SentinelSocketAddress> clazz, int port) {
    super(InetAddress.getLoopbackAddress(), port);
    this.nativeAddress = null;
    this.bytes = new byte[0];
    this.addressFamily = null;
  }

  @SuppressWarnings({"cast", "this-escape"})
  private static void initAFSocketAddress(AFSocketAddress addr, int port,
      final byte[] socketAddress, Lease<ByteBuffer> nativeAddress, AFAddressFamily<?> af)
      throws SocketException {
    if (socketAddress.length == 0) {
      throw new SocketException("Illegal address length: " + socketAddress.length);
    }

    addr.nativeAddress = nativeAddress == null ? null : (ByteBuffer) (Object) nativeAddress.get()
        .duplicate().rewind();
    if (port < -1) {
      throw new IllegalArgumentException("port out of range");
    } else if (port > 0xffff) {
      if (!NativeUnixSocket.isLoaded()) {
        throw (SocketException) new SocketException(
            "Cannot set SocketAddress port - junixsocket JNI library is not available").initCause(
                NativeUnixSocket.unsupportedException());
      }
      NativeUnixSocket.setPort1(addr, port);
    }

    addr.bytes = socketAddress.clone();
    addr.addressFamily = af;
  }

  /**
   * Returns a new {@link AFSocketAddress} instance via deserialization. This is a trick to
   * workaround certain environments that do not allow the construction of {@link InetSocketAddress}
   * instances without trying DNS resolution.
   *
   * @param <A> The subclass (must be a direct subclass of {@link AFSocketAddress}).
   * @param port The port to use.
   * @param socketAddress The junixsocket representation of the socket address.
   * @param nativeAddress The system-native representation of the socket address, or {@code null}.
   * @param af The address family, corresponding to the subclass
   * @param constructor The constructor to use as fallback
   * @return The new instance.
   * @throws SocketException on error.
   */
  @SuppressFBWarnings("OBJECT_DESERIALIZATION") // we craft the serialized data
  protected static <A extends AFSocketAddress> A newDeserializedAFSocketAddress(int port,
      final byte[] socketAddress, Lease<ByteBuffer> nativeAddress, AFAddressFamily<A> af,
      AFSocketAddressConstructor<A> constructor) throws SocketException {
    String hostname = AFInetAddress.createUnresolvedHostname(socketAddress, af);
    if (hostname == null || hostname.isEmpty()) {
      return constructor.newAFSocketAddress(port, socketAddress, nativeAddress);
    }
    try (ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(AFSocketAddress
        .craftSerializedObject(af.getSocketAddressClass(), hostname, (port >= 0 && port <= 0xffff
            ? port : 0))))) {
      @SuppressWarnings("unchecked")
      A addr = (A) oin.readObject();
      initAFSocketAddress(addr, port, socketAddress, nativeAddress, af);
      return addr;
    } catch (SocketException e) {
      throw e;
    } catch (ClassNotFoundException | IOException e) {
      throw (SocketException) new SocketException("Unexpected deserialization problem").initCause(
          e);
    }
  }

  /**
   * Creates a byte-representation of a serialized {@link AFSocketAddress} instance, overriding
   * hostname and port, which allows bypassing DNS resolution.
   *
   * @param className The actual subclass.
   * @param hostname The hostname to use (must not be empty or null).
   * @param port The port to use.
   * @return The byte representation.
   */
  private static byte[] craftSerializedObject(Class<? extends AFSocketAddress> className,
      String hostname, int port) {
    ByteBuffer bb = ByteBuffer.allocate(768);
    bb.putShort((short) 0xaced); // STREAM_MAGIC
    bb.putShort((short) 5); // STREAM_VERSION
    bb.put((byte) 0x73); // TC_OBJECT
    bb.put((byte) 0x72); // TC_CLASSDESC

    putShortLengthUtf8(bb, className.getName());
    bb.putLong(1); // serialVersionUID of subclass (expected to be 1)
    bb.putInt(0x02000078);
    bb.put((byte) 0x72);

    putShortLengthUtf8(bb, AFSocketAddress.class.getName());
    bb.putLong(serialVersionUID); // serialVersionUID of AFSocketAddress
    bb.putInt(0x0300025B);
    putShortLengthUtf8(bb, "bytes");

    bb.putInt(0x7400025B);
    bb.putShort((short) 0x424C);

    putShortLengthUtf8(bb, "inetAddress");
    bb.put((byte) 0x74);

    putShortLengthEncodedClassName(bb, InetAddress.class);

    bb.putShort((short) 0x7872);
    putShortLengthUtf8(bb, InetSocketAddress.class.getName());
    bb.putLong(5076001401234631237L); // NOPMD InetSocketAddress serialVersionUID

    bb.putInt(0x03000349);
    putShortLengthUtf8(bb, "port");

    bb.put((byte) 0x4C);
    putShortLengthUtf8(bb, "addr");

    bb.putInt(0x71007E00);
    bb.putShort((short) 0x034C);
    putShortLengthUtf8(bb, "hostname");
    bb.put((byte) 0x74);

    putShortLengthEncodedClassName(bb, String.class);

    bb.putShort((short) 0x7872);
    putShortLengthUtf8(bb, SocketAddress.class.getName());
    bb.putLong(5215720748342549866L); // NOPMD SocketAddress serialVersionUID

    bb.putInt(0x02000078);
    bb.put((byte) 0x70);
    bb.putInt(port);

    bb.putShort((short) 0x7074);
    putShortLengthUtf8(bb, hostname);

    bb.putInt(0x78707077);
    bb.put((byte) 0x0B);

    putShortLengthUtf8(bb, "undefined");

    bb.put((byte) 0x78); // TC_ENDBLOCKDATA
    bb.flip();

    byte[] buf = new byte[bb.remaining()];
    bb.get(buf);
    return buf;
  }

  private static void putShortLengthEncodedClassName(ByteBuffer bb, Class<?> klazz) {
    putShortLengthUtf8(bb, "L" + klazz.getName().replace('.', '/') + ";");
  }

  private static void putShortLengthUtf8(ByteBuffer bb, String s) {
    byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
    bb.putShort((short) utf8.length);
    bb.put(utf8);
  }

  /**
   * Checks if {@link AFSocketAddress} instantiation should be performed via deserialization.
   *
   * @return {@code true} if so.
   * @see #newDeserializedAFSocketAddress(int, byte[], Lease, AFAddressFamily,
   *      AFSocketAddressConstructor)
   */
  protected static boolean isUseDeserializationForInit() {
    return USE_DESERIALIZATION_FOR_INIT;
  }

  /**
   * Checks if the address can be resolved to a {@link File}.
   *
   * @return {@code true} if the address has a filename.
   */
  public abstract boolean hasFilename();

  /**
   * Returns the {@link File} corresponding with this address, if possible.
   *
   * A {@link FileNotFoundException} is thrown if there is no filename associated with the address,
   * which applies to addresses in the abstract namespace, for example.
   *
   * @return The filename.
   * @throws FileNotFoundException if the address is not associated with a filename.
   */
  public abstract File getFile() throws FileNotFoundException;

  /**
   * Returns the corresponding {@link AFAddressFamily}.
   *
   * @return The address family instance.
   */
  public final AFAddressFamily<?> getAddressFamily() {
    return addressFamily;
  }

  /**
   * Wraps the socket name/peer name of a file descriptor as an {@link InetAddress}.
   *
   * @param fdesc The file descriptor.
   * @param peerName If {@code true}, the remote peer name (instead of the local name) is retrieved.
   * @param af The address family.
   * @return The {@link InetAddress}.
   */
  protected static final InetAddress getInetAddress(FileDescriptor fdesc, boolean peerName,
      AFAddressFamily<?> af) {
    if (!fdesc.valid()) {
      return null;
    }
    byte[] addr = NativeUnixSocket.sockname(af.getDomain(), fdesc, peerName);
    if (addr == null) {
      return null;
    }
    return AFInetAddress.wrapAddress(addr, af);
  }

  /**
   * Gets the socket name/peer name of a file descriptor as an {@link AFSocketAddress}.
   *
   * @param <A> The corresponding address type.
   * @param fdesc The file descriptor.
   * @param requestPeerName If {@code true}, the remote peer name (instead of the local name) is
   *          retrieved.
   * @param port The port.
   * @param af The address family.
   * @return The {@link InetAddress}.
   */
  protected static final <A extends AFSocketAddress> @Nullable A getSocketAddress(
      FileDescriptor fdesc, boolean requestPeerName, int port, AFAddressFamily<A> af) {
    if (!fdesc.valid()) {
      return null;
    }
    byte[] addr = NativeUnixSocket.sockname(af.getDomain(), fdesc, requestPeerName);
    if (addr == null) {
      return null;
    }
    try {
      // FIXME we could infer the "port" from the path if the socket factory supports that
      return AFSocketAddress.unwrap(AFInetAddress.wrapAddress(addr, af), port, af);
    } catch (SocketException e) {
      throw new IllegalStateException(e);
    }
  }

  static final AFSocketAddress preprocessSocketAddress(
      Class<? extends AFSocketAddress> supportedAddressClass, SocketAddress endpoint,
      AFSocketAddressFromHostname<?> afh) throws SocketException {
    Objects.requireNonNull(endpoint);
    if (endpoint instanceof SentinelSocketAddress) {
      return (SentinelSocketAddress) endpoint;
    }

    if (!(endpoint instanceof AFSocketAddress)) {
      if (afh != null) {
        if (endpoint instanceof InetSocketAddress) {
          InetSocketAddress isa = (InetSocketAddress) endpoint;

          String hostname = isa.getHostString();
          if (afh.isHostnameSupported(hostname)) {
            try {
              endpoint = afh.addressFromHost(hostname, isa.getPort());
            } catch (SocketException e) {
              throw e;
            }
          }
        }
      }
      endpoint = mapOrFail(endpoint, supportedAddressClass);
    }

    Objects.requireNonNull(endpoint);

    if (!supportedAddressClass.isAssignableFrom(endpoint.getClass())) {
      throw new IllegalArgumentException("Can only connect to endpoints of type "
          + supportedAddressClass.getName() + ", got: " + endpoint.getClass() + ": " + endpoint);
    }

    return (AFSocketAddress) endpoint;
  }

  /**
   * Returns the (non-native) byte-level representation of this address.
   *
   * @return The byte array.
   */
  protected final byte[] getBytes() {
    return bytes; // NOPMD
  }

  /**
   * Returns a "special" {@link InetAddress} that contains information about this
   * {@link AFSocketAddress}.
   *
   * IMPORTANT: This {@link InetAddress} does not properly compare (using
   * {@link InetAddress#equals(Object)} and {@link InetAddress#hashCode()}). It should be used
   * exclusively to circumvent existing APIs like {@link DatagramSocket} that only accept/return
   * {@link InetAddress} and not arbitrary {@link SocketAddress} types.
   *
   * @return The "special" {@link InetAddress}.
   */
  public final InetAddress wrapAddress() {
    return AFInetAddress.wrapAddress(bytes, getAddressFamily());
  }

  /**
   * A reference to the constructor of an AFSocketAddress subclass.
   *
   * @param <T> The actual subclass.
   * @author Christian Kohlschütter
   */
  @FunctionalInterface
  protected interface AFSocketAddressConstructor<T extends AFSocketAddress> {
    /**
     * Constructs a new AFSocketAddress instance.
     *
     * @param port The port.
     * @param socketAddress The socket address in junixsocket-specific byte-array representation.
     * @param nativeAddress The socket address in system-native representation.
     * @return The instance.
     * @throws SocketException on error.
     */
    @NonNull
    T newAFSocketAddress(int port, byte[] socketAddress, Lease<ByteBuffer> nativeAddress)
        throws SocketException;
  }

  /**
   * Resolves a junixsocket-specific byte-array representation of an {@link AFSocketAddress} to an
   * actual {@link AFSocketAddress} instance, possibly reusing a cached instance.
   *
   * @param <A> The concrete {@link AFSocketAddress} that is supported by this type.
   * @param socketAddress The socket address in junixsocket-specific byte-array representation.
   * @param port The port.
   * @param af The address family.
   * @return The instance.
   * @throws SocketException on error.
   */
  @SuppressWarnings({"unchecked", "null"})
  protected static final <A extends AFSocketAddress> A resolveAddress(final byte[] socketAddress,
      int port, AFAddressFamily<A> af) throws SocketException {
    if (socketAddress.length == 0) {
      throw new SocketException("Address cannot be empty");
    }

    if (port == -1) {
      port = 0;
    }

    try (Lease<ByteBuffer> lease = SOCKETADDRESS_BUFFER_TL.take()) {
      ByteBuffer direct = lease.get();
      int limit = NativeUnixSocket.isLoaded() ? NativeUnixSocket.bytesToSockAddr(af.getDomain(),
          direct, socketAddress) : -1;
      if (limit == -1) {
        // not supported, but we can still create an address
        return af.getAddressConstructor().newAFSocketAddress(port, socketAddress, null);
      } else if (limit > SOCKADDR_MAX_LEN) {
        throw new IllegalStateException("Unexpected address length");
      }
      direct.rewind();
      direct.limit(limit);

      A instance;
      synchronized (AFSocketAddress.class) {
        Map<ByteBuffer, AFSocketAddress> map;
        Map<Integer, Map<ByteBuffer, AFSocketAddress>> mapPorts = ADDRESS_CACHE.get(af);
        if (mapPorts == null) {
          instance = null;
          mapPorts = new HashMap<>();
          map = new HashMap<>();
          mapPorts.put(port, map);
          ADDRESS_CACHE.put(af, mapPorts);
        } else {
          map = mapPorts.get(port);
          if (map == null) {
            instance = null;
            map = new HashMap<>();
            mapPorts.put(port, map);
          } else {
            instance = (A) map.get(direct);
          }
        }

        if (instance == null) {
          ByteBuffer key = newSockAddrKeyBuffer(limit);
          key.put(direct);
          key = key.asReadOnlyBuffer();

          instance = af.getAddressConstructor().newAFSocketAddress(port, socketAddress, ObjectPool
              .unpooledLease(key));

          map.put(key, instance);
        }
      }
      return instance;
    }
  }

  @SuppressWarnings("null")
  static final <A extends AFSocketAddress> A ofInternal(ByteBuffer socketAddressBuffer,
      AFAddressFamily<A> af) throws SocketException {
    synchronized (AFSocketAddress.class) {
      socketAddressBuffer.rewind();

      Map<Integer, Map<ByteBuffer, AFSocketAddress>> mapPorts = ADDRESS_CACHE.get(af);
      if (mapPorts != null) {
        Map<ByteBuffer, AFSocketAddress> map = mapPorts.get(0); // FIXME get port, something like
                                                                // sockAddrToPort
        if (map != null) {
          @SuppressWarnings("unchecked")
          A address = (A) map.get(socketAddressBuffer);
          if (address != null) {
            return address;
          }
        }
      }

      try (Lease<ByteBuffer> leasedBuffer = socketAddressBuffer.isDirect() ? null
          : getNativeAddressDirectBuffer(Math.min(socketAddressBuffer.limit(), SOCKADDR_MAX_LEN))) {
        if (leasedBuffer != null) {
          ByteBuffer buf = leasedBuffer.get();
          buf.put(socketAddressBuffer);
          socketAddressBuffer = buf;
        }

        byte[] sockAddrToBytes = NativeUnixSocket.sockAddrToBytes(af.getDomain(),
            socketAddressBuffer);
        if (sockAddrToBytes == null) {
          return null;
        } else {
          return AFSocketAddress.resolveAddress(sockAddrToBytes, 0, af);
        }
      }
    }
  }

  /**
   * Wraps an address as an {@link InetAddress}.
   *
   * @param af The address family.
   * @return The {@link InetAddress}.
   */
  protected final synchronized InetAddress getInetAddress(AFAddressFamily<?> af) {
    if (inetAddress == null) {
      inetAddress = AFInetAddress.wrapAddress(bytes, af);
    }
    return inetAddress;
  }

  /**
   * Wraps this address as an {@link InetAddress}.
   *
   * @return The {@link InetAddress}.
   */
  protected final InetAddress getInetAddress() {
    return getInetAddress(getAddressFamily());
  }

  @SuppressWarnings("null")
  static final @NonNull ByteBuffer newSockAddrDirectBuffer(int length) {
    return ByteBuffer.allocateDirect(length);
  }

  @SuppressWarnings("null")
  static final @NonNull ByteBuffer newSockAddrKeyBuffer(int length) {
    return ByteBuffer.allocate(length);
  }

  /**
   * Returns an {@link AFSocketAddress} given a special {@link InetAddress} that encodes the byte
   * sequence of an AF_UNIX etc. socket address, like those returned by {@link #wrapAddress()}.
   *
   * @param <A> The corresponding address type.
   * @param address The "special" {@link InetAddress}.
   * @param port The port (use 0 for "none").
   * @param af The address family.
   * @return The {@link AFSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  @SuppressWarnings("null")
  @NonNull
  protected static final <A extends AFSocketAddress> A unwrap(InetAddress address, int port,
      AFAddressFamily<A> af) throws SocketException {
    Objects.requireNonNull(address);
    return resolveAddress(AFInetAddress.unwrapAddress(address, af), port, af);
  }

  /**
   * Returns an {@link AFSocketAddress} given a special {@link InetAddress} hostname that encodes
   * the byte sequence of an AF_UNIX etc. socket address, like those returned by
   * {@link #wrapAddress()}.
   *
   * @param <A> The corresponding address type.
   * @param hostname The "special" hostname, as provided by {@link InetAddress#getHostName()}.
   * @param port The port (use 0 for "none").
   * @param af The address family.
   * @return The {@link AFSocketAddress} instance.
   * @throws SocketException if the operation fails, for example when an unsupported address is
   *           specified.
   */
  @SuppressWarnings("null")
  @NonNull
  protected static final <A extends AFSocketAddress> A unwrap(String hostname, int port,
      AFAddressFamily<A> af) throws SocketException {
    Objects.requireNonNull(hostname);
    return resolveAddress(AFInetAddress.unwrapAddress(hostname, af), port, af);
  }

  static final int unwrapAddressDirectBufferInternal(ByteBuffer socketAddressBuffer,
      SocketAddress address) throws SocketException {
    if (!NativeUnixSocket.isLoaded()) {
      throw new SocketException("Unsupported operation; junixsocket native library is not loaded");
    }
    Objects.requireNonNull(address);

    address = AFSocketAddress.mapOrFail(address, AFSocketAddress.class);
    AFSocketAddress socketAddress = (AFSocketAddress) address;

    byte[] addr = socketAddress.getBytes();
    int domain = socketAddress.getAddressFamily().getDomain();

    int len = NativeUnixSocket.bytesToSockAddr(domain, socketAddressBuffer, addr);
    if (len == -1) {
      throw new SocketException("Unsupported domain");
    }
    return len;
  }

  /**
   * Returns a thread-local direct ByteBuffer containing the native socket address representation of
   * this {@link AFSocketAddress}.
   *
   * @return The direct {@link ByteBuffer}.
   */
  final Lease<ByteBuffer> getNativeAddressDirectBuffer() throws SocketException {
    ByteBuffer address = nativeAddress;
    if (address == null) {
      throw (SocketException) new SocketException("Cannot access native address").initCause(
          NativeUnixSocket.unsupportedException());
    }
    address = address.duplicate();

    Lease<ByteBuffer> lease = getNativeAddressDirectBuffer(address.limit());
    ByteBuffer direct = lease.get();
    address.position(0);
    direct.put(address);

    return lease;
  }

  static final Lease<ByteBuffer> getNativeAddressDirectBuffer(int limit) {
    Lease<ByteBuffer> lease = SOCKETADDRESS_BUFFER_TL.take();
    ByteBuffer direct = lease.get();
    direct.position(0);
    direct.limit(limit);
    return lease;
  }

  /**
   * Checks if the given address is supported by this address family.
   *
   * @param addr The address.
   * @param af The address family.
   * @return {@code true} if supported.
   */
  protected static final boolean isSupportedAddress(InetAddress addr, AFAddressFamily<?> af) {
    return AFInetAddress.isSupportedAddress(addr, af);
  }

  /**
   * Writes the native (system-level) representation of this address to the given buffer.
   *
   * The position of the target buffer will be at the end (i.e., after) the written data.
   *
   * @param buf The target buffer.
   * @throws IOException on error.
   */
  public final void writeNativeAddressTo(ByteBuffer buf) throws IOException {
    if (nativeAddress == null) {
      throw (SocketException) new SocketException("Cannot access native address").initCause(
          NativeUnixSocket.unsupportedException());
    }
    buf.put(nativeAddress);
  }

  /**
   * Creates a new socket connected to this address.
   *
   * @return The socket instance.
   * @throws IOException on error.
   */
  public AFSocket<?> newConnectedSocket() throws IOException {
    AFSocket<?> socket = getAddressFamily().newSocket();
    socket.connect(this);
    return socket;
  }

  /**
   * Creates a new server socket bound to this address.
   *
   * @return The server socket instance.
   * @throws IOException on error.
   */
  public AFServerSocket<?> newBoundServerSocket() throws IOException {
    AFServerSocket<?> serverSocket = getAddressFamily().newServerSocket();
    serverSocket.bind(this);
    return serverSocket;
  }

  /**
   * Creates a new server socket force-bound to this address (i.e., any additional call to
   * {@link ServerSocket#bind(SocketAddress)} will ignore the passed address and use this one
   * instead.
   *
   * @return The server socket instance.
   * @throws IOException on error.
   */
  public AFServerSocket<?> newForceBoundServerSocket() throws IOException {
    AFServerSocket<?> serverSocket = getAddressFamily().newServerSocket();
    serverSocket.forceBindAddress(this).bind(this);
    return serverSocket;
  }

  /**
   * Tries to parse the given URI and return a corresponding {@link AFSocketAddress} for it.
   *
   * NOTE: Only certain URI schemes are supported, such as {@code unix://} (for
   * {@link AFUNIXSocketAddress}) and {@code tipc://} for {@link AFTIPCSocketAddress}.
   *
   * @param u The URI.
   * @return The address.
   * @throws SocketException on error.
   * @see AFAddressFamily#uriSchemes()
   */
  @SuppressWarnings("PMD.ShortMethodName")
  public static AFSocketAddress of(URI u) throws SocketException {
    return of(u, -1);
  }

  /**
   * Tries to parse the given URI and return a corresponding {@link AFSocketAddress} for it.
   *
   * NOTE: Only certain URI schemes are supported, such as {@code unix://} (for
   * {@link AFUNIXSocketAddress}) and {@code tipc://} for {@link AFTIPCSocketAddress}.
   *
   * @param u The URI.
   * @param overridePort The port to forcibly use, or {@code -1} for "don't override".
   * @return The address.
   * @throws SocketException on error.
   * @see AFAddressFamily#uriSchemes()
   */
  @SuppressWarnings("PMD.ShortMethodName")
  public static AFSocketAddress of(URI u, int overridePort) throws SocketException {
    AFAddressFamily<?> af = AFAddressFamily.getAddressFamily(u);
    if (af == null) {
      throw new SocketException("Cannot resolve AFSocketAddress from URI scheme: " + u.getScheme());
    }
    return af.parseURI(u, overridePort);
  }

  /**
   * Tries to create a URI based on this {@link AFSocketAddress}.
   *
   * @param scheme The target scheme.
   * @param template An optional template to reuse certain parameters (e.g., the "path" component
   *          for an {@code http} request), or {@code null}.
   * @return The URI.
   * @throws IOException on error.
   */
  public URI toURI(String scheme, URI template) throws IOException {
    throw new IOException("Unsupported operation");
  }

  /**
   * Returns a address string that can be used with {@code socat}'s {@code SOCKET-CONNECT},
   * {@code SOCKET-LISTEN}, {@code SOCKET-DATAGRAM}, etc., address types, or {@code null} if the
   * address type is not natively supported by this platform.
   *
   * This call is mostly suited for debugging purposes. The resulting string is specific to the
   * platform the code is executed on, and thus may be different among platforms.
   *
   * @param socketType The socket type, or {@code null} to omit from string.
   * @param socketProtocol The socket protocol, or {@code null} to omit from string.
   * @return The string (such as 1:0:x2f746d702f796f).
   * @throws IOException on error (a {@link SocketException} is thrown if the native address cannot
   *           be accessed).
   */
  public @Nullable @SuppressWarnings("PMD.NPathComplexity") String toSocatAddressString(
      AFSocketType socketType, AFSocketProtocol socketProtocol) throws IOException {

    if (SOCKADDR_NATIVE_FAMILY_OFFSET == -1 || SOCKADDR_NATIVE_DATA_OFFSET == -1) {
      return null;
    }
    if (nativeAddress == null) {
      throw (SocketException) new SocketException("Cannot access native address").initCause(
          NativeUnixSocket.unsupportedException());
    }
    if (socketProtocol != null && socketProtocol.getId() != 0) {
      throw new IOException("Protocol not (yet) supported"); // FIXME support additional protocols
    }

    int family = (nativeAddress.get(SOCKADDR_NATIVE_FAMILY_OFFSET) & 0xFF);
    int type = socketType == null ? -1 : NativeUnixSocket.sockTypeToNative(socketType.getId());
    StringBuilder sb = new StringBuilder();
    sb.append(family);
    if (type != -1) {
      sb.append(':');
      sb.append(type);
    }
    if (socketProtocol != null) {
      sb.append(':');
      sb.append(socketProtocol.getId()); // FIXME needs native conversion
    }
    sb.append(":x");
    int n = nativeAddress.limit();
    while (n > 1 && nativeAddress.get(n - 1) == 0) {
      n--;
    }
    for (int pos = SOCKADDR_NATIVE_DATA_OFFSET; pos < n; pos++) {
      byte b = nativeAddress.get(pos);
      sb.append(String.format(Locale.ENGLISH, "%02x", b));
    }
    return sb.toString();
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
   * @param other The other address that could be covered by this address.
   * @return {@code true} if the other address could be covered.
   */
  public boolean covers(AFSocketAddress other) {
    return this.equals(other);
  }

  /**
   * Custom serialization: Reference {@link AFAddressFamily} instance by identifier string.
   *
   * @param in The {@link ObjectInputStream}.
   * @throws ClassNotFoundException on error.
   * @throws IOException on error.
   */
  private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
    in.defaultReadObject();

    String af = in.readUTF();
    if ("undefined".equals(af)) {
      this.addressFamily = null;
    } else {
      this.addressFamily = Objects.requireNonNull(AFAddressFamily.getAddressFamily(af),
          "address family");
    }
  }

  /**
   * Custom serialization: Reference {@link AFAddressFamily} instance by identifier string.
   *
   * @param out The {@link ObjectOutputStream}.
   * @throws IOException on error.
   */
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeUTF(addressFamily == null ? "undefined" : addressFamily.getJuxString());
  }

  /**
   * Returns a string representation of the argument as an unsigned decimal value.
   * <p>
   * Works like {@link Integer#toUnsignedString(int)}; added to allow execution on Java 1.7.
   *
   * @param i The value.
   * @return The string.
   */
  static String toUnsignedString(int i) {
    return Long.toString(toUnsignedLong(i));
  }

  /**
   * Returns a string representation of the first argument as an unsigned integer value in the radix
   * specified by the second argument; added to allow execution on Java 1.7.
   *
   * @param i The value.
   * @param radix The radix.
   * @return The string.
   */
  static String toUnsignedString(int i, int radix) {
    return Long.toUnsignedString(toUnsignedLong(i), radix);
  }

  private static long toUnsignedLong(long x) {
    return x & 0xffffffffL;
  }

  /**
   * Parses the string argument as an unsigned integer in the radix specified by the second
   * argument. Works like {@link Integer#parseUnsignedInt(String, int)}; added to allow execution on
   * Java 1.7.
   *
   * @param s The string.
   * @param radix The radix.
   * @return The integer.
   * @throws NumberFormatException on parse error.
   */
  protected static int parseUnsignedInt(String s, int radix) throws NumberFormatException {
    if (s == null || s.isEmpty()) {
      throw new NumberFormatException("Cannot parse null or empty string");
    }

    int len = s.length();
    if (s.startsWith("-")) {
      throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s);
    }

    if (len <= 5 || (radix == 10 && len <= 9)) {
      return Integer.parseInt(s, radix);
    } else {
      long ell = Long.parseLong(s, radix);
      if ((ell & 0xffff_ffff_0000_0000L) == 0) {
        return (int) ell;
      } else {
        throw new NumberFormatException("String value exceeds " + "range of unsigned int: " + s);
      }
    }
  }

  /**
   * Checks if the given {@link SocketAddress} can be mapped to an {@link AFSocketAddress}. This is
   * the case if the address either already is an {@link AFSocketAddress}, {@code null}, or
   * something that has an equivalent representation, such as {@code UnixDomainSocketAddress}.
   *
   * @param addr The address.
   * @return {@code true} if mappable.
   */
  public static boolean canMap(SocketAddress addr) {
    return canMap(addr, AFSocketAddress.class);
  }

  /**
   * Checks if the given {@link SocketAddress} can be mapped to a specific {@link AFSocketAddress}
   * subclass. This is the case if the address either already is such an {@link AFSocketAddress},
   * {@code null}, or something that has an equivalent representation, such as
   * {@code UnixDomainSocketAddress}.
   *
   * @param addr The address.
   * @param targetAddressClass The target address class to map to.
   * @return {@code true} if mappable.
   */
  public static boolean canMap(SocketAddress addr,
      Class<? extends AFSocketAddress> targetAddressClass) {
    if (addr == null) {
      return true;
    } else if (targetAddressClass.isAssignableFrom(addr.getClass())) {
      return true;
    }
    AFSupplier<? extends AFSocketAddress> supplier = SocketAddressUtil.supplyAFSocketAddress(addr);
    if (supplier == null) {
      return false;
    }
    AFSocketAddress afAddr = supplier.get();
    if (afAddr == null) {
      return false;
    }
    return (targetAddressClass.isAssignableFrom(afAddr.getClass()));
  }

  /**
   * Maps the given address to an {@link AFSocketAddress}.
   *
   * @param addr The address.
   * @return The {@link AFSocketAddress}.
   * @throws IllegalArgumentException if the address could not be mapped.
   * @see #canMap(SocketAddress,Class)
   */
  public static AFSocketAddress mapOrFail(SocketAddress addr) {
    return mapOrFail(addr, AFSocketAddress.class);
  }

  /**
   * Maps the given address to a specific {@link AFSocketAddress} type.
   *
   * @param addr The address.
   * @param targetAddressClass The target address class.
   * @param <A> The target address type.
   * @return The {@link AFSocketAddress}.
   * @throws IllegalArgumentException if the address could not be mapped.
   * @see #canMap(SocketAddress,Class)
   */
  @SuppressWarnings("null")
  public static <A extends AFSocketAddress> A mapOrFail(SocketAddress addr,
      Class<A> targetAddressClass) {
    if (addr == null) {
      return null;
    } else if (targetAddressClass.isAssignableFrom(addr.getClass())) {
      return targetAddressClass.cast(addr);
    }

    AFSupplier<? extends AFSocketAddress> supplier = SocketAddressUtil.supplyAFSocketAddress(addr);
    if (supplier == null) {
      throw new IllegalArgumentException("Can only bind to endpoints of type "
          + AFSocketAddress.class.getName() + ": " + addr);
    }
    AFSocketAddress afAddr = supplier.get();
    if (afAddr == null || !targetAddressClass.isAssignableFrom(afAddr.getClass())) {
      throw new IllegalArgumentException("Can only bind to endpoints of type "
          + AFSocketAddress.class.getName() + ", and this specific address is unsupported: "
          + addr);
    }
    return targetAddressClass.cast(afAddr);
  }
}
