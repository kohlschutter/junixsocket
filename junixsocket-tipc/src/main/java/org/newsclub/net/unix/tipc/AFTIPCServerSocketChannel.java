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
package org.newsclub.net.unix.tipc;

import java.io.IOException;

import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFTIPCSocketAddress;

/**
 * A selectable channel for stream-oriented listening sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFTIPCServerSocketChannel extends AFServerSocketChannel<AFTIPCSocketAddress> {
  AFTIPCServerSocketChannel(AFTIPCServerSocket socket) {
    super(socket, AFTIPCSelectorProvider.getInstance());
  }

  /**
   * Opens a server-socket channel.
   *
   * @return The new channel
   * @throws IOException on error.
   */
  public static AFTIPCServerSocketChannel open() throws IOException {
    return AFTIPCServerSocket.newInstance().getChannel();
  }
}
