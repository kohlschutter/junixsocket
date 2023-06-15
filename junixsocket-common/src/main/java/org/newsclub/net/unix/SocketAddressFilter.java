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
import java.net.SocketAddress;

/**
 * A filter that takes a {@link SocketAddress}, and potentially changes it, or throws an exception
 * if certain criteria are met.
 *
 * @author Christian Kohlschütter
 */
@FunctionalInterface
public interface SocketAddressFilter {

  /**
   * Applies the filter on the given address.
   *
   * @param address The address.
   * @return The address itself or a changed address.
   * @throws IOException on error or if a certain error condition is desired.
   */
  SocketAddress apply(SocketAddress address) throws IOException;
}
