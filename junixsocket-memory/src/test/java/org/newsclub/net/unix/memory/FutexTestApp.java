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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileDescriptor;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel.MapMode;

/**
 * See {@link SharedMemoryTest#testFutexSeparateVM()}.
 *
 * @author Christian Kohlschütter
 */
public class FutexTestApp {
  public static void main(String[] args) {
    try (SharedMemory mem = SharedMemory.using(FileDescriptor.in)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

      long size = ms.byteSize();
      if (size != SharedMemoryTest.FORKEDVM_FUTEX_SIZE) {
        assertEquals(SharedMemory.defaultAllocationSize(), size, "Should either be exactly "
            + SharedMemoryTest.FORKEDVM_FUTEX_SIZE + " or page-aligned");
      }

      try (Futex futex = mem.futex(ms.asSlice(0, 4))) {
        futex.tryWake(true);
      }
    } catch (Throwable e) { // NOPMD
      e.printStackTrace();
      System.exit(1);
    }
  }
}
