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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * A {@link DatagramSocket} implementation that works with AF_UNIX Unix domain sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFUNIXDatagramSocket extends AFDatagramSocket<AFUNIXSocketAddress> implements
    AFUNIXSocketExtensions {

  AFUNIXDatagramSocket(final FileDescriptor fd) throws IOException {
    super(new AFUNIXDatagramSocketImpl(fd));
  }

  private AFUNIXDatagramSocket(final FileDescriptor fd, AFSocketType socketType)
      throws IOException {
    super(new AFUNIXDatagramSocketImpl(fd, socketType));
  }

  @Override
  protected AFUNIXDatagramChannel newChannel() {
    return new AFUNIXDatagramChannel(this);
  }

  /**
   * Returns a new {@link AFUNIXDatagramSocket} instance.
   *
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFUNIXDatagramSocket newInstance() throws IOException {
    return (AFUNIXDatagramSocket) newInstance(AFUNIXDatagramSocket::new);
  }

  /**
   * Returns a new {@link AFUNIXDatagramSocket} instance for the given socket type.
   *
   * @param socketType The socket type.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFUNIXDatagramSocket newInstance(AFSocketType socketType) throws IOException {
    return (AFUNIXDatagramSocket) newInstance((fd) -> {
      return new AFUNIXDatagramSocket(fd, socketType);
    });
  }

  static AFUNIXDatagramSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    return (AFUNIXDatagramSocket) newInstance(AFUNIXDatagramSocket::new, fdObj, localPort,
        remotePort);
  }

  @Override
  public AFUNIXDatagramChannel getChannel() {
    return (AFUNIXDatagramChannel) super.getChannel();
  }

  @Override
  public FileDescriptor[] getReceivedFileDescriptors() throws IOException {
    return getAncillaryDataSupport().getReceivedFileDescriptors();
  }

  @Override
  public void clearReceivedFileDescriptors() {
    getAncillaryDataSupport().clearReceivedFileDescriptors();
  }

  @Override
  public void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    if (fdescs != null && fdescs.length > 0 && !isConnected()) {
      throw new SocketException("Not connected");
    }
    getAncillaryDataSupport().setOutboundFileDescriptors(fdescs);
  }

  @Override
  public boolean hasOutboundFileDescriptors() {
    return getAncillaryDataSupport().hasOutboundFileDescriptors();
  }

  @Override
  public AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    if (isClosed() || !isConnected()) {
      throw new SocketException("Not connected");
    }
    return ((AFUNIXDatagramSocketImpl) getAFImpl()).getPeerCredentials();
  }
}
