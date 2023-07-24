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
package org.newsclub.net.unix.darwin.system;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;

import org.newsclub.net.unix.AFDatagramChannel;
import org.newsclub.net.unix.AFSYSTEMSocketAddress;

/**
 * A {@link DatagramChannel} implementation that works with {@code AF_SYSTEM} sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFSYSTEMDatagramChannel extends AFDatagramChannel<AFSYSTEMSocketAddress>
    implements AFSYSTEMSocketExtensions {
  AFSYSTEMDatagramChannel(AFSYSTEMDatagramSocket socket) {
    super(AFSYSTEMSelectorProvider.getInstance(), socket);
  }

  /**
   * Opens a datagram channel.
   *
   * @return The new channel
   * @throws IOException if an I/O error occurs
   */
  public static AFSYSTEMDatagramChannel open() throws IOException {
    return AFSYSTEMSelectorProvider.provider().openDatagramChannel();
  }

  /**
   * Opens a datagram channel.
   *
   * @param family The protocol family
   * @return A new datagram channel
   *
   * @throws UnsupportedOperationException if the specified protocol family is not supported
   * @throws IOException if an I/O error occurs
   */
  public static AFSYSTEMDatagramChannel open(ProtocolFamily family) throws IOException {
    return AFSYSTEMSelectorProvider.provider().openDatagramChannel(family);
  }
}
