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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;

/**
 * A {@link DatagramSocket} implementation that works with {@code AF_SYSTEM} sockets.
 *
 * @author Christian Kohlschütter
 */
final class AFGenericDatagramSocket extends AFDatagramSocket<AFGenericSocketAddress> implements
    AFGenericSocketExtensions {

  AFGenericDatagramSocket(FileDescriptor fd) throws IOException {
    this(fd, AFSocketType.SOCK_DGRAM);
  }

  AFGenericDatagramSocket(FileDescriptor fd, AFSocketType socketType) throws IOException {
    super(new AFGenericDatagramSocketImpl(fd, socketType));
  }

  @Override
  protected AFGenericDatagramChannel newChannel() {
    return new AFGenericDatagramChannel(this);
  }

  /**
   * Returns a new {@link AFGenericDatagramSocket} instance, using the default
   * {@link AFSocketType#SOCK_DGRAM} socket type.
   *
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFGenericDatagramSocket newInstance() throws IOException {
    return (AFGenericDatagramSocket) newInstance(AFGenericDatagramSocket::new);
  }

  /**
   * Returns a new {@link AFGenericDatagramSocket} instance for the given socket type.
   *
   * @param socketType The socket type.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFGenericDatagramSocket newInstance(AFSocketType socketType) throws IOException {
    return (AFGenericDatagramSocket) newInstance((fd) -> {
      return new AFGenericDatagramSocket(fd, socketType);
    });
  }

  @Override
  public AFGenericDatagramChannel getChannel() {
    return (AFGenericDatagramChannel) super.getChannel();
  }

  @Override
  protected AFGenericSocketImplExtensions getImplExtensions() {
    return (AFGenericSocketImplExtensions) super.getImplExtensions();
  }

  @Override
  protected AFDatagramSocket<AFGenericSocketAddress> newDatagramSocketInstance()
      throws IOException {
    return new AFGenericDatagramSocket(null);
  }
}
