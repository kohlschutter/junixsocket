/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNull;
import org.newsclub.net.unix.pool.MutableHolder;
import org.newsclub.net.unix.pool.ObjectPool;
import org.newsclub.net.unix.pool.ObjectPool.Lease;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * The core functionality of file descriptor based I/O.
 *
 * @author Christian Kohlschütter
 */
class AFCore extends CleanableState {
  private static final ObjectPool<MutableHolder<ByteBuffer>> TL_BUFFER = ObjectPool
      .newThreadLocalPool(() -> {
        return new MutableHolder<>(null);
      }, (o) -> {
        ByteBuffer bb = o.get();
        if (bb != null) {
          bb.clear();
        }
        return true;
      });

  private static final String PROP_TL_BUFFER_MAX_CAPACITY =
      "org.newsclub.net.unix.thread-local-buffer.max-capacity"; // 0 means "no limit" (discouraged)

  private static final int TL_BUFFER_MIN_CAPACITY = 8192; // 8 kb per thread
  private static final int TL_BUFFER_MAX_CAPACITY = Integer.parseInt(System.getProperty(
      PROP_TL_BUFFER_MAX_CAPACITY, Integer.toString(1 * 1024 * 1024))); // 1 MB per thread

  private final AtomicBoolean closed = new AtomicBoolean(false);

  final FileDescriptor fd;
  final AncillaryDataSupport ancillaryDataSupport;

  private final boolean datagramMode;

  private final AtomicInteger virtualBlockingLeases = new AtomicInteger(0);
  private volatile boolean blocking = true;
  private final AtomicBoolean cleanFd = new AtomicBoolean(true);

  AFCore(Object observed, FileDescriptor fd, AncillaryDataSupport ancillaryDataSupport,
      boolean datagramMode) {
    super(observed);
    this.datagramMode = datagramMode;
    this.ancillaryDataSupport = ancillaryDataSupport;

    this.fd = fd == null ? new FileDescriptor() : fd;
  }

  AFCore(Object observed, FileDescriptor fd) {
    this(observed, fd, null, false);
  }

  @Override
  protected final void doClean() {
    if (fd != null && fd.valid() && cleanFd.get()) {
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

  void disableCleanFd() {
    this.cleanFd.set(false);
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

  int read(ByteBuffer dst, AFSupplier<Integer> timeout) throws IOException {
    return read(dst, timeout, null, 0);
  }

  @SuppressWarnings({
      "PMD.NcssCount", "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity",
      "PMD.NPathComplexity"})
  int read(ByteBuffer dst, AFSupplier<Integer> timeout, ByteBuffer socketAddressBuffer, int options)
      throws IOException {
    int remaining = dst.remaining();
    if (remaining == 0) {
      return 0;
    }
    FileDescriptor fdesc = validFdOrException();

    int dstPos = dst.position();

    ByteBuffer buf;
    int pos;

    boolean direct = dst.isDirect();

    final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && isBlocking())
        || isVirtualBlocking();
    final long now;
    if (virtualBlocking) {
      now = System.currentTimeMillis();
    } else {
      now = 0;
    }
    if (virtualBlocking || !blocking) {
      options |= NativeUnixSocket.OPT_NON_BLOCKING;
    }

    boolean park = false;

    int count;
    virtualThreadLoop : do {
      if (virtualBlocking) {
        if (park) {
          VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_WRITE, now,
              timeout, this::close);
        }
        configureVirtualBlocking(true);
      }

      try (Lease<MutableHolder<ByteBuffer>> lease = direct ? null : getPrivateDirectByteBuffer(
          remaining)) {
        if (direct) {
          buf = dst;
          pos = dstPos;
        } else {
          buf = Objects.requireNonNull(Objects.requireNonNull(lease).get().get());
          remaining = Math.min(remaining, buf.remaining());
          pos = buf.position();
          buf.limit(pos + remaining);
        }

        try {
          count = NativeUnixSocket.receive(fdesc, buf, pos, remaining, socketAddressBuffer, options,
              ancillaryDataSupport, 0);
          if (count == 0 && virtualBlocking) {
            // try again
            park = true;
            continue virtualThreadLoop;
          }
        } catch (AsynchronousCloseException e) {
          throw e;
        } catch (ClosedChannelException e) {
          if (isClosed()) {
            throw e;
          } else if (Thread.currentThread().isInterrupted()) {
            throw (ClosedByInterruptException) new ClosedByInterruptException().initCause(e);
          } else {
            throw (AsynchronousCloseException) new AsynchronousCloseException().initCause(e);
          }
        } catch (SocketTimeoutException e) {
          if (virtualBlocking) {
            // try again
            park = true;
            continue virtualThreadLoop;
          } else {
            throw e;
          }
        }

        if (count == -1 || buf == null) {
          return -1;
        }

        if (direct) {
          if (count < 0) {
            throw new IllegalStateException();
          }
          dst.position(pos + count);
        } else {
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
        }
      } finally {
        if (virtualBlocking) {
          configureVirtualBlocking(false);
        }
      }
      break; // NOPMD.AvoidBranchingStatementAsLastInLoop virtualThreadLoop
    } while (true); // NOPMD.WhileLoopWithLiteralBoolean

    return count;
  }

  int write(ByteBuffer src, AFSupplier<Integer> timeout) throws IOException {
    return write(src, timeout, null, 0);
  }

  @SuppressWarnings({
      "PMD.NcssCount", "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity",
      "PMD.NPathComplexity"})
  int write(ByteBuffer src, AFSupplier<Integer> timeout, SocketAddress target, int options)
      throws IOException {
    int remaining = src.remaining();

    if (remaining == 0) {
      return 0;
    }

    FileDescriptor fdesc = validFdOrException();
    final ByteBuffer addressTo;
    final int addressToLen;
    try (Lease<ByteBuffer> addressToLease = target == null ? null
        : AFSocketAddress.SOCKETADDRESS_BUFFER_TL.take()) {
      if (addressToLease == null) {
        addressTo = null;
        addressToLen = 0;
      } else {
        addressTo = addressToLease.get();
        addressToLen = AFSocketAddress.unwrapAddressDirectBufferInternal(addressTo, target);
      }

      // accept "send buffer overflow" as packet loss
      // and don't retry (which may slow things down quite a bit)

      int pos = src.position();
      boolean isDirect = src.isDirect();
      ByteBuffer buf;
      int bufPos;

      final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && isBlocking())
          || isVirtualBlocking();
      final long now;
      if (virtualBlocking) {
        now = System.currentTimeMillis();
      } else {
        now = 0;
      }
      if (virtualBlocking || !blocking) {
        options |= NativeUnixSocket.OPT_NON_BLOCKING;
      }
      if (datagramMode) {
        options |= NativeUnixSocket.OPT_DGRAM_MODE;
      }

      int written;

      boolean park = false;
      virtualThreadLoop : do {
        if (virtualBlocking) {
          if (park) {
            VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_WRITE, now,
                timeout, this::close);
          }
          configureVirtualBlocking(true);
        }

        try (Lease<MutableHolder<ByteBuffer>> lease = isDirect ? null : getPrivateDirectByteBuffer(
            remaining)) {
          if (isDirect) {
            buf = src;
            bufPos = pos;
          } else {
            buf = Objects.requireNonNull(Objects.requireNonNull(lease).get().get());
            remaining = Math.min(remaining, buf.remaining());

            bufPos = buf.position();

            while (src.hasRemaining() && buf.hasRemaining()) {
              buf.put(src);
            }

            buf.position(bufPos);
          }

          written = NativeUnixSocket.send(fdesc, buf, bufPos, remaining, addressTo, addressToLen,
              options, ancillaryDataSupport);
          if (written == 0 && virtualBlocking) {
            // try again
            park = true;
            continue virtualThreadLoop;
          }
        } catch (SocketTimeoutException e) {
          if (virtualBlocking) {
            // try again
            park = true;
            continue virtualThreadLoop;
          } else {
            throw e;
          }
        } finally {
          if (virtualBlocking) {
            configureVirtualBlocking(false);
          }
        }
        break; // NOPMD.AvoidBranchingStatementAsLastInLoop virtualThreadLoop
      } while (true); // NOPMD.WhileLoopWithLiteralBoolean
      src.position(pos + written);
      return written;
    }
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
  @SuppressWarnings("null")
  Lease<MutableHolder<@NonNull ByteBuffer>> getPrivateDirectByteBuffer(int capacity) {
    if (capacity > TL_BUFFER_MAX_CAPACITY && TL_BUFFER_MAX_CAPACITY > 0) {
      // Capacity exceeds configurable maximum limit;
      // allocate but do not cache direct buffer.
      // This may incur a performance penalty at the cost of correctness when using such capacities.
      return ObjectPool.unpooledLease(new MutableHolder<>(ByteBuffer.allocateDirect(capacity)));
    }
    if (capacity < TL_BUFFER_MIN_CAPACITY) {
      capacity = TL_BUFFER_MIN_CAPACITY;
    }
    Lease<MutableHolder<ByteBuffer>> lease = TL_BUFFER.take();
    MutableHolder<ByteBuffer> holder = lease.get();
    ByteBuffer buffer = holder.get();
    if (buffer == null || capacity > buffer.capacity()) {
      buffer = ByteBuffer.allocateDirect(capacity);
      holder.set(buffer);
    }
    buffer.clear();
    return lease;
  }

  void implConfigureBlocking(boolean block) throws IOException {
    this.blocking = block;
    if (block && isVirtualBlocking()) {
      // do not actually change it here, defer it to when the virtual blocking counter goes to 0
    } else {
      NativeUnixSocket.configureBlocking(validFdOrException(), block);
    }
  }

  /**
   * Increments/decrements the "virtual blocking" counter (calls must be in pairs/balanced using
   * try-finally blocks).
   *
   * @param enabled {@code true} if increment, {@code false} if decrement.
   * @throws SocketException on error.
   * @throws IOException on error, including count overflow/underflow.
   */
  void configureVirtualBlocking(boolean enabled) throws SocketException, IOException {
    int v;
    if (enabled) {
      if ((v = this.virtualBlockingLeases.incrementAndGet()) >= 1 && blocking) {
        NativeUnixSocket.configureBlocking(validFdOrException(), false);
      }
      if (v >= Integer.MAX_VALUE) {
        throw new IOException("blocking overflow");
      }
    } else {
      if ((v = this.virtualBlockingLeases.decrementAndGet()) == 0 && blocking) {
        NativeUnixSocket.configureBlocking(validFdOrException(), true);
      }
      if (v < 0) {
        throw new IOException("blocking underflow");
      }
    }
  }

  boolean isVirtualBlocking() {
    return virtualBlockingLeases.get() > 0;
  }

  boolean isBlocking() {
    return blocking;
  }
}
