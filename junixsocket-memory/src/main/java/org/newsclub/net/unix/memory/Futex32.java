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
package org.newsclub.net.unix.memory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.ValueLayout.OfInt;
import java.lang.invoke.VarHandle;

import org.newsclub.net.unix.MemoryImplUtilInternal;

final class Futex32 implements Futex {
  private static final VarHandle VH_INT = ValueLayout.OfInt.JAVA_INT.varHandle();

  private static final int MUTEX_UNLOCKED = 0;
  private static final int MUTEX_LOCKED = 1;
  private static final int MUTEX_LOCKED_WAITING = 2;

  private final MemorySegment ms;
  private final boolean zeroOnClose;
  private final long address;
  private volatile boolean closed = false;

  Futex32(MemorySegment addr, boolean zeroOnClose) throws IOException {
    this.address = addr.address();
    if ((address & 3) != 0) {
      throw new IOException("Not aligned");
    }
    if (addr.byteSize() != SharedMemory.FUTEX32_SEGMENT_SIZE) {
      throw new IOException("MemorySegment must be exactly 4 bytes long");
    }
    this.ms = addr;
    this.zeroOnClose = zeroOnClose;

    // Make sure the 32-bit value is accessible (page-in memory)
    SharedMemory.UTIL.madvise(address, SharedMemory.FUTEX32_SEGMENT_SIZE,
        MemoryImplUtilInternal.MADV_WILLNEED, true);
  }

  @Override
  public void close() {
    if (zeroOnClose && ms != null) {
      ms.set(OfInt.JAVA_INT, 0, 0);
    }
    this.closed = true;
  }

  @Override
  public boolean tryWait(int ifValue, int timeoutMillis) throws IOException {
    if (closed) {
      return false;
    }

    return SharedMemory.UTIL.futexWait(address, ifValue, timeoutMillis);
  }

  @Override
  public boolean tryWake(boolean wakeAll) throws IOException {
    return SharedMemory.UTIL.futexWake(address, wakeAll);
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  MemorySegment getMemorySegment() {
    return ms;
  }

  @Override
  public boolean isInterProcess() {
    return SharedMemory.UTIL.futexIsInterProcess();
  }

  private final class Mutex32 implements SharedMutex {
    @Override
    public void close() throws IOException {
      Futex32.this.close();
    }

    @Override
    public boolean tryLock(int timeoutMillis) throws IOException {
      int w = (int) VH_INT.compareAndExchange(ms, 0, MUTEX_UNLOCKED, MUTEX_LOCKED);
      if (w == MUTEX_UNLOCKED) {
        return true;
      }

      if (w != MUTEX_LOCKED_WAITING) {
        w = (int) VH_INT.getAndSet(ms, 0, MUTEX_LOCKED_WAITING);
      }

      if (w == MUTEX_UNLOCKED) {
        return true;
      }

      long start = System.currentTimeMillis();
      while (!Thread.interrupted()) {
        if (!Futex32.this.tryWait(MUTEX_LOCKED_WAITING, timeoutMillis)) {
          if (isClosed()) {
            return false;
          }
        }
        w = (int) VH_INT.getAndSet(ms, 0, MUTEX_LOCKED_WAITING);
        if (w == MUTEX_UNLOCKED) {
          return true;
        }
        if (timeoutMillis != 0) {
          timeoutMillis -= (int) (System.currentTimeMillis() - start);
          if (timeoutMillis <= 0) {
            return false;
          }
        }
      }
      return false;
    }

    @Override
    public void unlock() throws IOException {
      int c = (int) VH_INT.getAndAdd(ms, 0, -1);
      switch (c) {
        case MUTEX_UNLOCKED:
        case MUTEX_LOCKED:
          break;
        default:
          VH_INT.set(ms, 0, MUTEX_UNLOCKED);
          Futex32.this.tryWake(false);
      }
    }

    @Override
    public boolean isReentrant() {
      return false;
    }

    @Override
    public boolean isInterProcess() {
      return Futex32.this.isInterProcess();
    }
  }

  SharedMutex mutex() {
    return new Mutex32();
  }
}