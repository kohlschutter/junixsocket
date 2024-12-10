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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout.OfByte;
import java.lang.foreign.ValueLayout.OfInt;
import java.lang.foreign.ValueLayout.OfLong;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.NonWritableChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.FileDescriptorCast;
import org.newsclub.net.unix.OperationNotSupportedIOException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.ExecutionEnvironmentRequirement;
import com.kohlschutter.testutil.ExecutionEnvironmentRequirement.Rule;
import com.kohlschutter.testutil.ForkedVM;
import com.kohlschutter.testutil.ForkedVMRequirement;
import com.kohlschutter.testutil.TestAbortedNotAnIssueException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

public class SharedMemoryTest {
  static final int FORKEDVM_FUTEX_SIZE = 16;
  private static final int FUTEX32BIT_CHECK_WAIT_TIME = 20;

  static {
    Thread.setDefaultUncaughtExceptionHandler((th, t) -> {
      System.err.println("Uncaught exception @ " + th + ":");
      t.printStackTrace();
    });
  }

  @Test
  public void testPageSize() throws Exception {
    long pageSize = SharedMemory.defaultAllocationSize();
    assertNotEquals(0, pageSize);
    assertTrue((pageSize & 0xFFFL) == 0, "Page size should be a multiple of 4k");

    System.out.println("System page size: " + (pageSize / 1024) + "k");
  }

  @Test
  public void testOpenNull() throws Exception {
    assertThrows(NullPointerException.class, () -> SharedMemory.openExisting((String) null));
  }

  @Test
  public void testOpenEmpty() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> SharedMemory.openExisting(""));
  }

  @Test
  public void testSlashes() throws Exception {
    // slash is optional, but there can only be one at the start
    assertThrows(FileNotFoundException.class, () -> SharedMemory.openExisting("juxtest.Slashes"));
    assertThrows(FileNotFoundException.class, () -> SharedMemory.openExisting("/juxtest.Slashes"));

    assertThrows(IllegalArgumentException.class, () -> SharedMemory.openExisting("sl/ash"));
    assertThrows(IllegalArgumentException.class, () -> SharedMemory.openExisting("/sl/ash"));
    assertThrows(IllegalArgumentException.class, () -> SharedMemory.openExisting("/slashes/"));
    assertThrows(IllegalArgumentException.class, () -> SharedMemory.openExisting("sl/ash"));
    assertThrows(IllegalArgumentException.class, () -> SharedMemory.openExisting("slash/"));
    assertThrows(IllegalArgumentException.class, () -> SharedMemory.openExisting("sl/as/hes"));
  }

  @Test
  public void testOpenTwoAnonymous() throws Exception {
    try (SharedMemory mem1 = SharedMemory.createAnonymous(16)) {
      try (SharedMemory mem2 = SharedMemory.createAnonymous(16)) {
        String name1 = mem1.getName();
        String name2 = mem2.getName();

        if (name1 != null) {
          assertNotNull(name2);
          assertNotEquals(name1, name2);
        } else {
          assertNull(name2);
        }

        assertTrue(mem1.getFileDescriptor().valid());
        assertTrue(mem2.getFileDescriptor().valid());
      }
    }
  }

  @ExecutionEnvironmentRequirement(windows = Rule.PROHIBITED)
  @Test
  public void testFileChannelMMap() throws Exception {
    String name = "juxtest.FileChannelMap";

    try {
      SharedMemory.unlinkShared(name); // forcibly remove, just in case
      try (SharedMemory mem = SharedMemory.createOrReuseExisting(name, 3,
          SharedMemoryOption.UNLINK_UPON_CLOSE)) {

        int actuallyAllocatedSize = (int) mem.byteSize() /* (int) fc.size() */;
        // System.out.println("Allocation size: " + actuallyAllocatedSize);
        assertTrue(actuallyAllocatedSize >= 3);

        try (FileChannel fc = mem.asMappableFileChannel()) {
          assertEquals(actuallyAllocatedSize, fc.size());
          MappedByteBuffer mbb = fc.map(MapMode.READ_WRITE, 0, actuallyAllocatedSize);
          assertTrue(mbb.capacity() >= 3);
          assertTrue(mbb.limit() >= 3);
          assertEquals(0, mbb.position());

          mbb.put((byte) 1);
          mbb.put((byte) 2);
          mbb.put((byte) 3);
          mbb.put(actuallyAllocatedSize - 1, (byte) 3);

          int actuallyAllocatedSize0 = (int) fc.size(); // don't expect any extra allocations for
                                                        // that
                                                        // fourth put
          assertEquals(actuallyAllocatedSize, actuallyAllocatedSize0);

          assertThrows(FileAlreadyExistsException.class, () -> SharedMemory.createExclusively(name,
              16));

          // mbb.force();

          // opening an existing mapping by name should retain its data
          try (SharedMemory mem1 = SharedMemory.openExisting(name);
              FileChannel fc1 = mem1.asMappableFileChannel()) {
            int actuallyAllocatedSize1 = (int) fc1.size();
            assertEquals(actuallyAllocatedSize0, actuallyAllocatedSize1);

            MappedByteBuffer mbb1 = fc1.map(MapMode.READ_WRITE, 0, actuallyAllocatedSize1);
            assertEquals(1, mbb1.get());
            assertEquals(2, mbb1.get());
            assertEquals(3, mbb1.get());

            assertFalse(mbb1.isReadOnly());
          }

          // reusing an existing mapping should still clear out the previous memory
          try (SharedMemory mem1 = SharedMemory.createOrReuseExisting(name, 3);
              FileChannel fc1 = mem1.asMappableFileChannel()) {
            int actuallyAllocatedSize1 = (int) fc1.size();
            assertEquals(actuallyAllocatedSize0, actuallyAllocatedSize1);

            MappedByteBuffer mbb1 = fc1.map(MapMode.READ_WRITE, 0, actuallyAllocatedSize1);
            assertEquals(0, mbb1.get());
            assertEquals(0, mbb1.get());
            assertEquals(0, mbb1.get());

            assertFalse(mbb1.isReadOnly());
          }

          // read-only should work as expected
          try (SharedMemory mem2 = SharedMemory.createOrReuseExisting(name, 3,
              SharedMemoryOption.READ_ONLY); //
              FileChannel fc2 = mem2.asMappableFileChannel()) {

            assertThrows(NonWritableChannelException.class, () -> fc2.map(MapMode.READ_WRITE, 0, fc2
                .size()));

            MappedByteBuffer mbb2 = fc2.map(MapMode.READ_ONLY, 0, fc2.size());
            assertTrue(mbb2.isReadOnly());
          }
        } catch (UnsupportedOperationException e) {
          throw new TestAbortedWithImportantMessageException(
              MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
              "System does not support SharedMemory#asMappableFileChannel", e);
        }
      }

      // We did automatically unlink the shared memory object upon close, so there isn't one now
      assertThrows(FileNotFoundException.class, () -> SharedMemory.openExisting(name));
    } finally {
      SharedMemory.unlinkShared(name);
    }
  }

  @Test
  @SuppressWarnings("preview")
  public void testMemorySegmentFileChannelMap() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(64)) {
      try (FileChannel fc = mem.asMappableFileChannel()) {
        MemorySegment ms = fc.map(MapMode.READ_WRITE, 0, fc.size(), Arena.global());
        ms.setString(0, "Hello");
        ms.force();
      } catch (UnsupportedOperationException e) {
        throw new TestAbortedWithImportantMessageException(
            MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
            "System does not support SharedMemory#asMappableFileChannel", e);
      }
    }
  }

  @Test
  public void testWriteFileOutputStream() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(16);
        FileOutputStream out = new FileOutputStream(mem.getFileDescriptor())) {

      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_ONLY);
      try {
        out.write(1); // please don't do this
      } catch (IOException e) {
        // macOS: Device not configured
        throw new TestAbortedWithImportantMessageException(
            MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
            "(debugging) System does not support writing to shared memory objects via FileOutputStream",
            e);
      }
      assertEquals(1, ms.get(OfByte.JAVA_BYTE, 0));
    }
  }

  @Test
  public void testWriteFileChannel() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(64)) {
      try (FileChannel fc = mem.asMappableFileChannel()) {
        fc.write(ByteBuffer.wrap("Hello World".getBytes(StandardCharsets.UTF_8))); // please don't
      } catch (IOException e) {
        // macOS: Device not configured
      }
    } catch (UnsupportedOperationException e) {
      throw new TestAbortedWithImportantMessageException(
          MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
          "(debugging) System does not support SharedMemory#asMappableFileChannel", e);
    }
  }

  @Test
  public void testMMapWithDuplicates() throws Exception {
    testMMapWithDuplicates(Arena.ofShared(), true); // closing the Arena prevents memory access
    testMMapWithDuplicates(Arena.global(), false); // cannot use Arena.global in try-finally
  }

  private void testMMapWithDuplicates(Arena arena, boolean closeArena) throws Exception {
    boolean notMarkedAsMapped = false;

    MemorySegment ms;
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      long memSizeFC = mem.byteSize();
      assertNotEquals(0, memSizeFC);

      ms = mem.asMappedMemorySegment(MapMode.READ_WRITE, arena, 2);
      long memSize = ms.byteSize() / 3;

      ms.setString(0, "Hello World");
      assertEquals("Hello World", ms.getString(0));
      assertEquals("Hello World", ms.getString(1 * memSize));
      assertEquals("Hello World", ms.getString(2 * memSize));

      // mmap-specific calls should succeed; these rely on Buffer having a "fd" field.
      // However, shared memory on Windows does not have a file descriptor, therefore we
      // need to guard this.
      if (ms.isMapped()) {
        ms.force();
        ms.load();
        ms.isLoaded();
        ms.unload();
      } else {
        notMarkedAsMapped = true;
      }

      if (closeArena) {
        arena.close();
      }

      // automatic unmap-upon-close relies on Buffer having a "segment" field that we can populate
      assertNotEquals(closeArena, ms.scope().isAlive());

      if (ms.scope().isAlive()) {
        // global scope
        assertEquals("Hello World", ms.getString(0));
      } else {
        assertThrows(IllegalStateException.class, () -> ms.getString(0)); // already closed
      }

      if (notMarkedAsMapped) {
        throw new TestAbortedWithImportantMessageException(
            MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
            "SharedMemory MemorySegments are not considered #isMapped() on this platform");
      }
    }
  }

  @Test
  public void testMMapReadOnly() throws Exception {
    try (SharedMemory mem1 = SharedMemory.createAnonymous(16);
        SharedMemory mem2 = SharedMemory.using(mem1.getFileDescriptor());) {
      MemorySegment ms1 = mem1.asMappedMemorySegment(MapMode.READ_WRITE);
      MemorySegment ms2 = mem2.asMappedMemorySegment(MapMode.READ_ONLY);

      assertEquals("", ms2.getString(0));
      ms1.setString(0, "Hello");
      assertEquals("Hello", ms2.getString(0));

      assertThrows(IllegalArgumentException.class, () -> ms2.setString(0, "World"));
      assertEquals("Hello", ms1.getString(0));
      assertEquals("Hello", ms2.getString(0));
    }
  }

  @Test
  public void testMMapAfterClose() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      mem.close();
      assertThrows(ClosedChannelException.class, () -> mem.asMappedMemorySegment(
          MapMode.READ_WRITE));
    }
  }

  @Test
  public void testMMapAccessAfterClose() throws Exception {
    MemorySegment ms;
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      ms = mem.asMappedMemorySegment(MapMode.READ_WRITE, (Arena) null);
      ms.setString(0, "Hello World :-)");
    }

    // SharedMemory is closed, but we're accessing its MemorySegment. This is not correct.
    // An IllegalStateException is thrown because the arena created for our SharedMemory instance
    // has been closed, and MemorySegment knows that.
    assertThrows(IllegalStateException.class, () -> ms.getString(0));
  }

  @Test
  public void testCreateAnonymousZeroMmap() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(0)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE, (Arena) null);
      assertEquals(SharedMemory.defaultAllocationSize(), ms.byteSize());
    } catch (IOException e) {
      // may also be thrown here (Windows)
    }
  }

  @Test
  public void testMMapAccessAfterCloseGlobalArena() throws Exception {
    MemorySegment ms;
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      ms = mem.asMappedMemorySegment(MapMode.READ_WRITE, Arena.global());
      ms.setString(0, "Hello World :-)");
    }

    // SharedMemory is closed, but we're accessing its MemorySegment. This is not correct.
    try {
      assertEquals("", ms.getString(0));
    } catch (InternalError e) {
      // An InternalError may be thrown due to a page fault / access error, but at least the VM
      // doesn't
      // crash.
      // This is because we may run an additional mmap with PROT_NONE upon our custom
      // "MADV_FREE_NOW"
    }
  }

  @SuppressWarnings("PMD.DoNotCallGarbageCollectionExplicitly")
  @SuppressFBWarnings("DM_GC")
  private static void runGc() {
    Runtime.getRuntime().gc();
  }

  @Test
  @ExecutionEnvironmentRequirement(epsilonGC = Rule.PROHIBITED)
  public void testMMapThenAccessAfterGC() throws Exception {
    CompletableFuture<MemorySegment> ms = CompletableFuture.supplyAsync(() -> {
      try {
        SharedMemory mem = SharedMemory.createAnonymous(16);
        return mem.asMappedMemorySegment(MapMode.READ_WRITE);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    });

    CompletableFuture<IllegalStateException> gotException = new CompletableFuture<>();

    try {
      // Try to access MemorySegment after GC
      new Thread(() -> {
        for (int i = 0; i < 500; i++) { // 500 * 10ms = 5 seconds
          try {
            runGc();
            ms.get().getString(0); // should throw IllegalStateException
          } catch (IllegalStateException e) { // expected
            gotException.complete(e);
            break;
          } catch (Exception e) {
            fail(e);
            break;
          }
          // if we reach this, keep trying -- there may be a slight delay between OOME and actually
          // calling the cleaner
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            break;
          }
        }
        gotException.complete(null); // unexpected

        assertNotNull(gotException);
      }).start();

      // probably not necessary:
      // GCUtil.addBrutalGCPressure(gotException::isDone);

      assertNotNull(gotException.get(5, TimeUnit.SECONDS),
          "Should have thrown some IllegalStateException");
    } finally {
      gotException.complete(null); // unblock OOME thread
    }
  }

  @Test
  public void testUnlink() throws Exception {
    // trying to remove a non-existant or already deleted entry should not throw an exception
    SharedMemory.unlinkShared("jux.unlink");
    SharedMemory.unlinkShared("jux.unlink");
  }

  @Test
  public void testOpenExisting() throws Exception {
    String name = "juxoe";
    String unrelated = "juxoeU";
    SharedMemory.unlinkShared(name);
    SharedMemory.unlinkShared(unrelated);
    try (SharedMemory mem1 = SharedMemory.createOrReuseExisting(name, 64)) {
      MemorySegment seg1 = mem1.asMappedMemorySegment(MapMode.READ_WRITE);
      assertEquals("", seg1.getString(0));
      seg1.setString(0, "Hello");
      assertEquals("Hello", seg1.getString(0));

      try (SharedMemory memUnrelated = SharedMemory.createOrOpenExisting(unrelated, 64)) {
        MemorySegment segUnrelated = memUnrelated.asMappedMemorySegment(MapMode.READ_WRITE);
        assertEquals("", segUnrelated.getString(0));
        segUnrelated.setString(0, "unrelated");
      }

      try (SharedMemory mem2 = SharedMemory.createOrOpenExisting(name, 64)) {
        MemorySegment seg2 = mem2.asMappedMemorySegment(MapMode.READ_WRITE);
        assertEquals("Hello", seg2.getString(0));
        seg2.setString(0, "World");
      }

      assertEquals("World", seg1.getString(0));
    } finally {
      SharedMemory.unlinkShared(name);
      SharedMemory.unlinkShared(unrelated);
    }
  }

  @Test
  public void testOpen() throws Exception {
    String name = "juxo";
    SharedMemory.unlinkShared(name);
    try (SharedMemory mem1 = SharedMemory.createExclusively(name, 64)) {
      MemorySegment seg1 = mem1.asMappedMemorySegment(MapMode.READ_WRITE);
      seg1.setString(0, "Hello");

      try (SharedMemory mem2 = SharedMemory.openExisting(name)) {
        MemorySegment seg2 = mem2.asMappedMemorySegment(MapMode.READ_WRITE);
        assertEquals("Hello", seg2.getString(0));
      }
    }
  }

  @Test
  public void testreateOrOpen() throws Exception {
    String name = "juxco";
    SharedMemory.unlinkShared(name);
    try (SharedMemory mem1 = SharedMemory.createExclusively(name, 64)) {
      MemorySegment seg1 = mem1.asMappedMemorySegment(MapMode.READ_WRITE);
      seg1.setString(0, "Hello");

      try (SharedMemory mem2 = SharedMemory.createOrOpenExisting(name, 64)) {
        MemorySegment seg2 = mem2.asMappedMemorySegment(MapMode.READ_WRITE);
        assertEquals("Hello", seg2.getString(0));
      }
    }
  }

  @Test
  public void testUnlinkOpen() throws Exception {
    String name = "juxuo";
    SharedMemory.unlinkShared(name);
    try (SharedMemory mem1 = SharedMemory.createExclusively(name, 3 * 64 * 1024)) {
      MemorySegment seg1 = mem1.asMappedMemorySegment(MapMode.READ_WRITE);
      seg1.setString(0, "Hello");

      SharedMemory.unlinkShared(name);
      try (SharedMemory mem2 = SharedMemory.createOrOpenExisting(name, 64)) {
        MemorySegment seg2 = mem2.asMappedMemorySegment(MapMode.READ_WRITE);
        assertEquals("", seg2.getString(0));
      }
    }
  }

  @Test
  public void testFutex() throws Exception {
    testFutex(false, false);
  }

  @Test
  public void testFutexCloseSharedMemory() throws Exception {
    testFutex(true, true);
  }

  @Test
  public void testFutexCloseSharedMemoryFail() throws Exception {
    testFutex(true, false);
  }

  @Test
  public void testFutexAlignmentAndLength() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

      mem.futex(ms.asSlice(0, SharedMemory.FUTEX32_SEGMENT_SIZE)); // OK
      assertThrows(IOException.class, () -> mem.futex(ms.asSlice(1,
          SharedMemory.FUTEX32_SEGMENT_SIZE)));
      assertThrows(IOException.class, () -> mem.futex(ms.asSlice(2,
          SharedMemory.FUTEX32_SEGMENT_SIZE)));
      assertThrows(IOException.class, () -> mem.futex(ms.asSlice(3,
          SharedMemory.FUTEX32_SEGMENT_SIZE)));
      mem.futex(ms.asSlice(4, SharedMemory.FUTEX32_SEGMENT_SIZE)); // OK

      // slice must be 4 bytes long
      assertThrows(IOException.class, () -> mem.futex(ms.asSlice(0, 0)));
      assertThrows(IOException.class, () -> mem.futex(ms.asSlice(0, 1)));
      assertThrows(IOException.class, () -> mem.futex(ms.asSlice(0, 5)));
      assertThrows(IOException.class, () -> mem.futex(ms.asSlice(0, 8)));
    }
  }

  @Test
  public void testFutexIs32Bit() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

      ms.setAtIndex(OfInt.JAVA_INT, 1, 0xCAFEBABE);
      assertNotEquals(0, ms.get(OfLong.JAVA_LONG, 0));

      // the bytes after the first 4 should not affect tryWait
      long elapsed = System.currentTimeMillis();
      assertFalse(mem.futex(ms.asSlice(0, SharedMemory.FUTEX32_SEGMENT_SIZE)).tryWait(0,
          FUTEX32BIT_CHECK_WAIT_TIME));
      elapsed = System.currentTimeMillis() - elapsed;
      if (elapsed < FUTEX32BIT_CHECK_WAIT_TIME) {
        // If tryWait returns before the expected waitTime, then we can assume that it
        // somehow took more than the first 32-bit into consideration, and decided that the
        // "ifValue" was not the expected one.
        fail("The futex should have waited at least " + FUTEX32BIT_CHECK_WAIT_TIME + "ms");
      }
    } catch (UnsupportedOperationException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          "Futexes are not supported on your platform", e);
    }
  }

  @Test
  public void testFutexReadOnlySegment() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_ONLY);

      assertThrows(IOException.class, () -> mem.futex(ms));
    }
  }

  @Test
  @ForkedVMRequirement(forkSupported = true)
  public void testFutexSeparateVM() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(FORKEDVM_FUTEX_SIZE)) {
      ForkedVM vm = new ForkedVM(FutexTestApp.class);

      CompletableFuture<@Nullable IOException> cf = CompletableFuture.supplyAsync(() -> {
        try {
          MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

          int waitRemaining = 5000;
          long now = System.currentTimeMillis();

          try (Futex futex = mem.futex(ms.asSlice(0, 4))) {
            do {
              if (!futex.tryWait(0, waitRemaining)) {
                waitRemaining -= (int) (System.currentTimeMillis() - now);
                if (waitRemaining <= 0) {
                  return new IOException("Timeout");
                }
              } else {
                return null;
              }
            } while (true); // NOPMD
          }
        } catch (IOException e) {
          return e;
        }
      });

      Redirect fdRedirect = FileDescriptorCast.using(mem.getFileDescriptor()).as(Redirect.class);
      vm.setRedirectInput(fdRedirect);
      vm.setRedirectError(Redirect.INHERIT);
      vm.setRedirectOutput(Redirect.INHERIT);

      Process p = vm.fork();

      IOException exc;
      try {
        exc = cf.get(5, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof UnsupportedOperationException) {
          throw new TestAbortedWithImportantMessageException(
              MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
              "Futexes are not supported on your platform", e);
        }
        throw e;
      }
      if (exc != null) {
        throw exc;
      }

      assertEquals(0, p.waitFor(), "Forked VM should have terminated successfully");
    }
  }

  private void testFutex(boolean closeMem, boolean wakeUp) throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(16)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

      try (Futex futex = mem.futex(ms.asSlice(4, 4), wakeUp)) {
        assertTrue(futex.tryWait(0xDEADBEEF, 0)); // immediately returns; value doesn't match

        long time = System.currentTimeMillis();
        assertFalse(futex.tryWait(0, 10)); // waits because value is 0, then times out after 10ms
        time = System.currentTimeMillis() - time;
        assertTrue(time >= 10, "Should have waited for at least 10 milliseconds");

        AtomicBoolean tryWaitComplete = new AtomicBoolean(false);
        Semaphore cfReady = new Semaphore(0);
        CompletableFuture<Throwable> cf = CompletableFuture.supplyAsync(() -> {
          try {
            cfReady.release();

            if (closeMem) {
              mem.close(); // effective when we take care of waking all remaining Futexes with
                           // SharedMemory#futex(MemorySegment, true)
            } else {
              // We may try to wake before a wait was registered, so let's keep trying until we
              // actually wake up some thread
              assertTrue(futex.tryWakeWithTimeout(false, 5000, 10, () -> !tryWaitComplete.get()));
            }
          } catch (Throwable e) { // NOPMD.AvoidCatchingThrowable
            return e;
          }
          return null;
        });
        cfReady.acquire(); // wait until thread is running (to improve the stability of the test)

        if (closeMem && !wakeUp) {
          // use a shorter test interval since it's going to fail anyways
          boolean success = futex.tryWait(0, 50);
          tryWaitComplete.set(true);
          assertFalse(success);
        } else {
          // Wait may fail because we closed the memory
          boolean success = futex.tryWait(0, 5000) || futex.isClosed();
          tryWaitComplete.set(true);
          assertTrue(success);
        }

        Throwable t = cf.get();
        if (t != null) {
          if (t instanceof Exception) {
            throw (Exception) t;
          } else {
            throw (Error) t;
          }
        }
      }
    } catch (UnsupportedOperationException e) {
      throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_SHORT_WITH_ISSUES,
          "Futexes are not supported on your platform", e);
    }
  }

  @Test
  public void testTwoAnonymous() throws Exception {
    try (SharedMemory mem1 = SharedMemory.createAnonymous(64);
        SharedMemory mem2 = SharedMemory.createAnonymous(64)) {
      MemorySegment seg1 = mem1.asMappedMemorySegment(MapMode.READ_WRITE);
      MemorySegment seg2 = mem2.asMappedMemorySegment(MapMode.READ_WRITE);

      seg1.set(OfByte.JAVA_INT, 0, 12);
      assertEquals(12, seg1.get(OfByte.JAVA_INT, 0));
      assertEquals(0, seg2.get(OfByte.JAVA_INT, 0));
      seg2.set(OfByte.JAVA_INT, 0, 34);
      assertEquals(12, seg1.get(OfByte.JAVA_INT, 0));
      assertEquals(34, seg2.get(OfByte.JAVA_INT, 0));
    }
  }

  @Test
  public void testSecret() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(64, SharedMemoryOption.SECRET)) {
      MemorySegment seg = mem.asMappedMemorySegment(MapMode.READ_WRITE);
      assertEquals(0, seg.get(OfByte.JAVA_INT, 0));
      seg.set(OfByte.JAVA_INT, 0, 99);
      assertEquals(99, seg.get(OfByte.JAVA_INT, 0));
    } catch (OperationNotSupportedIOException e) {
      // acceptable
      throw new TestAbortedNotAnIssueException("memfd_secret not supported/enabled in kernel", e);
    }
  }

  // @Test
  public void testFutexIsInterProcess() throws Exception { // NOPMD
    try (SharedMemory mem = SharedMemory.createAnonymous(8)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

      try (Futex futex = mem.futex(ms.asSlice(0, SharedMemory.FUTEX32_SEGMENT_SIZE))) {
        if (!futex.isInterProcess()) {
          throw new TestAbortedWithImportantMessageException(
              MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
              "On this system, SharedMemory Futexes cannot be shared between processes");
        }
      }
    }
  }

  @Test
  public void testMutexIsInterProcess() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(8)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

      try (SharedMutex mutex = mem.mutex(ms.asSlice(0, SharedMemory.MUTEX_SEGMENT_SIZE))) {
        if (!mutex.isInterProcess()) {
          throw new TestAbortedWithImportantMessageException(
              MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
              "On this system, SharedMemory Mutexes cannot be shared between processes");
        }
      }
    }
  }

  @Test
  public void testMutex() throws Exception {
    try (SharedMemory mem = SharedMemory.createAnonymous(8)) {
      MemorySegment ms = mem.asMappedMemorySegment(MapMode.READ_WRITE);

      try (SharedMutex mutex = mem.mutex(ms.asSlice(0, SharedMemory.MUTEX_SEGMENT_SIZE))) {
        assertTrue(mutex.tryLock(1));
        assertFalse(mutex.tryLock(1));
        mutex.unlock();
        assertTrue(mutex.tryLock(1));
        mutex.unlock();
        mutex.unlock();
        mutex.unlock();
        mutex.unlock();
        mutex.unlock();
        assertTrue(mutex.tryLock(1));
      }
    }
  }

  @Test
  public void testOSAdvisory() throws Exception {
    String osName = System.getProperty("os.name", "");
    if ("z/OS".equals(osName)) {
      throw new TestAbortedWithImportantMessageException(
          MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
          "junixsocket-memory has not been tested on z/OS, due to the lack of availability of a "
              + "Java 22 environment. Please file a ticket at "
              + "https://github.com/kohlschutter/junixsocket/issues with your selftest results, this "
              + "will be tremendously useful. Thank you!");
    }
  }
}
