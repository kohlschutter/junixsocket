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
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The core functionality of file descriptor based I/O.
 *
 * @author Christian Kohlschütter
 */
class AFCore extends CleanableState {
  private static final ThreadLocal<ByteBuffer> TL_BUFFER = new ThreadLocal<>();

  private static final String PROP_TL_BUFFER_MAX_CAPACITY =
      "org.newsclub.net.unix.thread-local-buffer.max-capacity"; // 0 means "no limit" (discouraged)

  private static final int TL_BUFFER_MIN_CAPACITY = 8192; // 8 kb per thread
  private static final int TL_BUFFER_MAX_CAPACITY = Integer.parseInt(System.getProperty(
      PROP_TL_BUFFER_MAX_CAPACITY, Integer.toString(1 * 1024 * 1024))); // 1 MB per thread

  private final AtomicBoolean closed = new AtomicBoolean(false);

  final FileDescriptor fd;
  final AncillaryDataSupport ancillaryDataSupport;

  private final boolean datagramMode;

  private boolean blocking = true;

  AFCore(Object observed, FileDescriptor fd, AncillaryDataSupport ancillaryDataSupport,
      boolean datagramMode) {
    super(observed);
    this.datagramMode = datagramMode;
    this.fd = (fd == null) ? new FileDescriptor() : fd;
    this.ancillaryDataSupport = ancillaryDataSupport;
  }

  AFCore(Object observed, FileDescriptor fd) {
    this(observed, fd, null, false);
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

  boolean isClosed() {
    return closed.get();
  }

  void doClose() throws IOException {
    if (closed.compareAndSet(false, true)) {
      NativeUnixSocket.close(fd);
    }
  }

  FileDescriptor validFdOrException() throws SocketException {
    FileDescriptor fdesc = validFd();
    if (fdesc == null) {
      closed.set(true);
      throw new SocketClosedException("Not open");
    }
    return fdesc;
  }

  synchronized FileDescriptor validFd() {
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

    int dstPos = dst.position();

    ByteBuffer buf;
    int pos;
    if (dst.isDirect()) {
      buf = dst;
      pos = dstPos;
    } else {
      buf = getThreadLocalDirectByteBuffer(remaining);
      remaining = Math.min(remaining, buf.remaining());
      pos = buf.position();
    }

    if (!blocking) {
      options |= NativeUnixSocket.OPT_NON_BLOCKING;
    }

    int count = NativeUnixSocket.receive(fdesc, buf, pos, remaining, socketAddressBuffer, options,
        ancillaryDataSupport, 0);
    if (count == -1) {
      return count;
    }
    if (buf != dst) { // NOPMD
      int oldLimit = buf.limit();
      if (count < oldLimit) {
        buf.limit(count);
      }
      try {
        while (buf.hasRemaining()) {
          dst.put(buf);
        }
      } finally {
        if (count < oldLimit) {
          buf.limit(oldLimit);
        }
      }
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
    final ByteBuffer addressTo;
    final int addressToLen;
    if (target == null) {
      addressTo = null;
      addressToLen = 0;
    } else {
      addressTo = AFSocketAddress.SOCKETADDRESS_BUFFER_TL.get();
      addressToLen = AFSocketAddress.unwrapAddressDirectBufferInternal(addressTo, target);
    }

    // accept "send buffer overflow" as packet loss
    // and don't retry (which may slow things down quite a bit)
    if (!blocking) {
      options |= NativeUnixSocket.OPT_NON_BLOCKING;
    }

    int pos = src.position();
    boolean isDirect = src.isDirect();
    ByteBuffer buf;
    int bufPos;
    if (isDirect) {
      buf = src;
      bufPos = pos;
    } else {
      buf = getThreadLocalDirectByteBuffer(remaining);
      remaining = Math.min(remaining, buf.remaining());

      bufPos = buf.position();

      while (src.hasRemaining() && buf.hasRemaining()) {
        buf.put(src);
      }

      buf.position(bufPos);
    }
    if (datagramMode) {
      options |= NativeUnixSocket.OPT_DGRAM_MODE;
    }

    int written = NativeUnixSocket.send(fdesc, buf, bufPos, remaining, addressTo, addressToLen,
        options, ancillaryDataSupport);
    src.position(pos + written);

    return written;
  }

  /**
   * Returns a per-thread reusable byte buffer for a given capacity.
   *
   * If a thread-local buffer currently uses a smaller capacity, the buffer is replaced by a larger
   * one. If the capacity exceeds a configurable maximum, a new direct buffer is allocated but not
   * cached (i.e., the previously cached one is kept but not immediately returned to the caller).
   *
   * @param capacity The desired capacity.
   * @return A byte buffer satisfying the requested capacity.
   */
  ByteBuffer getThreadLocalDirectByteBuffer(int capacity) {
    if (capacity > TL_BUFFER_MAX_CAPACITY && TL_BUFFER_MAX_CAPACITY > 0) {
      // Capacity exceeds configurable maximum limit;
      // allocate but do not cache direct buffer.
      // This may incur a performance penalty at the cost of correctness when using such capacities.
      return ByteBuffer.allocateDirect(capacity);
    }
    if (capacity < TL_BUFFER_MIN_CAPACITY) {
      capacity = TL_BUFFER_MIN_CAPACITY;
    }
    ByteBuffer buffer = TL_BUFFER.get();
    if (buffer == null || capacity > buffer.capacity()) {
      buffer = ByteBuffer.allocateDirect(capacity);
      TL_BUFFER.set(buffer);
    }
    buffer.clear();
    return buffer;
  }

  void implConfigureBlocking(boolean block) throws IOException {
    NativeUnixSocket.configureBlocking(validFdOrException(), block);
    this.blocking = block;
  }

  boolean isBlocking() {
    return blocking;
  }
}
