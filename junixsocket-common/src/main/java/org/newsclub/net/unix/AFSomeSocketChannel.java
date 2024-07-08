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
import java.nio.channels.DatagramChannel;
import java.nio.channels.InterruptibleChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Marker interface that combines junixsocket-based {@link SocketChannel}s, {@link DatagramChannel}s
 * or {@link ServerSocketChannel}s.
 *
 * @author Christian Kohlschütter
 * @see AFSocketChannel
 * @see AFServerSocketChannel
 * @see AFDatagramChannel
 */
public interface AFSomeSocketChannel extends InterruptibleChannel, FileDescriptorAccess,
    AFSomeSocketThing {
  /**
   * Checks if the channel is configured blocking. The result may be cached, and therefore not
   * invoke native code to check if the underlying socket is actually configured that way.
   *
   * @return {@code true} if blocking.
   */
  boolean isBlocking();

  /**
   * Adjusts this channel's blocking mode.
   *
   * <p>
   * If the given blocking mode is different from the currently cached blocking mode then this
   * method native code to change it.
   * </p>
   *
   * @param block {@code true} if blocking is desired.
   * @return This channel.
   * @throws IOException on error.
   */
  SelectableChannel configureBlocking(boolean block) throws IOException;
}
