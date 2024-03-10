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

import java.io.Closeable;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Marker interface that combines junixsocket-based {@link SocketChannel}s, {@link Socket}s,
 * {@link DatagramChannel}s and {@link DatagramSocket}s, as well as {@link ServerSocket}s and
 * {@link ServerSocketChannel}s.
 *
 * @author Christian Kohlschütter
 * @see AFSocketPair
 * @see AFSocket
 * @see AFSocketChannel
 * @see AFDatagramSocket
 * @see AFDatagramChannel
 * @see AFServerSocket
 * @see AFServerSocketChannel
 */
public interface AFSomeSocketThing extends Closeable, FileDescriptorAccess {
  /**
   * Returns the socket's local socket address, or {@code null} if unavailable or if there was a
   * problem retrieving it.
   *
   * @return The local socket address, or {@code null}.
   */
  @Nullable
  SocketAddress getLocalSocketAddress();

  /**
   * Configures whether the socket should be shutdown upon {@link #close()}, which is the default.
   *
   * @param enabled {@code true} if enabled.
   */
  void setShutdownOnClose(boolean enabled);
}
