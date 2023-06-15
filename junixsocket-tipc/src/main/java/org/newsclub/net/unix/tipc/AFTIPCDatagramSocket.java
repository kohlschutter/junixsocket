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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;

import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSocketType;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketImplExtensions;

/**
 * A {@link DatagramSocket} implementation that works with {@code AF_TIPC} sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFTIPCDatagramSocket extends AFDatagramSocket<AFTIPCSocketAddress> implements
    AFTIPCSocketExtensions {
  /**
   * The maximum size for user messages.
   */
  public static final int TIPC_MAX_USER_MSG_SIZE = 66000;

  AFTIPCDatagramSocket(FileDescriptor fd) throws IOException {
    this(fd, AFSocketType.SOCK_DGRAM);
  }

  AFTIPCDatagramSocket(FileDescriptor fd, AFSocketType socketType) throws IOException {
    super(new AFTIPCDatagramSocketImpl(fd, socketType));
  }

  @Override
  protected AFTIPCDatagramChannel newChannel() {
    return new AFTIPCDatagramChannel(this);
  }

  /**
   * Returns a new {@link AFTIPCDatagramSocket} instance, using the default
   * {@link AFSocketType#SOCK_DGRAM} socket type.
   *
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFTIPCDatagramSocket newInstance() throws IOException {
    return (AFTIPCDatagramSocket) newInstance(AFTIPCDatagramSocket::new);
  }

  /**
   * Returns a new {@link AFTIPCDatagramSocket} instance for the given socket type.
   *
   * @param socketType The socket type.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFTIPCDatagramSocket newInstance(AFSocketType socketType) throws IOException {
    return (AFTIPCDatagramSocket) newInstance((fd) -> {
      return new AFTIPCDatagramSocket(fd, socketType);
    });
  }

  @Override
  public AFTIPCDatagramChannel getChannel() {
    return (AFTIPCDatagramChannel) super.getChannel();
  }

  @Override
  public AFTIPCErrInfo getErrInfo() {
    return AFTIPCErrInfo.of(((AFTIPCSocketImplExtensions) getImplExtensions()).getTIPCErrInfo());
  }

  @Override
  public AFTIPCDestName getDestName() {
    return AFTIPCDestName.of(((AFTIPCSocketImplExtensions) getImplExtensions()).getTIPCDestName());
  }
}
