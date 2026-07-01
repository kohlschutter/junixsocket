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

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import org.newsclub.net.unix.MemoryImplUtilInternal;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Some mapped memory.
 *
 * @author Christian Kohlschütter
 */
public final class MappedMemory implements Closeable {
  private final MemorySegment ms;
  private final SharedMemoryCleaner cleaner;
  private final boolean rw;
  private static final boolean IS_WINDOWS = ";".equals(System.getProperty("path.separator", ""));

  private MappedMemory(Arena arena, ByteBuffer mappedBuffer, boolean rw, FileDescriptor extraFd) {
    this.rw = rw;
    this.cleaner = new SharedMemoryCleaner(arena, this, extraFd);
    this.ms = SharedMemory.asRegisteredMemorySegment(cleaner, mappedBuffer, rw);
  }

  /**
   * Returns the {@link MemorySegment} backed by this instance.
   *
   * @return The {@link MemorySegment}.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public MemorySegment getMemorySegment() {
    return ms;
  }

  /**
   * Returns the minimum sub-allocation size.
   *
   * @return The minimum sub-allocation size.
   */
  public long getMinimumSubAllocationSize() {
    return defaultAllocationSize();
  }

  /**
   * Returns the system's default memory page allocation size for shared memory.
   * <p>
   * This may be larger than the system's regular page size (e.g., on Windows it's 64k).
   *
   * @return The page size.
   */
  public static long defaultAllocationSize() {
    return SharedMemory.getUtil().getSharedMemoryAllocationSize();
  }

  /**
   * Returns an anonymous read-only memory segment of the given length.
   *
   * @param arena The arena to associate the {@link MappedMemory} instance with.
   * @param length The length of the segment.
   * @return The {@link MappedMemory} segment.
   * @throws IOException on error.
   */
  public static MappedMemory anonymousReadOnlySegment(Arena arena, long length) throws IOException {
    return anonymousSegment(arena, MemoryImplUtilInternal.MMODE_READ, length);
  }

  /**
   * Returns an anonymous read/write memory segment of the given length.
   *
   * @param arena The arena to associate the {@link MappedMemory} instance with.
   * @param length The length of the segment.
   * @return The {@link MappedMemory} segment.
   * @throws IOException on error.
   */
  public static MappedMemory anonymousSegment(Arena arena, long length) throws IOException {
    return anonymousSegment(arena, MemoryImplUtilInternal.MMODE_READ_WRITE, length);
  }

  /**
   * Returns an anonymous memory segment of the given length that acts as a placeholder for later,
   * memory-mapped segments in this area.
   *
   * @param arena The arena to associate the {@link MappedMemory} instance with.
   * @param length The length of the segment.
   * @return The {@link MappedMemory} segment.
   * @throws IOException on error.
   */
  public static MappedMemory placeholderSegment(Arena arena, long length) throws IOException {
    return anonymousSegment(arena, MemoryImplUtilInternal.MMODE_PLACEHOLDER
        | MemoryImplUtilInternal.MMODE_READ_WRITE, length);
  }

  /**
   * Returns an anonymous copy-on-write (changes are local to the process running this JVM) memory
   * segment of the given length.
   *
   * @param arena The arena to associate the {@link MappedMemory} instance with.
   * @param length The length of the segment.
   * @return The {@link MappedMemory} segment.
   * @throws IOException on error.
   */
  public static MappedMemory anonymousCopyOnWriteSegment(Arena arena, long length)
      throws IOException {
    return anonymousSegment(arena, MemoryImplUtilInternal.MMODE_READ_WRITE
        | MemoryImplUtilInternal.MMODE_COPY_ON_WRITE, length);
  }

  private static MappedMemory anonymousSegment(Arena arena, int mode, long length)
      throws IOException {
    MemoryImplUtilInternal util = SharedMemory.getUtil();

    mode |= MemoryImplUtilInternal.MMODE_ANONYMOUS;

    long addr = util.mmap(0, null, 0, length, mode, null);
    ByteBuffer mappedBuffer = util.mappedBuffer(addr, length, null, 0, arena.allocate(0));

    return new MappedMemory(arena, mappedBuffer, (mode & MemoryImplUtilInternal.MMODE_WRITE) != 0,
        null);
  }

  /**
   * Maps some memory to a region of this {@link MappedMemory} object.
   *
   * @param msOffset The target offset in this object.
   * @param length The number of bytes to map.
   * @param fd The file descriptor to map from.
   * @param fdOffset The file descriptor-specific content offset
   * @return A new {@link MappedMemory} object, whose {@link MemorySegment} is a subrange of this
   *         object's {@link MemorySegment}.
   * @throws IOException on error.
   */
  public MappedMemory mapRegion(long msOffset, long length, FileDescriptor fd, long fdOffset)
      throws IOException {
    if (msOffset < 0) {
      throw new IllegalArgumentException("msOffset");
    }
    if (length <= 0) {
      throw new IllegalArgumentException("length");
    }
    if ((msOffset + length) > ms.byteSize()) {
      throw new IllegalArgumentException("capacity");
    }
    if (fdOffset < 0) {
      throw new IllegalArgumentException("fileOffset");
    }

    MemoryImplUtilInternal util = SharedMemory.getUtil();

    int mmode = ms.isReadOnly() ? MemoryImplUtilInternal.MMODE_READ
        : MemoryImplUtilInternal.MMODE_READ_WRITE;
    mmode |= MemoryImplUtilInternal.MMODE_FIXED;
    // FIXME cow

    long targetAddr = ms.address() + msOffset;

    FileDescriptor extraFd = IS_WINDOWS ? new FileDescriptor() : null;
    long actualAddr = util.mmap(targetAddr, fd, fdOffset, length, mmode, extraFd);

    if (actualAddr != targetAddr) {
      // unexpected
      util.unmap(targetAddr, length, 0, true);
      throw new IllegalStateException("targetAddr " + actualAddr + " vs expected " + targetAddr);
    }

    ByteBuffer mappedBuffer = util.mappedBuffer(actualAddr, length, null, 0, cleaner
        .getArenaSegment());

    return new MappedMemory(cleaner.getArena(), mappedBuffer, rw, extraFd);
  }

  @Override
  public void close() throws IOException {
    cleaner.close();
  }
}
