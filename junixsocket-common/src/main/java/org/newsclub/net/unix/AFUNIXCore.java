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
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The core functionality of file descriptor based I/O.
 * 
 * @author Christian Kohlschütter
 */
class AFUNIXCore extends CleanableState {
  @SuppressWarnings("PMD.UseDiamondOperator") // not in Java 7
  private static final ThreadLocal<ByteBuffer> DATAGRAMPACKET_BUFFER_TL =
      new ThreadLocal<ByteBuffer>();

  private static final int DATAGRAMPACKET_BUFFER_MIN_CAPACITY = 8192;
  private static final int DATAGRAMPACKET_BUFFER_MAX_CAPACITY = 1 * 1024 * 1024;

  private final AtomicBoolean closed = new AtomicBoolean(false);

  protected final FileDescriptor fd;
  protected final AncillaryDataSupport ancillaryDataSupport;

  protected AFUNIXCore(Object observed, FileDescriptor fd,
      AncillaryDataSupport ancillaryDataSupport) {
    super(observed);
    this.fd = (fd == null) ? new FileDescriptor() : fd;
    this.ancillaryDataSupport = ancillaryDataSupport;
  }

  protected AFUNIXCore(Object observed, FileDescriptor fd) {
    this(observed, fd, null);
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
    if (ancillaryDataSupport != null) {
      ancillaryDataSupport.close();
    }
  }

  protected boolean isClosed() {
    return closed.get();
  }

  protected void doClose() throws IOException {
    NativeUnixSocket.close(fd);
    closed.set(true);
  }

  protected FileDescriptor validFdOrException() throws SocketException {
    FileDescriptor fdesc = validFd();
    if (fdesc == null) {
      closed.set(true);
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

  int read(ByteBuffer dst) throws IOException {
    return read(dst, null, 0);
  }

  int read(ByteBuffer dst, ByteBuffer socketAddressBuffer, int options) throws IOException {
    int remaining = dst.remaining();
    if (remaining == 0) {
      return 0;
    }
    FileDescriptor fdesc = validFdOrException();

    ByteBuffer buf;
    if (dst.isDirect()) {
      buf = dst;
    } else {
      buf = getThreadLocalDirectByteBuffer(remaining);
      remaining = Math.min(remaining, buf.remaining());
    }

    int pos = dst.position();

    int count = NativeUnixSocket.receive(fdesc, buf, pos, remaining, socketAddressBuffer, options,
        ancillaryDataSupport, 0);
    if (buf != dst) { // NOPMD
      buf.limit(count);
      dst.put(buf);
    } else {
      if (count < 0) {
        throw new IllegalStateException();
      }
      dst.position(pos + count);
    }
    return count;
  }

  int write(ByteBuffer src) throws IOException {
    return write(src, null, 0);
  }

  int write(ByteBuffer src, SocketAddress target, int options) throws IOException {
    int remaining = src.remaining();
    if (remaining == 0) {
      return 0;
    }

    FileDescriptor fdesc = validFdOrException();
    ByteBuffer addressTo;
    if (target == null) {
      addressTo = null;
    } else {
      addressTo = AFUNIXSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
      AFUNIXSocketAddress.unwrapAddressDirectBufferInternal(addressTo, target);
    }

    // accept "send buffer overflow" as packet loss
    // and don't retry (which would slow things down quite a bit)
    options |= NativeUnixSocket.OPT_NON_BLOCKING;

    ByteBuffer buf;
    if (src.isDirect()) {
      buf = src;
    } else {
      buf = getThreadLocalDirectByteBuffer(remaining);
      remaining = Math.min(remaining, buf.remaining());
      buf.put(src);
      buf.position(0);
    }
    int pos = buf.position();

    int written = NativeUnixSocket.send(fdesc, buf, pos, remaining, addressTo, options,
        ancillaryDataSupport);
    if (written > 0) {
      src.position(pos + written);
    }

    return written;
  }

  ByteBuffer getThreadLocalDirectByteBuffer(int capacity) {
    if (capacity > DATAGRAMPACKET_BUFFER_MAX_CAPACITY) {
      capacity = DATAGRAMPACKET_BUFFER_MAX_CAPACITY;
    } else if (capacity < DATAGRAMPACKET_BUFFER_MIN_CAPACITY) {
      capacity = DATAGRAMPACKET_BUFFER_MIN_CAPACITY;
    }
    ByteBuffer datagramPacketBuffer = DATAGRAMPACKET_BUFFER_TL.get();
    if (datagramPacketBuffer == null || capacity > datagramPacketBuffer.capacity()) {
      datagramPacketBuffer = ByteBuffer.allocateDirect(capacity);
      DATAGRAMPACKET_BUFFER_TL.set(datagramPacketBuffer);
    }
    datagramPacketBuffer.clear();
    return datagramPacketBuffer;
  }

  void implConfigureBlocking(boolean block) throws IOException {
    NativeUnixSocket.configureBlocking(validFdOrException(), block);
  }
}
