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
import java.nio.ByteBuffer;

/**
 * Internal class, to be used by junixsocket-memory only.
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("doclint")
public final class MemoryImplUtilInternal {
  public static final int S_IRUSR = NativeUnixSocket.S_IRUSR;
  public static final int S_IWUSR = NativeUnixSocket.S_IWUSR;
  public static final int S_IRGRP = NativeUnixSocket.S_IRGRP;
  public static final int S_IWGRP = NativeUnixSocket.S_IWGRP;
  public static final int S_IROTH = NativeUnixSocket.S_IROTH;
  public static final int S_IWOTH = NativeUnixSocket.S_IWOTH;

  public static final int MOPT_RDONLY = NativeUnixSocket.MOPT_RDONLY;
  public static final int MOPT_CREAT = NativeUnixSocket.MOPT_CREAT;
  public static final int MOPT_EXCL = NativeUnixSocket.MOPT_EXCL;

  private static final long SHM_ALLOC_SIZE = NativeUnixSocket.sharedMemoryAllocationSize();

  // FIXME: POSIX leaves the behavior of the combination of O_RDONLY and
  // O_TRUNC unspecified. On Linux, this will successfully truncate
  // an existing shared memory object—this may not be so on other UNIX
  // systems.
  public static final int MOPT_TRUNC = NativeUnixSocket.MOPT_TRUNC;
  public static final int MOPT_SEALABLE = NativeUnixSocket.MOPT_SEALABLE;
  public static final int MOPT_SECRET = NativeUnixSocket.MOPT_SECRET;
  public static final int MOPT_UNLINK_UPON_CLOSE = NativeUnixSocket.MOPT_UNLINK_UPON_CLOSE;

  public static final int MMODE_READ = NativeUnixSocket.MMODE_READ;
  public static final int MMODE_WRITE = NativeUnixSocket.MMODE_WRITE;
  public static final int MMODE_COPY_ON_WRITE = NativeUnixSocket.MMODE_COPY_ON_WRITE;
  public static final int MMODE_SYNC = NativeUnixSocket.MMODE_SYNC;

  public static final int MADV_NORMAL = NativeUnixSocket.MADV_NORMAL;
  public static final int MADV_FREE = NativeUnixSocket.MADV_FREE;
  public static final int MADV_FREE_NOW = NativeUnixSocket.MADV_FREE_NOW;
  public static final int MADV_WILLNEED = NativeUnixSocket.MADV_WILLNEED;
  public static final int MADV_DONTNEED = NativeUnixSocket.MADV_DONTNEED;

  private static final boolean NEED_TO_TRACK_SHM = NativeUnixSocket.needToTrackSharedMemory();
  private static final boolean FUTEX_INTER_PROCESS = NativeUnixSocket.futexIsInterProcess();

  MemoryImplUtilInternal() {
  }

  public void shmUnlink(String name) throws IOException {
    if (name != null) {
      NativeUnixSocket.shmUnlink(name);
    }
  }

  public long shmOpen(FileDescriptor fdOut, String name, long truncateLength, int mode, int options)
      throws IOException {
    return NativeUnixSocket.shmOpen(fdOut, name, truncateLength, mode, options);
  }

  public void close(FileDescriptor fd) throws IOException {
    NativeUnixSocket.close(fd);
  }

  public long getSharedMemoryAllocationSize() {
    return SHM_ALLOC_SIZE;
  }

  public ByteBuffer mmap(Object arenaSegment, FileDescriptor fd, long offset, long length,
      int mmode, int duplicates) throws IOException {
    if (offset < 0) {
      throw new IllegalArgumentException("offset");
    }
    if (length < 0) {
      throw new IllegalArgumentException("length");
    }
    if (duplicates < 0) {
      throw new IllegalArgumentException("duplicates");
    }

    return NativeUnixSocket.mmap(arenaSegment, fd, offset, length, mmode, duplicates);
  }

  public void unmap(long address, long byteSize, int duplicates, boolean ignoreError)
      throws IOException {
    NativeUnixSocket.unmap(address, byteSize, duplicates, ignoreError);
  }

  public void madvise(long address, long byteSize, int madv, boolean ignoreError)
      throws IOException {
    NativeUnixSocket.madvise(address, byteSize, madv, ignoreError);
  }

  public boolean futexWait(long address, int ifValue, int timeoutMillis) throws IOException {
    try {
      return NativeUnixSocket.futexWait(address, ifValue, timeoutMillis);
    } catch (OperationNotSupportedIOException e) {
      throw new UnsupportedOperationException("futexWait", e);
    }
  }

  public boolean futexWake(long address, boolean wakeAll) throws IOException {
    try {
      return NativeUnixSocket.futexWake(address, wakeAll);
    } catch (OperationNotSupportedIOException e) {
      throw new UnsupportedOperationException("futexWake", e);
    }
  }

  public boolean needToTrackSharedMemory() {
    return NEED_TO_TRACK_SHM;
  }

  public long sizeOfSharedMemory(FileDescriptor fd) throws IOException {
    return NativeUnixSocket.sizeOfSharedMemory(fd);
  }

  public boolean futexIsInterProcess() {
    return FUTEX_INTER_PROCESS;
  }
}
