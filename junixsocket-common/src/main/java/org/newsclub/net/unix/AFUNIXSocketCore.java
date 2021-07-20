/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A shared core that is common for all AFUNIX sockets (datagrams, streams).
 * 
 * @author Christian Kohlschütter
 */
class AFUNIXSocketCore extends AFUNIXCore {
  private static final int SHUT_RD_WR = 2;

  /**
   * We keep track of the server's inode to detect when another server connects to our address.
   */
  protected final AtomicLong inode = new AtomicLong(-1);

  protected AFUNIXSocketAddress socketAddress;

  protected AFUNIXSocketCore(Object observed, FileDescriptor fd,
      AncillaryDataSupport ancillaryDataSupport) {
    super(observed, fd, ancillaryDataSupport);
  }

  @Override
  protected void doClose() throws IOException {
    NativeUnixSocket.shutdown(fd, SHUT_RD_WR);
    unblockAccepts();

    super.doClose();
  }

  protected void unblockAccepts() {
  }

  AFUNIXSocketAddress receive(ByteBuffer dst) throws IOException {
    ByteBuffer socketAddressBuffer = AFUNIXSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
    int read = read(dst, socketAddressBuffer, 0);
    if (read > 0) {
      return AFUNIXSocketAddress.ofInternal(socketAddressBuffer);
    } else {
      return null;
    }
  }

  boolean isConnected(boolean boundOk) {
    try {
      if (fd.valid()) {
        switch (NativeUnixSocket.socketStatus(fd)) {
          case NativeUnixSocket.SOCKETSTATUS_CONNECTED:
            return true;
          case NativeUnixSocket.SOCKETSTATUS_BOUND:
            if (boundOk) {
              return true;
            }
            break;
          default:
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return false;
  }
}
