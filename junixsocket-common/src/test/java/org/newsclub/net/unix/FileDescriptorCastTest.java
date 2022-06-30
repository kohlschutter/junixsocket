/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.util.IOUtil;

@SuppressWarnings("PMD.CouplingBetweenObjects")
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public class FileDescriptorCastTest {
  @Test
  public void testInvalidFileDescriptor() throws IOException {
    assertThrows(IOException.class, () -> FileDescriptorCast.using(new FileDescriptor()));
    assertThrows(NullPointerException.class, () -> FileDescriptorCast.using(null));
    assertThrows(ClassCastException.class, () -> FileDescriptorCast.using(FileDescriptor.out).as(
        Runtime.class));
  }

  @Test
  public void testAvailableTypes() throws IOException {
    FileDescriptorCast fdc = FileDescriptorCast.using(FileDescriptor.out);
    Set<Class<?>> availableTypes = fdc.availableTypes();
    assertTrue(availableTypes.contains(OutputStream.class));
    assertTrue(fdc.isAvailable(OutputStream.class));
    assertFalse(fdc.isAvailable(System.class));
  }

  @Test
  public void testStdout() throws IOException {
    FileDescriptorCast fdc = FileDescriptorCast.using(FileDescriptor.out);

    assertThrows(ClassCastException.class, () -> fdc.as(Socket.class));
    assertThrows(ClassCastException.class, () -> fdc.as(AFUNIXSocket.class));

    assertThrows(NullPointerException.class, () -> fdc.as(null));

    assertEquals(fdc.getFileDescriptor(), fdc.as(Object.class));
    assertEquals(fdc.getFileDescriptor(), fdc.as(FileDescriptor.class));

    assertEquals(fdc.as(OutputStream.class).getClass(), fdc.as(FileOutputStream.class).getClass());

    // We can cast this file descriptor to an InputStream, but read access will fail
    assertEquals(fdc.as(InputStream.class).getClass(), fdc.as(FileInputStream.class).getClass());

    try {
      assertTimeoutPreemptively(Duration.ofMillis(100), () -> {
        try {
          fdc.as(InputStream.class).read();
        } catch (IOException e) {
          // expected, but not guaranteed (Linux won't throw this); ignore
        }
      });
    } catch (AssertionFailedError e) {
      // on Linux, we timeout, and that's OK, too.
    }
  }

  @Test
  public void testRandomAccessFile() throws Exception {
    File tempFile = AFUNIXSocketAddress.newTempPath(false);
    try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw");
        FileChannel fc = FileDescriptorCast.using(raf.getFD()).as(FileChannel.class)) {
      ByteBuffer bb = ByteBuffer.allocate(4); // big endian
      bb.putInt(0x12345678);
      bb.flip();
      fc.write(bb);

      assertEquals(4, raf.length());
      assertEquals(4, raf.getFilePointer());

      raf.seek(2);
      assertEquals(2, fc.position());
      bb.clear();
      assertEquals(2, fc.read(bb));
      assertEquals(0x5678, bb.getShort());
    } finally {
      IOUtil.delete(tempFile);
    }
  }

  @Test
  public void testPipe() throws Exception {
    try (AFPipe pipe = AFPipe.open();
        AFPipe.SourceChannel source = pipe.source();
        AFPipe.SinkChannel sink = pipe.sink();) {

      ByteBuffer buf = ByteBuffer.allocate(32);
      buf.order(ByteOrder.LITTLE_ENDIAN);
      buf.putInt(0xeeff);
      buf.flip();

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        sink.write(buf);

        OutputStream badOut = FileDescriptorCast.using(source.getFileDescriptor()).as(
            OutputStream.class);
        try {
          badOut.write(0x22);
        } catch (IOException e) {
          // expected but not guaranteed to be thrown
        }

        FileDescriptorCast fdc = FileDescriptorCast.using(source.getFileDescriptor());
        InputStream in = fdc.as(InputStream.class);

        int available = in.available();
        if (available != 0) {
          assertEquals(4, available);
        }
        assertEquals(0xFF, in.read());
        assertEquals(0xEE, in.read());
        assertEquals(0, in.read());
        assertEquals(0, in.read());

        OutputStream out = FileDescriptorCast.using(sink.getFileDescriptor()).as(
            OutputStream.class);
        out.write(0x22);
        out.write(0x33);

        buf.clear();
        int numRead = source.read(buf);
        assertEquals(2, numRead);
        buf.flip();
        assertEquals(0x3322, buf.getShort());
      });
    }
  }
}
