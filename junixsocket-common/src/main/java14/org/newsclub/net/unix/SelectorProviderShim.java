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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Objects;

abstract class SelectorProviderShim extends SelectorProvider {
  public SocketChannel openSocketChannel(ProtocolFamily family) throws IOException {
    Objects.requireNonNull(family);
    throw new UnsupportedOperationException("Protocol family not supported");
  }

  public ServerSocketChannel openServerSocketChannel(ProtocolFamily family) throws IOException {
    Objects.requireNonNull(family);
    throw new UnsupportedOperationException("Protocol family not supported");
  }
}
