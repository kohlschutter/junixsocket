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

import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Helps converting an internet "hostname" to an {@link AFSocketAddress}.
 *
 * @author Christian Kohlschütter
 * @param <A> The supported address type.
 */
public interface AFSocketAddressFromHostname<A extends AFSocketAddress> {
  /**
   * Translates a "host" string (and port) to an {@link AFSocketAddress}.
   *
   * @param host The hostname
   * @param port The port, or 0.
   * @return The {@link AFSocketAddress}
   * @throws SocketException If there was a problem converting the hostname
   * @throws NullPointerException If host was {@code null}.
   */
  SocketAddress addressFromHost(String host, int port) throws SocketException;

  /**
   * Checks whether the given hostname is supported by this socket factory. If not, calls to
   * createSocket will cause a {@link SocketException}.
   *
   * @param host The host to check.
   * @return {@code true} if supported.
   */
  default boolean isHostnameSupported(String host) {
    return host != null;
  }
}
