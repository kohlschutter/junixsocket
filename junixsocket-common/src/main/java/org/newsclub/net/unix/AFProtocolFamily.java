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

import java.io.IOException;
import java.net.ProtocolFamily;

/**
 * A junixsocket-based protocol family.
 *
 * @author Christian Kohlschütter
 */
public interface AFProtocolFamily extends ProtocolFamily {

  /**
   * Creates a new datagram channel compatible with this protocol family.
   *
   * @return A new datagram channel.
   * @throws IOException on error.
   */
  AFDatagramChannel<?> openDatagramChannel() throws IOException;

  /**
   * Creates a new server socket channel compatible with this protocol family.
   *
   * @return A new server socket channel.
   * @throws IOException on error.
   */
  AFServerSocketChannel<?> openServerSocketChannel() throws IOException;

  /**
   * Creates a new socket channel compatible with this protocol family.
   *
   * @return A new socket channel.
   * @throws IOException on error.
   */
  AFSocketChannel<?> openSocketChannel() throws IOException;
}
