/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.net.URI;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.newsclub.net.unix.AFSocketAddress.AFSocketAddressConstructor;

/**
 * Describes an address family supported by junixsocket.
 * 
 * @param <A> The corresponding {@link AFSocketAddress} subclass.
 * @author Christian Kohlschütter
 */
public final class AFAddressFamily<A extends AFSocketAddress> {
  private static final Map<String, AFAddressFamily<?>> AF_MAP = Collections.synchronizedMap(
      new HashMap<>());
  private static final Map<String, AFAddressFamily<?>> URI_SCHEMES = Collections.synchronizedMap(
      new HashMap<>());
  private static final AtomicBoolean DEFERRED_INIT_DONE = new AtomicBoolean(false);

  private final int domain;
  private AFSocketAddressConstructor<A> addressConstructor;
  private @Nullable Class<A> addressClass;
  private final String juxString;
  private final String juxInetAddressSuffix;
  private final String addressClassname;

  private String selectorProviderClassname;

  private AFSocket.Constructor<A> socketConstructor;
  private AFServerSocket.Constructor<A> serverSocketConstructor;
  private AFSocketAddressConfig<A> addressConfig;

  private SelectorProvider selectorProvider = null;

  static {
    NativeUnixSocket.isLoaded(); // trigger init
  }

  private AFAddressFamily(String juxString, int domain, String addressClassname) {
    this.juxString = juxString;
    this.domain = domain; // FIXME validate
    this.addressClassname = addressClassname;
    this.juxInetAddressSuffix = "." + juxString + AFInetAddress.INETADDR_SUFFIX;
  }

  @SuppressWarnings("unchecked")
  static synchronized <A extends AFSocketAddress> @NonNull AFAddressFamily<A> registerAddressFamily(
      String juxString, int domain, String addressClassname) {
    AFAddressFamily<?> af = AF_MAP.get(juxString);
    if (af != null) {
      if (af.getDomain() != domain) {
        throw new IllegalStateException("Wrong domain for address family " + juxString + ": " + af
            .getDomain() + " vs. " + domain);
      }
      return (AFAddressFamily<A>) af;
    }

    af = new AFAddressFamily<>(juxString, domain, addressClassname);
    AF_MAP.put(juxString, af);

    return (AFAddressFamily<A>) af;
  }

  static synchronized void triggerInit() {
    for (AFAddressFamily<?> af : new HashSet<>(AF_MAP.values())) {
      if (af.addressClassname != null) {
        try {
          Class<?> clz = Class.forName(af.addressClassname);
          clz.getMethod("addressFamily").invoke(null);
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  static synchronized AFAddressFamily<?> getAddressFamily(String juxString) {
    return AF_MAP.get(juxString);
  }

  static AFAddressFamily<?> getAddressFamily(URI uri) {
    checkDeferredInit();
    Objects.requireNonNull(uri, "uri");
    String scheme = uri.getScheme();
    return URI_SCHEMES.get(scheme);
  }

  static void checkDeferredInit() {
    if (DEFERRED_INIT_DONE.compareAndSet(false, true)) {
      NativeUnixSocket.isLoaded();
      AFAddressFamily.triggerInit();
    }
  }

  int getDomain() {
    return domain;
  }

  String getJuxString() {
    return juxString;
  }

  AFSocketAddressConstructor<A> getAddressConstructor() {
    if (addressConstructor == null) {
      throw new UnsupportedAddressTypeException();
    }
    return addressConstructor;
  }

  private synchronized void checkProvider() {
    if (socketConstructor == null && selectorProvider == null) {
      try {
        getSelectorProvider();
      } catch (IllegalStateException e) {
        // ignore
      }
    }
  }

  AFSocket.Constructor<A> getSocketConstructor() {
    checkProvider();
    if (socketConstructor == null) {
      throw new UnsupportedAddressTypeException();
    }
    return socketConstructor;
  }

  AFServerSocket.Constructor<A> getServerSocketConstructor() {
    checkProvider();
    if (serverSocketConstructor == null) {
      throw new UnsupportedAddressTypeException();
    }
    return serverSocketConstructor;
  }

  Class<A> getSocketAddressClass() {
    if (addressClass == null) {
      throw new UnsupportedAddressTypeException();
    }
    return addressClass;
  }

  String getJuxInetAddressSuffix() {
    return juxInetAddressSuffix;
  }

  /**
   * Registers an address family.
   * 
   * @param <A> The supported address type.
   * @param juxString The sockaddr_* identifier as registered in native code.
   * @param addressClass The supported address subclass.
   * @param config The address-specific config object.
   * @return The corresponding {@link AFAddressFamily} instance.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static synchronized <A extends AFSocketAddress> AFAddressFamily<A> registerAddressFamily(
      String juxString, //
      Class<A> addressClass, AFSocketAddressConfig<A> config) {
    AFAddressFamily<?> af = getAddressFamily(juxString);
    if (af == null) {
      throw new IllegalStateException("Address family not supported by native code: " + juxString);
    }
    if (af.addressClassname != null && !addressClass.getName().equals(af.addressClassname)) {
      throw new IllegalStateException("Unexpected classname for address family " + juxString + ": "
          + addressClass.getName());
    }
    if (af.addressConstructor != null || af.addressClass != null) {
      throw new IllegalStateException("Already registered: " + juxString);
    }
    af.addressConfig = (AFSocketAddressConfig) config;
    af.addressConstructor = (AFSocketAddressConstructor) config.addressConstructor();
    af.addressClass = (Class) addressClass;
    synchronized (af) { // work-around for likely false positive Spotbugs error
      af.selectorProviderClassname = config.selectorProviderClassname();
    }

    for (String scheme : config.uriSchemes()) {
      if (scheme.isEmpty()) {
        throw new IllegalStateException("Invalid URI scheme; cannot register " + scheme + " for "
            + juxString);

      }
      if (URI_SCHEMES.containsKey(scheme)) {
        throw new IllegalStateException("URI scheme already registered; cannot register " + scheme
            + " for " + juxString);
      }
      URI_SCHEMES.put(scheme, af);
    }

    return (AFAddressFamily<A>) af;
  }

  /**
   * Registers an implementation.
   * 
   * @param <A> The supported address type.
   * @param juxString The sockaddr_* identifier as registered in native code.
   * @param addressFamily The supported address family as registered via
   *          {@link #registerAddressFamily(String, Class, AFSocketAddressConfig)}.
   * @param config The address family-specific configuration object.
   * @return The corresponding {@link AFAddressFamily} instance.
   */
  @SuppressWarnings({"unchecked", "rawtypes", "PMD.ExcessiveParameterList"})
  public static synchronized <A extends AFSocketAddress> AFAddressFamily<A> registerAddressFamilyImpl(
      String juxString, //
      AFAddressFamily<A> addressFamily, //
      AFAddressFamilyConfig<A> config) {
    Objects.requireNonNull(addressFamily);
    Objects.requireNonNull(config);

    AFAddressFamily<?> af = getAddressFamily(juxString);
    if (af == null) {
      throw new IllegalStateException("Unknown address family: " + juxString);
    }
    if (addressFamily != af) { // NOPMD.CompareObjectsWithEquals
      throw new IllegalStateException("Address family inconsistency: " + juxString);
    }
    if (af.socketConstructor != null) {
      throw new IllegalStateException("Already registered: " + juxString);
    }
    af.socketConstructor = (AFSocket.Constructor) config.socketConstructor();
    af.serverSocketConstructor = (AFServerSocket.Constructor) config.serverSocketConstructor();

    FileDescriptorCast.registerCastingProviders(config);

    return (AFAddressFamily<A>) af;
  }

  @SuppressWarnings("unchecked")
  AFSocketImplExtensions<A> initImplExtensions(AncillaryDataSupport ancillaryDataSupport) {
    switch (getDomain()) {
      case NativeUnixSocket.DOMAIN_TIPC:
        return (AFSocketImplExtensions<A>) new AFTIPCSocketImplExtensions(ancillaryDataSupport);
      default:
        throw new UnsupportedOperationException();
    }
  }

  /**
   * Creates a new, unconnected, unbound socket compatible with this socket address.
   * 
   * @return The socket instance.
   * @throws IOException on error.
   */
  public AFSocket<?> newSocket() throws IOException {
    try {
      return getSocketConstructor().newInstance(null, null);
    } catch (UnsupportedOperationException e) {
      throw (SocketException) new SocketException().initCause(e);
    }
  }

  /**
   * Creates a new, unconnected, unbound server socket compatible with this socket address.
   * 
   * @return The server socket instance.
   * @throws IOException on error.
   */
  public AFServerSocket<?> newServerSocket() throws IOException {
    try {
      return getServerSocketConstructor().newInstance(null);
    } catch (UnsupportedOperationException e) {
      throw (SocketException) new SocketException().initCause(e);
    }
  }

  /**
   * Creates a new, unconnected, unbound {@link SocketChannel} compatible with this socket address.
   * 
   * @return The socket instance.
   * @throws IOException on error.
   */
  public AFSocketChannel<?> newSocketChannel() throws IOException {
    return newSocket().getChannel();
  }

  /**
   * Creates a new, unconnected, unbound {@link ServerSocketChannel} compatible with this socket
   * address.
   * 
   * @return The socket instance.
   * @throws IOException on error.
   */
  public AFServerSocketChannel<?> newServerSocketChannel() throws IOException {
    return newServerSocket().getChannel();
  }

  AFSocketAddress parseURI(URI u, int overridePort) throws SocketException {
    if (addressConfig == null) {
      throw new SocketException("Cannot instantiate addresses of type " + addressClass);
    }
    return addressConfig.parseURI(u, overridePort);
  }

  /**
   * Returns the set of supported URI schemes that can be parsed to some {@link AFSocketAddress}.
   * 
   * The set is dependent on which {@link AFSocketAddress} implementations are registered with
   * junixsocket.
   * 
   * @return The set of supported URI schemes.
   */
  public static synchronized Set<String> uriSchemes() {
    checkDeferredInit();
    return Collections.unmodifiableSet(URI_SCHEMES.keySet());
  }

  /**
   * Returns the {@link SelectorProvider} associated with this address family, or {@code null} if no
   * such instance is registered.
   * 
   * @return The {@link SelectorProvider}.
   * @throws IllegalStateException on error.
   */
  public synchronized SelectorProvider getSelectorProvider() {
    if (selectorProvider != null) {
      return selectorProvider;
    }
    if (selectorProviderClassname == null) {
      return null;
    }
    try {
      selectorProvider = (SelectorProvider) Class.forName(selectorProviderClassname).getMethod(
          "provider", new Class[0]).invoke(null);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException
        | ClassNotFoundException | RuntimeException e) {
      throw new IllegalStateException("Cannot instantiate selector provider for "
          + addressClassname, e);
    }
    return selectorProvider;
  }
}
