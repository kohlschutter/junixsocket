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

import java.io.IOException;

/**
 * Some connector that is able to create {@link AFServerSocket}s bound to a given
 * {@link AFSocketAddress}.
 *
 * @param <A> The address type to bind to.
 * @param <T> The address type for the returned server socket (which should either be identical to
 *          {@code A} or {@link AFSocketAddress} to indicate that this could be any socket).
 * @author Christian Kohlschütter
 * @see AFSocketConnector
 */
public interface AFServerSocketConnector<A extends AFSocketAddress, T extends AFSocketAddress> {
  /**
   * Creates an {@link AFServerSocket} bound to the given address.
   *
   * @param addr The address to bind to.
   * @return The bound socket.
   * @throws IOException on error.
   */
  AFServerSocket<? extends T> bind(A addr) throws IOException;
}
