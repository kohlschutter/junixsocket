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
import java.net.Socket;
import java.net.SocketException;

class AFUNIXSocketImpl extends AFSocketImpl<AFUNIXSocketAddress> {
  protected AFUNIXSocketImpl(FileDescriptor fdObj) {
    super(AFUNIXSocketAddress.AF_UNIX, fdObj);
  }

  /**
   * Changes the behavior to be somewhat lenient with respect to the specification.
   *
   * In particular, we ignore calls to {@link Socket#getTcpNoDelay()} and
   * {@link Socket#setTcpNoDelay(boolean)}.
   */
  static final class Lenient extends AFUNIXSocketImpl {
    protected Lenient(FileDescriptor fdObj) throws SocketException {
      super(fdObj);
    }

    @Override
    public void setOption(int optID, Object value) throws SocketException {
      super.setOptionLenient(optID, value);
    }

    @Override
    public Object getOption(int optID) throws SocketException {
      return super.getOptionLenient(optID);
    }
  }

  AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return NativeUnixSocket.peerCredentials(fd, new AFUNIXSocketCredentials());
  }

  final FileDescriptor[] getReceivedFileDescriptors() {
    return ancillaryDataSupport.getReceivedFileDescriptors();
  }

  final void clearReceivedFileDescriptors() {
    ancillaryDataSupport.clearReceivedFileDescriptors();
  }

  final void receiveFileDescriptors(int[] fds) throws IOException {
    ancillaryDataSupport.receiveFileDescriptors(fds);
  }

  final void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    ancillaryDataSupport.setOutboundFileDescriptors(fdescs);
  }

  final boolean hasOutboundFileDescriptors() {
    return ancillaryDataSupport.hasOutboundFileDescriptors();
  }
}
