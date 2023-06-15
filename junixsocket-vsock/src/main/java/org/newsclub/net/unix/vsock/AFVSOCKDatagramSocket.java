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
package org.newsclub.net.unix.vsock;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;

import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSocketType;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

/**
 * A {@link DatagramSocket} implementation that works with {@code AF_VSOCK} sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFVSOCKDatagramSocket extends AFDatagramSocket<AFVSOCKSocketAddress> implements
    AFVSOCKSocketExtensions {
  /**
   * The maximum size for user messages.
   */
  public static final int VSOCK_MAX_USER_MSG_SIZE = 66000;

  AFVSOCKDatagramSocket(FileDescriptor fd) throws IOException {
    this(fd, AFSocketType.SOCK_DGRAM);
  }

  AFVSOCKDatagramSocket(FileDescriptor fd, AFSocketType socketType) throws IOException {
    super(new AFVSOCKDatagramSocketImpl(fd, socketType));
  }

  @Override
  protected AFVSOCKDatagramChannel newChannel() {
    return new AFVSOCKDatagramChannel(this);
  }

  /**
   * Returns a new {@link AFVSOCKDatagramSocket} instance, using the default
   * {@link AFSocketType#SOCK_DGRAM} socket type.
   *
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFVSOCKDatagramSocket newInstance() throws IOException {
    return (AFVSOCKDatagramSocket) newInstance(AFVSOCKDatagramSocket::new);
  }

  /**
   * Returns a new {@link AFVSOCKDatagramSocket} instance for the given socket type.
   *
   * @param socketType The socket type.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFVSOCKDatagramSocket newInstance(AFSocketType socketType) throws IOException {
    return (AFVSOCKDatagramSocket) newInstance((fd) -> {
      return new AFVSOCKDatagramSocket(fd, socketType);
    });
  }

  @Override
  public AFVSOCKDatagramChannel getChannel() {
    return (AFVSOCKDatagramChannel) super.getChannel();
  }
}
