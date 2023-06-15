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
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;

/**
 * A {@link DatagramChannel} implementation that works with AF_UNIX Unix domain sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFUNIXDatagramChannel extends AFDatagramChannel<AFUNIXSocketAddress> implements
    AFUNIXSocketExtensions {
  AFUNIXDatagramChannel(AFUNIXDatagramSocket socket) {
    super(AFUNIXSelectorProvider.getInstance(), socket);
  }

  /**
   * Opens a datagram channel.
   *
   * @return The new channel
   * @throws IOException if an I/O error occurs
   */
  public static AFUNIXDatagramChannel open() throws IOException {
    return AFUNIXSelectorProvider.provider().openDatagramChannel();
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
  public static AFUNIXDatagramChannel open(ProtocolFamily family) throws IOException {
    return AFUNIXSelectorProvider.provider().openDatagramChannel(family);
  }

  // CPD-OFF

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
