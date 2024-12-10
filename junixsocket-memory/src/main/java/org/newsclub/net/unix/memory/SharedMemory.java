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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.FileChannelSupplier;
import org.newsclub.net.unix.FileDescriptorCast;
import org.newsclub.net.unix.MemoryImplUtilInternal;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Some shared memory.
 *
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings("OVERLY_PERMISSIVE_FILE_PERMISSION")
public final class SharedMemory implements Closeable {
  private static final Set<PosixFilePermission> DEFAULT_PERMISSIONS = PosixFilePermissions
      .fromString("rw-rw-rw-");

  /**
   * Keep track of known shared memory sizes, but only if useful (currently: Windows only).
   */
  static Map<FileDescriptor, Long> FD_MEMORY; // NOPMD

  /**
   * The exact size (in bytes) required for a {@link MemorySegment} used in
   * {@link #mutex(MemorySegment)}.
   */
  public static final int MUTEX_SEGMENT_SIZE = 8;

  /**
   * The exact size (in bytes) required for a {@link MemorySegment} used in
   * {@link #futex(MemorySegment)}.
   */
  static final int FUTEX32_SEGMENT_SIZE = 4;

  private static final Map<String, Integer> MAP_MODES = Map.of(//
      "READ_ONLY", MemoryImplUtilInternal.MMODE_READ, //
      "READ_WRITE", (MemoryImplUtilInternal.MMODE_READ | MemoryImplUtilInternal.MMODE_WRITE), //
      "PRIVATE", (MemoryImplUtilInternal.MMODE_READ | MemoryImplUtilInternal.MMODE_WRITE
          | MemoryImplUtilInternal.MMODE_COPY_ON_WRITE), //
      // from ExtendedMapMode:
      "READ_ONLY_SYNC", (MemoryImplUtilInternal.MMODE_READ | MemoryImplUtilInternal.MMODE_SYNC), //
      "READ_WRITE_SYNC", (MemoryImplUtilInternal.MMODE_READ | MemoryImplUtilInternal.MMODE_WRITE
          | MemoryImplUtilInternal.MMODE_SYNC) //
  );

  private final String name;

  private final boolean knownReadOnly;
  private final boolean unlinkUponClose;

  static MemoryImplUtilInternal UTIL = null; // NOPMD

  private final SharedMemoryCleaner cleaner;

  private final long size;

  private SharedMemory(FileDescriptor fd) throws IOException {
    this(fd, -1, null, 0);
  }

  private SharedMemory(FileDescriptor fd, long size, String name, int mopts) throws IOException {
    super();
    if (size == -1) {
      this.size = determineSize(fd);
    } else {
      this.size = size;
    }
    Objects.requireNonNull(fd);
    this.cleaner = new SharedMemoryCleaner(this, fd);
    this.name = name;
    this.knownReadOnly = isReadOnly(mopts);
    this.unlinkUponClose = (mopts & MemoryImplUtilInternal.MOPT_UNLINK_UPON_CLOSE) != 0;
  }

  private static long determineSize(FileDescriptor fd) throws IOException {
    Map<FileDescriptor, Long> map = FD_MEMORY;
    if (map != null) {
      synchronized (map) {
        Long knownSize = map.get(fd);
        if (knownSize != null) {
          return knownSize;
        }
      }
    }
    return getUtil().sizeOfSharedMemory(fd);
    // ; return FileDescriptorCast.using(fd).as(FileChannel.class).size();
  }

  static boolean isUtilLoaded() {
    return UTIL != null;
  }

  static MemoryImplUtilInternal getUtil() {
    AFSocket.isSupported(); // trigger init

    MemoryImplUtilInternal util = UTIL;
    if (util == null) {
      throw new IllegalStateException("MemoryImplUtilInternal not initialized");
    }
    return util;
  }

  /**
   * Internal initializer used by junixsocket-common; do not use.
   *
   * @param util The MemoryImplUtil instance.
   */
  public static synchronized void init(MemoryImplUtilInternal util) {
    if (util != null) {
      if (UTIL == null) {
        UTIL = util;
        if (util.needToTrackSharedMemory()) {
          FD_MEMORY = new WeakHashMap<>();
        } else {
          FD_MEMORY = null;
        }
      } else {
        throw new IllegalStateException();
      }
    }
  }

  /**
   * Creates a new {@link SharedMemory} instance using the given file descriptor, which can be
   * associated with a regular file that is to be memory-mapped, or a shared memory region.
   *
   * @param fd The file descriptor.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory using(FileDescriptor fd) throws IOException {
    return new SharedMemory(fd);
  }

  /**
   * Creates a new {@link SharedMemory} instance under the given name, using default permissions
   * (read-write for all users, where applicable). If there already exists an object under that
   * name, this call fails with an error.
   *
   * @param name The name.
   * @param minimumLength The requested length (the actual object can be larger).
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createExclusively(String name, long minimumLength,
      SharedMemoryOption... options) throws IOException {
    return createExclusively(name, minimumLength, DEFAULT_PERMISSIONS, options);
  }

  /**
   * Creates a new {@link SharedMemory} instance under the given name. If there already exists an
   * instance under that name, this call fails with an error.
   *
   * @param name The name.
   * @param minimumLength The requested length (the actual object can be larger).
   * @param perms The file system permissions, where applicable.
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createExclusively(String name, long minimumLength,
      Set<PosixFilePermission> perms, SharedMemoryOption... options) throws IOException {
    return shmOpen(name, perms, toOptions(options) | MemoryImplUtilInternal.MOPT_CREAT
        | MemoryImplUtilInternal.MOPT_EXCL, minimumLength);
  }

  /**
   * Creates a new {@link SharedMemory} instance under the given name, using default permissions
   * (read-write for all users, where applicable). If there already exists an object under that
   * name, that object is opened instead.
   *
   * @param name The name.
   * @param minimumLength The requested length (the actual object can be larger).
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createOrOpenExisting(String name, long minimumLength,
      SharedMemoryOption... options) throws IOException {
    return createOrOpenExisting(name, minimumLength, DEFAULT_PERMISSIONS, options);
  }

  /**
   * Creates a new {@link SharedMemory} instance under the given namex. If there already exists an
   * object under that name, that object is opened instead.
   *
   * @param name The name.
   * @param minimumLength The requested length (the actual object can be larger).
   * @param perms The file system permissions, where applicable.
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createOrOpenExisting(String name, long minimumLength,
      Set<PosixFilePermission> perms, SharedMemoryOption... options) throws IOException {
    return shmOpen(name, perms, toOptions(options) | MemoryImplUtilInternal.MOPT_CREAT,
        minimumLength);
  }

  /**
   * Creates a new {@link SharedMemory} instance under the given name, using default permissions
   * (read-write for all users, where applicable). If there already exists an object under that
   * name, that object is reused (truncated to zero or deleted prior to allocation).
   *
   * @param name The name.
   * @param minimumLength The requested length (the actual object can be larger).
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createOrReuseExisting(String name, long minimumLength,
      SharedMemoryOption... options) throws IOException {
    return createOrReuseExisting(name, minimumLength, DEFAULT_PERMISSIONS, options);
  }

  /**
   * Creates a new {@link SharedMemory} instance under the given name. If there already exists an
   * object under that name, that object is reused (truncated to zero or deleted prior to
   * allocation).
   *
   * @param name The name.
   * @param minimumLength The requested length (the actual object can be larger).
   * @param perms The file system permissions, where applicable.
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createOrReuseExisting(String name, long minimumLength,
      Set<PosixFilePermission> perms, SharedMemoryOption... options) throws IOException {
    return shmOpen(name, perms, toOptions(options) | MemoryImplUtilInternal.MOPT_CREAT
        | MemoryImplUtilInternal.MOPT_TRUNC, minimumLength);
  }

  /**
   * Creates a new {@link SharedMemory} instance under the given name, using the object under the
   * given name. This call fails with an exception if no such object exists.
   *
   * @param name The name.
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory openExisting(String name, SharedMemoryOption... options)
      throws IOException {
    return shmOpen(name, DEFAULT_PERMISSIONS, toOptions(options), 0);
  }

  /**
   * Creates a new {@link SharedMemory} instance using an anonymous identifier.
   *
   * @param minimumLength The requested length (the actual object can be larger).
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createAnonymous(long minimumLength) throws IOException {
    return createAnonymous(minimumLength, (SharedMemoryOption[]) null);
  }

  /**
   * Creates a new {@link SharedMemory} instance using an anonymous identifier.
   *
   * @param minimumLength The requested length (the actual object can be larger).
   * @param options Instantiation options.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static SharedMemory createAnonymous(long minimumLength, SharedMemoryOption... options)
      throws IOException {
    int mopts = toOptions(options) | MemoryImplUtilInternal.MOPT_CREAT
        | MemoryImplUtilInternal.MOPT_TRUNC;
    return shmOpen0(null, Collections.emptySet(), mopts, minimumLength);
  }

  private static SharedMemory shmOpen(String name, Set<PosixFilePermission> perms, int mopts,
      long minimumLength) throws IOException {
    name = checkShmName(name);
    return shmOpen0(name, perms, mopts, minimumLength);
  }

  private static SharedMemory shmOpen0(String name, Set<PosixFilePermission> perms, int mopts,
      long minimumLength) throws IOException {
    MemoryImplUtilInternal util = getUtil();

    FileDescriptor fd = new FileDescriptor();
    long size = util.shmOpen(fd, name, minimumLength, toMode(perms), mopts);
    SharedMemory sm = new SharedMemory(fd, size, name, mopts);

    Map<FileDescriptor, Long> map = FD_MEMORY;
    if (map != null) {
      synchronized (map) {
        map.put(fd, size);
      }
    }
    return sm;
  }

  private static boolean isReadOnly(int mopts) {
    return (mopts & MemoryImplUtilInternal.MOPT_RDONLY) != 0;
  }

  /**
   * Asks to explicitly unlink/remove a shared memory object identified by the given name.
   * <p>
   * This call may silently fail (some platforms do not support explicit unlinking -- they cleanup
   * the objects automatically).
   *
   * @param name The name of the object that should be unlinked.
   * @throws IOException on error.
   */
  public static void unlinkShared(String name) throws IOException {
    getUtil().shmUnlink(checkShmName(name));
  }

  /**
   * Returns the file descriptor associated with this instance.
   *
   * @return The file descriptor.
   */
  public FileDescriptor getFileDescriptor() {
    return cleaner.fd;
  }

  /**
   * Returns a {@link FileChannel} instance that can be used for memory-mapping via {code
   * FileChannel#map}. There are no guarantees that writing/truncating/mapping works, however
   * getting the current allocation size via {@link FileChannel#size()} should work.
   *
   * @return The {@link FileChannel}.
   * @throws IOException on error.
   * @throws ClosedChannelException if the file descriptor is closed.
   * @throws UnsupportedOperationException if this operation is not supported on this platform.
   */
  FileChannel asMappableFileChannel() throws IOException {
    if (!cleaner.fd.valid()) {
      throw new ClosedChannelException();
    }
    if (FD_MEMORY != null) {
      throw new UnsupportedOperationException();
    }
    if (knownReadOnly) {
      return FileDescriptorCast.using(cleaner.fd).as(FileChannelSupplier.ReadOnly.class).get();
    } else {
      return FileDescriptorCast.using(cleaner.fd).as(FileChannel.class);
    }
  }

  /**
   * Return a {@link MemorySegment} instance corresponding to this shared memory object, using the
   * given {@link MapMode}, and a custom shared {@link Arena} that will be closed upon
   * {@link SharedMemory#close()}.
   *
   * @param mapMode The map mode.
   * @return The memory segment.
   * @throws IOException on error.
   */
  public MemorySegment asMappedMemorySegment(MapMode mapMode) throws IOException {
    return asMappedMemorySegment(mapMode, null, 0);
  }

  /**
   * Return a {@link MemorySegment} instance corresponding to this shared memory object, using the
   * given {@link MapMode}, and the given arena.
   * <p>
   * If the given arena is {@code null}, a custom shared {@link Arena} is used that will be closed
   * upon {@link SharedMemory#close()}.
   *
   * @param mapMode The map mode.
   * @param arena The arena to use, or {@code null}.
   * @return The memory segment.
   * @throws IOException on error.
   */
  public MemorySegment asMappedMemorySegment(MapMode mapMode, Arena arena) throws IOException {
    return asMappedMemorySegment(mapMode, arena, 0);
  }

  /**
   * Return a {@link MemorySegment} instance corresponding to this shared memory object -- repeated
   * multiple times after each other (aligned with page size) -- using the given {@link MapMode},
   * and the given arena, as well as the duplication count.
   * <p>
   * This method is particularly useful to simplify building circular buffers ("magic RingBuffer").
   * <p>
   * If the given arena is {@code null}, a custom shared {@link Arena} is used that will be closed
   * upon {@link SharedMemory#close()}.
   *
   * @param mapMode The map mode.
   * @param arena The arena to use, or {@code null}.
   * @param duplicates The number of times the shared memory should be repeated (0 = no repetitions,
   *          just 1 copy).
   * @return The memory segment.
   * @throws IOException on error.
   */
  public MemorySegment asMappedMemorySegment(MapMode mapMode, Arena arena, int duplicates)
      throws IOException {
    return asMappedMemorySegment(mapMode, arena, 0, -1, duplicates);
  }

  /**
   * Return a {@link MemorySegment} instance corresponding to a range of this shared memory object
   * -- repeated multiple times after each other (aligned with page size) -- using the given
   * {@link MapMode}, and the given arena, as well as the duplication count.
   * <p>
   * This method is particularly useful to simplify building circular buffers ("magic RingBuffer").
   * <p>
   * If the given arena is {@code null}, a custom shared {@link Arena} is used that will be closed
   * upon {@link SharedMemory#close()}.
   *
   * @param mapMode The map mode.
   * @param arena The arena to use, or {@code null}.
   * @param offset The offset from the beginning of this segment, in bytes.
   * @param length The length of the mapped region, in bytes.
   * @param duplicates The number of times the shared memory should be repeated (0 = no repetitions,
   *          just 1 copy).
   * @return The memory segment.
   * @throws IOException on error.
   */
  public MemorySegment asMappedMemorySegment(MapMode mapMode, Arena arena, long offset, long length,
      int duplicates) throws IOException {
    if (offset < 0) {
      throw new IllegalArgumentException("startOffset");
    } else if (length < -1) {
      throw new IllegalArgumentException("length");
    }

    if (arena == null) {
      arena = cleaner.defaultArena;
      if (arena == null) {
        arena = cleaner.defaultArena = Arena.ofShared();
      }
    }

    int mmode = resolveMmode(mapMode);
    if (length == -1) {
      // FileChannel fc = asMappableFileChannel();
      // long size = fc.size();

      length = size;
    }

    // use a zero-length, 0-address segment for lifecycle management, preventing chicken-egg problem
    MemorySegment arenaSegment = arena.allocate(0);

    ByteBuffer buf = getUtil().mmap(arenaSegment, cleaner.fd, offset, length, mmode, duplicates);
    if ((mmode & MemoryImplUtilInternal.MMODE_WRITE) == 0) {
      // The MapMode is read-only.
      // If we don't ask for a read-only buffer here, write accesses will fail with a page fault
      // ("java.lang.InternalError: a fault occurred in an unsafe memory access operation")
      buf = buf.asReadOnlyBuffer();
    }
    MemorySegment ms = MemorySegment.ofBuffer(buf);
    cleaner.registerMemorySegment(ms, duplicates);
    return ms;
  }

  /**
   * Adds the given {@link MemorySeal}s, preventing certain operations on shared memory.
   *
   * @param seals The seals.
   * @throws IOException on error (e.g., if unsupported).
   */
  @SuppressWarnings("DoNotCallSuggester") // ErrorProne
  public void addSeals(Set<MemorySeal> seals) throws IOException {
    throw new IOException("Unsupported"); // FIXME
  }

  /**
   * Returns the current {@link MemorySeal}s for this shared memory instance.
   *
   * @return The seals, or empty if none or unsupported.
   * @throws IOException on error (e.g., if a system call fails unexpectedly).
   */
  public Set<MemorySeal> getSeals() throws IOException {
    return Collections.emptySet(); // FIXME
  }

  private static String checkShmName(String name) {
    Objects.requireNonNull(name);
    if (name.length() == 0) {
      throw new IllegalArgumentException("Name cannot be empty");
    }
    if (name.indexOf('/', 1) != -1) {
      throw new IllegalArgumentException("Name must not contain extra slashes");
    }
    if (name.charAt(0) == '/') {
      return name;
    } else {
      return "/" + name;
    }
  }

  private static int toMode(Set<PosixFilePermission> perms) {
    int mode = 0;
    if (perms == null) {
      perms = DEFAULT_PERMISSIONS;
    }
    for (PosixFilePermission perm : perms) {
      switch (perm) {
        case OWNER_READ:
          mode |= MemoryImplUtilInternal.S_IRUSR;
          break;
        case OWNER_WRITE:
          mode |= MemoryImplUtilInternal.S_IWUSR;
          break;
        case GROUP_READ:
          mode |= MemoryImplUtilInternal.S_IRGRP;
          break;
        case GROUP_WRITE:
          mode |= MemoryImplUtilInternal.S_IWGRP;
          break;
        case OTHERS_READ:
          mode |= MemoryImplUtilInternal.S_IROTH;
          break;
        case OTHERS_WRITE:
          mode |= MemoryImplUtilInternal.S_IWOTH;
          break;
        default:
          throw new IllegalArgumentException("Unsupported permission: " + perm);
      }
    }
    return mode;
  }

  private static int toOptions(SharedMemoryOption[] options) {
    int opt = 0;
    if (options != null) {
      for (SharedMemoryOption option : options) {
        opt |= option.getOpt();
      }
    }
    return opt;
  }

  /**
   * Closes this {@link SharedMemory} resource, potentially unlinking the corresponding underlying
   * resource from the kernel if the object has been instantiated with
   * {@link SharedMemoryOption#UNLINK_UPON_CLOSE}.
   */
  @Override
  public void close() throws IOException {
    boolean valid = cleaner.fd.valid();

    cleaner.close();
    if (unlinkUponClose && valid && name != null) {
      MemoryImplUtilInternal util = getUtil();
      util.shmUnlink(name);
    }
  }

  String getName() {
    return name;
  }

  private static int resolveMmode(MapMode mapMode) {
    String modeString = mapMode.toString();
    Integer mmode = MAP_MODES.get(modeString);
    if (mmode == null) {
      throw new UnsupportedOperationException("MapMode");
    }
    return mmode;
  }

  /**
   * Returns the system's default memory page allocation size for shared memory.
   * <p>
   * This may be larger than the system's regular page size (e.g., on Windows it's 64k).
   *
   * @return The page size.
   */
  public static long defaultAllocationSize() {
    return getUtil().getSharedMemoryAllocationSize();
  }

  Futex futex(MemorySegment addr) throws IOException {
    return futex(addr, false, false);
  }

  Futex futex(MemorySegment addr, boolean wakeUpOnClose) throws IOException {
    return futex(addr, wakeUpOnClose, false);
  }

  Futex futex(MemorySegment addr, boolean wakeUpOnClose, boolean zeroValueOnClose)
      throws IOException {
    if (addr.isReadOnly()) {
      throw new IOException("MemorySegment is read-only");
    }
    cleaner.checkCovered(addr);
    Futex futex = new Futex32(addr, zeroValueOnClose);
    if (wakeUpOnClose) {
      cleaner.registerFutex(futex);
    }
    return futex;
  }

  /**
   * Returns a {@link SharedMutex} instance working with the given {@link MemorySegment}, which has
   * to be exactly {@link #MUTEX_SEGMENT_SIZE} bytes long.
   *
   * @param addr The address.
   * @return The instance.
   * @throws IOException on error.
   */
  public SharedMutex mutex(MemorySegment addr) throws IOException {
    return mutex(addr, false);
  }

  private SharedMutex mutex(MemorySegment addr, boolean unlockOnClose) throws IOException {
    if (addr.isReadOnly()) {
      throw new IOException("MemorySegment is read-only");
    }
    if (addr.byteSize() != 8) {
      throw new IOException("MemorySegment must be exactly 8 bytes long");
    }

    cleaner.checkCovered(addr);

    Futex32 futex = new Futex32(addr.asSlice(0, 4), unlockOnClose);
    if (unlockOnClose) {
      cleaner.registerFutex(futex);
    }

    return futex.mutex();
  }

  /**
   * Returns the aligned size of this shared memory instance.
   *
   * @return The aligned size, in bytes.
   */
  public long byteSize() {
    return size;
  }
}
