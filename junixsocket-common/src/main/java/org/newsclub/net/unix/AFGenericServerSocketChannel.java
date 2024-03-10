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

/**
 * A selectable channel for stream-oriented listening sockets.
 *
 * @author Christian Kohlschütter
 */
final class AFGenericServerSocketChannel extends AFServerSocketChannel<AFGenericSocketAddress> {
  AFGenericServerSocketChannel(AFGenericServerSocket socket) {
    super(socket, AFGenericSelectorProvider.getInstance());
  }

  /**
   * Opens a server-socket channel.
   *
   * @return The new channel
   * @throws IOException on error.
   */
  public static AFGenericServerSocketChannel open() throws IOException {
    return AFGenericServerSocket.newInstance().getChannel();
  }

  @Override
  public AFGenericSocketChannel accept() throws IOException {
    return (AFGenericSocketChannel) super.accept();
  }
}
