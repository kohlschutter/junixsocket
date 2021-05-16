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

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

abstract class SocketStateBase extends CleanableState {
  private static final int SHUT_RD_WR = 2;

  private final AtomicBoolean closed = new AtomicBoolean(false);
  protected final FileDescriptor fd = new FileDescriptor();

  /**
   * We keep track of the server's inode to detect when another server connects to our address.
   */
  protected final AtomicLong inode = new AtomicLong(-1);

  protected AFUNIXSocketAddress socketAddress;

  private final Closeable additionalCloseable;

  protected SocketStateBase(Object observed, Closeable additionalCloseable) {
    super(observed);
    this.additionalCloseable = additionalCloseable;
  }

  @Override
  protected void doClean() {
    if (fd != null && fd.valid()) {
      try {
        doClose();
      } catch (IOException e) {
        // ignore
      }
    }
    if (additionalCloseable != null) {
      try {
        additionalCloseable.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  private void doClose() throws IOException {
    NativeUnixSocket.shutdown(fd, SHUT_RD_WR);

    unblockAccepts();

    NativeUnixSocket.close(fd);
    closed.set(true);
  }

  protected boolean isClosed() {
    return closed.get();
  }

  protected void unblockAccepts() {
  }

  protected FileDescriptor validFdOrException() throws SocketException {
    FileDescriptor fdesc = validFd();
    if (fdesc == null) {
      throw new SocketException("Not open");
    }
    return fdesc;
  }

  protected synchronized FileDescriptor validFd() {
    if (isClosed()) {
      return null;
    }
    FileDescriptor descriptor = this.fd;
    if (descriptor != null) {
      if (descriptor.valid()) {
        return descriptor;
      }
    }
    return null;
  }
}
