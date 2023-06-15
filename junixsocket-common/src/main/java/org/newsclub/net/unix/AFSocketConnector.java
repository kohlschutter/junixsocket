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
 * Some connector that is able to connect to a given {@link AFSocketAddress}.
 *
 * @param <A> The address type to connect to.
 * @param <T> The address type for the returned socket (which should either be identical to
 *          {@code A} or {@link AFSocketAddress} to indicate that this could be any socket).
 * @author Christian Kohlschütter
 * @see AFServerSocketConnector
 */
public interface AFSocketConnector<A extends AFSocketAddress, T extends AFSocketAddress> {
  /**
   * Connect to the socket at the given address.
   *
   * @param addr The address to connect to.
   * @return The connected socket.
   * @throws IOException on error.
   */
  AFSocket<? extends T> connect(A addr) throws IOException;
}
