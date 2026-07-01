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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

public class MappedMemoryTest {
  @Test
  public void testAnonymous() throws Exception {
    try (Arena arena = Arena.ofConfined();
        MappedMemory mm = MappedMemory.anonymousSegment(arena, 64)) {
      MemorySegment ms = mm.getMemorySegment();

      assertEquals(0, ms.get(ValueLayout.JAVA_BYTE, 0));
      ms.set(ValueLayout.JAVA_BYTE, 0, (byte) 123);
      assertEquals(123, ms.get(ValueLayout.JAVA_BYTE, 0));
      assertThrows(IndexOutOfBoundsException.class, () -> ms.get(ValueLayout.JAVA_BYTE, 1024));
    }
  }

  @Test
  public void testAnonymousReadOnly() throws Exception {
    try (Arena arena = Arena.ofConfined();
        MappedMemory mm = MappedMemory.anonymousReadOnlySegment(arena, 64)) {
      MemorySegment ms = mm.getMemorySegment();

      assertEquals(0, ms.get(ValueLayout.JAVA_BYTE, 0));
      assertThrows(IllegalArgumentException.class, () -> ms.set(ValueLayout.JAVA_BYTE, 0,
          (byte) 123));
      assertEquals(0, ms.get(ValueLayout.JAVA_BYTE, 0));
      assertThrows(IndexOutOfBoundsException.class, () -> ms.get(ValueLayout.JAVA_BYTE, 1024));
    }
  }

  @Test
  @SuppressWarnings({"PMD.UseTryWithResources", "CatchAndPrintStackTrace"})
  @SuppressFBWarnings("PATH_TRAVERSAL_IN")
  public void testMappedFiles() throws Exception {
    int numFiles = 5;
    long fileSize = MappedMemory.defaultAllocationSize();
    Path dir = Files.createTempDirectory("jux");

    try {
      for (int i = 0; i < numFiles; i++) {
        try (FileChannel fc = FileChannel.open(dir.resolve(Integer.toString(i)),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
          MemorySegment ms = Arena.ofConfined().allocate(fileSize);
          ms.fill((byte) i);
          ByteBuffer bb = ms.asByteBuffer();
          while (bb.hasRemaining()) {
            fc.write(bb);
          }
        }
      }

      try (Arena arena = Arena.ofConfined();
          MappedMemory mm = MappedMemory.placeholderSegment(arena, numFiles * fileSize);) {

        RandomAccessFile[] rafs = new RandomAccessFile[numFiles];
        MappedMemory[] parts = new MappedMemory[numFiles];
        try {
          for (int i = 0; i < numFiles; i++) {
            rafs[i] = new RandomAccessFile(dir.resolve(Integer.toString(i)).toString(), "rw");
            parts[i] = mm.mapRegion(i * fileSize, fileSize, rafs[i].getFD(), 0);
          }

          MemorySegment ms = mm.getMemorySegment();
          for (int i = 0; i < numFiles; i++) {
            assertEquals(i, ms.get(ValueLayout.JAVA_BYTE, i * fileSize));
            assertEquals(i, ms.get(ValueLayout.JAVA_BYTE, (i + 1) * fileSize - 1));
          }

          for (int i = 0; i < numFiles; i++) {
            ms.asSlice(i * fileSize, fileSize).fill((byte) ('A' + i));
          }

          for (int i = 0; i < numFiles; i++) {
            RandomAccessFile raf = rafs[i];
            assertEquals('A' + i, raf.read());
            raf.seek(fileSize - 1);
            assertEquals('A' + i, raf.read());
          }
        } finally {
          for (int i = 0; i < numFiles; i++) {
            close(parts[i]);
            close(rafs[i]);
          }
        }
      }
    } finally {
      try (Stream<Path> stream = Files.list(dir)) {
        stream.forEach((p) -> {
          try {
            Files.deleteIfExists(p);
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
      }
      Files.deleteIfExists(dir);
    }
  }

  private static void close(Closeable cl) throws IOException {
    if (cl != null) {
      cl.close();
    }
  }
}
