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

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.newsclub.net.unix.CleanableState;
import org.newsclub.net.unix.MemoryImplUtilInternal;

class SharedMemoryCleaner extends CleanableState {
  private final Map<MemorySegment, Integer> segments = new LinkedHashMap<>();
  private final Map<Futex, Futex> futexes = new WeakHashMap<>();
  private final Arena arena = Arena.ofShared();
  private final MemorySegment arenaSegment = arena.allocate(0);
  final FileDescriptor fd;
  Arena defaultArena;

  protected SharedMemoryCleaner(Object observed, FileDescriptor fd) {
    super(observed);
    this.fd = fd;
  }

  void registerMemorySegment(MemorySegment ms, int duplicates) {
    synchronized (segments) {
      segments.put(ms, duplicates);
    }
  }

  @Override
  @SuppressWarnings("PMD.CognitiveComplexity")
  protected void doClean() throws IOException {
    if (!SharedMemory.isUtilLoaded()) {
      // Nothing to do
      return;
    }

    Map<FileDescriptor, Long> map = SharedMemory.FD_MEMORY;
    if (map != null) {
      synchronized (map) {
        map.remove(fd);
      }
    }

    synchronized (futexes) {
      if (!futexes.isEmpty()) {
        for (Futex f : futexes.keySet()) {
          try {
            f.tryWake(true); // unblock waiting threads
            f.close();
          } catch (Exception e) { // NOPMD
            // ignore
          }
        }
        futexes.clear();
      }
    }

    if (defaultArena != null) {
      defaultArena.close();
      defaultArena = null;
    }

    MemoryImplUtilInternal util = SharedMemory.getUtil();

    IOException exc = null;
    if (fd.valid()) {
      try {
        util.close(fd);
      } catch (IOException e) {
        exc = e;
      }
    }

    synchronized (segments) {
      for (Map.Entry<MemorySegment, Integer> en : segments.entrySet()) {
        MemorySegment ms = en.getKey();
        int duplicates = en.getValue();

        long addr = ms.address();
        long length = ms.byteSize();
        if (ms.scope().isAlive()) {
          util.madvise(addr, length, MemoryImplUtilInternal.MADV_FREE_NOW, true);
          continue;
        }

        try {
          util.unmap(addr, length, duplicates, false);
        } catch (IOException e) {
          if (exc == null) {
            exc = e;
          } else {
            exc.addSuppressed(e);
          }
        }
      }
      segments.clear();
    }

    if (exc != null) {
      throw exc;
    }
  }

  public boolean isCovered(MemorySegment segment) {
    Objects.requireNonNull(segment);
    long start = segment.address();
    long end = start + segment.byteSize();
    synchronized (segments) {
      for (MemorySegment ms : segments.keySet()) {
        if (ms == segment) { // NOPMD
          return true;
        }
        long addr = ms.address();
        if (start >= addr && end <= addr + ms.byteSize()) {
          return true;
        }
      }
    }
    return false;
  }

  public MemorySegment getArenaSegment() {
    return arenaSegment;
  }

  public void registerFutex(Futex futex) {
    synchronized (futexes) {
      futexes.put(futex, futex);
    }
  }

  public void checkCovered(MemorySegment addr) throws IOException {
    if (!isCovered(addr)) {
      throw new IOException("Not a MemorySegment of ours");
    }
  }
}
