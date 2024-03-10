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
package org.newsclub.net.unix.tipc;

import java.io.IOException;

import org.newsclub.net.unix.AFSocketPair;
import org.newsclub.net.unix.AFSomeSocket;

/**
 * A pair of sockets.
 *
 * @param <T> The socket type.
 * @author Christian Kohlschütter
 */
public final class AFTIPCSocketPair<T extends AFSomeSocket> extends AFSocketPair<T> {
  /**
   * Creates a new socket pair.
   *
   * @param socket1 The first socket.
   * @param socket2 The second socket.
   */
  AFTIPCSocketPair(T socket1, T socket2) {
    super(socket1, socket2);
  }

  /**
   * Opens a socket pair of interconnected channels.
   *
   * @return The new channel pair.
   * @throws IOException on error.
   */
  public static AFTIPCSocketPair<AFTIPCSocketChannel> open() throws IOException {
    return AFTIPCSelectorProvider.provider().openSocketChannelPair();
  }

  /**
   * Opens a socket pair of interconnected datagram channels.
   *
   * @return The new channel pair.
   * @throws IOException on error.
   */
  public static AFTIPCSocketPair<AFTIPCDatagramChannel> openDatagram() throws IOException {
    return AFTIPCSelectorProvider.provider().openDatagramChannelPair();
  }
}
