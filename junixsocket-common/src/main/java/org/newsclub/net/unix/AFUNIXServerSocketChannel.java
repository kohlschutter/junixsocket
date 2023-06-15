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
 * A selectable channel for stream-oriented listening sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFUNIXServerSocketChannel extends AFServerSocketChannel<AFUNIXSocketAddress> {
  AFUNIXServerSocketChannel(AFUNIXServerSocket socket) {
    super(socket, AFUNIXSelectorProvider.getInstance());
  }

  /**
   * Opens a server-socket channel.
   *
   * @return The new channel
   * @throws IOException on error.
   */
  public static AFUNIXServerSocketChannel open() throws IOException {
    return AFUNIXServerSocket.newInstance().getChannel();
  }

  @Override
  public AFUNIXSocketChannel accept() throws IOException {
    return (AFUNIXSocketChannel) super.accept();
  }
}
