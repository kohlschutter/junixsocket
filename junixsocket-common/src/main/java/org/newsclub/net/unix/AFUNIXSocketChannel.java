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
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * A selectable channel for stream-oriented connecting sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFUNIXSocketChannel extends AFSocketChannel<AFUNIXSocketAddress> implements
    AFUNIXSocketExtensions {
  AFUNIXSocketChannel(AFUNIXSocket socket) {
    super(socket, AFUNIXSelectorProvider.getInstance());
  }

  /**
   * Opens a socket channel.
   *
   * @return The new channel
   * @throws IOException on error.
   */
  public static AFUNIXSocketChannel open() throws IOException {
    return (AFUNIXSocketChannel) AFSocketChannel.open(AFUNIXSocket::newLenientInstance);
  }

  /**
   * Opens a socket channel, connecting to the given socket address.
   *
   * @param remote The socket address to connect to.
   * @return The new channel
   * @throws IOException on error.
   */
  public static AFUNIXSocketChannel open(SocketAddress remote) throws IOException {
    return (AFUNIXSocketChannel) AFSocketChannel.open(AFUNIXSocket::newLenientInstance, remote);
  }

  @Override
  public FileDescriptor[] getReceivedFileDescriptors() throws IOException {
    return ((AFUNIXSocketExtensions) getAFSocket()).getReceivedFileDescriptors();
  }

  @Override
  public void clearReceivedFileDescriptors() {
    ((AFUNIXSocketExtensions) getAFSocket()).clearReceivedFileDescriptors();
  }

  @Override
  public void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    if (fdescs != null && fdescs.length > 0 && !isConnected()) {
      throw new SocketException("Not connected");
    }
    ((AFUNIXSocketExtensions) getAFSocket()).setOutboundFileDescriptors(fdescs);
  }

  @Override
  public boolean hasOutboundFileDescriptors() {
    return ((AFUNIXSocketExtensions) getAFSocket()).hasOutboundFileDescriptors();
  }

  @Override
  public AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return ((AFUNIXSocketExtensions) getAFSocket()).getPeerCredentials();
  }
}
