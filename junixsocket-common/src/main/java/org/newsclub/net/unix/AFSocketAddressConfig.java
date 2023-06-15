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

import java.net.SocketException;
import java.net.URI;
import java.util.Set;

import org.newsclub.net.unix.AFSocketAddress.AFSocketAddressConstructor;

/**
 * The implementation-specifics for a given {@link AFSocketAddress} subclass implementation.
 *
 * @param <A> The supported address type.
 * @author Christian Kohlschütter
 * @see AFAddressFamilyConfig
 */
public abstract class AFSocketAddressConfig<A extends AFSocketAddress> {

  /**
   * Constructor.
   */
  protected AFSocketAddressConfig() {
  }

  /**
   * Tries to parse the given address-specific URI.
   *
   * @param u The URI.
   * @param port The port to use, or {@code -1} for "unspecified".
   * @return The address.
   * @throws SocketException on error.
   */
  protected abstract A parseURI(URI u, int port) throws SocketException;

  /**
   * Returns the implementation's address constructor.
   *
   * @return The implementation's address constructor.
   */
  protected abstract AFSocketAddressConstructor<A> addressConstructor();

  /**
   * Returns the name of the implementation's selector provider class.
   *
   * @return The name of the implementation's selector provider class.
   */
  protected abstract String selectorProviderClassname();

  /**
   * Returns the set of supported URI schemes that can be parsed via {@link #parseURI(URI,int)}.
   *
   * These schemes must be unique to this {@link AFSocketAddress} type.
   *
   * @return The set of supported URI schemes.
   */
  protected abstract Set<String> uriSchemes();
}
