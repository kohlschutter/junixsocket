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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;

import com.kohlschutter.testutil.TestAbortedNotAnIssueException;
import com.kohlschutter.util.IOUtil;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class FileDescriptorCastTest {
  // CPD-OFF

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

    // Below we see code that may or may not block indefinitely due to system constraints
    // There's not much we can do other than not run it (it doesn't check state anyhow).

    // try {
    // assertTimeoutPreemptively(Duration.ofMillis(100), () -> {
    // try {
    // fdc.as(InputStream.class).read();
    // } catch (IOException e) {
    // // expected, but not guaranteed (Linux won't throw this); ignore
    // }
    // });
    // } catch (AssertionFailedError e) {
    // // on Linux, we timeout, and that's OK, too.
    // }
  }

  @Test
  public void testCastAsInteger() throws Exception {
    assertNotEquals(-1, FileDescriptorCast.using(FileDescriptor.in).as(Integer.class));
    assertNotEquals(-1, FileDescriptorCast.using(FileDescriptor.out).as(Integer.class));
    assertNotEquals(-1, FileDescriptorCast.using(FileDescriptor.err).as(Integer.class));

    assertThrows(IOException.class, () -> FileDescriptorCast.using(new FileDescriptor()).as(
        Integer.class));
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNSAFE)
  public void testUnsafeCast() throws Exception {
    assertEquals(1, FileDescriptorCast.unsafeUsing(1).as(Integer.class));
    assertEquals(-2, FileDescriptorCast.unsafeUsing(-2).as(Integer.class));
    assertSame(FileDescriptor.out, FileDescriptorCast.unsafeUsing(FileDescriptorCast.using(
        FileDescriptor.out).as(Integer.class)).as(FileDescriptor.class));
    assertThrows(IOException.class, () -> FileDescriptorCast.unsafeUsing(-1).as(Integer.class));
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

  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
  @Test
  public void testCastGeneric() throws Exception {
    try (AFUNIXServerSocketChannel ussc = AFUNIXServerSocketChannel.open()) {
      ussc.bind(AFUNIXSocketAddress.ofNewTempFile());

      AFGenericServerSocketChannel gssc = FileDescriptorCast.using(ussc.getFileDescriptor()).as(
          AFGenericServerSocketChannel.class);

      CompletableFuture<@Nullable ConnectionResult> cf = CompletableFuture.supplyAsync(() -> {
        try {
          AFGenericSocketChannel sc = gssc.accept();
          ByteBuffer bb = ByteBuffer.allocate(64);
          int r = sc.read(bb);
          if (r != 1) {
            throw new IllegalStateException("Unexpected result: " + r + " bytes read");
          }

          return new ConnectionResult(bb.get(0), sc.getLocalSocketAddress(), sc
              .getRemoteSocketAddress());
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      });

      try (AFUNIXSocket sock = AFUNIXSocket.connectTo(ussc.getLocalAddress())) {
        AFGenericSocket gs = FileDescriptorCast.using(sock.getFileDescriptor()).as(
            AFGenericSocket.class);

        try {
          gs.getOutputStream().write(123);
        } catch (BrokenPipeSocketException e) {
          try {
            cf.get();
          } catch (Exception e2) {
            e.addSuppressed(e2);
          }
          throw e;
        }

        AFGenericSocketAddress lsa = gs.getLocalSocketAddress();
        if (lsa != null) {
          assertEquals(AFGenericSocketAddress.class, lsa.getClass());
        }
        AFGenericSocketAddress rsa = gs.getRemoteSocketAddress();
        if (rsa != null) {
          assertEquals(AFGenericSocketAddress.class, rsa.getClass());
        }

        ConnectionResult cr = Objects.requireNonNull(cf.get());
        assertEquals(123, cr.firstByte);
        if (lsa != null) {
          compareGenericAddresses(lsa, cr.remoteSocketAddress);
        }
        if (rsa != null) {
          compareGenericAddresses(rsa, cr.localSocketAddress);
        }
      }
    }
  }

  private void compareGenericAddresses(AFGenericSocketAddress rsa, SocketAddress lsa) {
    if (rsa.toBytes().length < 2 && !rsa.equals(lsa)) {
      // observed on AIX
      // this is more or less equivalent to rsa == null
    } else {
      if (!rsa.equals(lsa) && lsa instanceof AFGenericSocketAddress) {
        // On Windows, we may get the same address back but padding bytes
        // may be omitted. Therefore, just compare the common prefix.

        byte[] bytesRemote = rsa.getBytes();
        byte[] bytesLocal = ((AFGenericSocketAddress) lsa).getBytes();
        int minLength = Math.min(bytesRemote.length, bytesLocal.length);

        boolean ok = true;
        for (int i = 0; i < minLength; i++) {
          if (bytesRemote[i] != bytesLocal[i]) {
            ok = false;
            break;
          }
        }
        if (!ok) {
          assertEquals(rsa, lsa);
        }
      } else {
        assertEquals(rsa, lsa);
      }
    }
  }

  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
  @Test
  public void testCastGenericDuplicating() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();
    Path p = addr.getFile().toPath();
    try (AFUNIXServerSocketChannel ussc = AFUNIXServerSocketChannel.open()) {
      ussc.bind(addr);

      FileDescriptorCast fdc = FileDescriptorCast.duplicating(ussc.getFileDescriptor());
      if (fdc == null) {
        throw new TestAbortedNotAnIssueException("FileDescriptCast.duplicating not supported");
      }

      // also won't delete the file because we told to it not delete above, otherwise
      // we would be able to bind but not connect (this is an AF_UNIX-specific issue)

      AFGenericServerSocketChannel gssc = fdc.as(AFGenericServerSocketChannel.class);

      CompletableFuture<@Nullable ConnectionResult> cf = CompletableFuture.supplyAsync(() -> {
        try {
          AFGenericSocketChannel sc = gssc.accept();
          ByteBuffer bb = ByteBuffer.allocate(64);
          int r = sc.read(bb);
          if (r != 1) {
            throw new IllegalStateException("Unexpected result: " + r + " bytes read");
          }

          return new ConnectionResult(bb.get(0), sc.getLocalSocketAddress(), sc
              .getRemoteSocketAddress());
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      });

      try (AFUNIXSocket sock = AFUNIXSocket.connectTo(ussc.getLocalAddress())) {
        AFGenericSocket gs = FileDescriptorCast.using(sock.getFileDescriptor()).as(
            AFGenericSocket.class);

        try {
          gs.getOutputStream().write(123);
        } catch (BrokenPipeSocketException e) {
          try {
            cf.get();
          } catch (Exception e2) {
            e.addSuppressed(e2);
          }
          throw e;
        }

        AFGenericSocketAddress lsa = gs.getLocalSocketAddress();
        if (lsa != null) {
          assertEquals(AFGenericSocketAddress.class, lsa.getClass());
        }
        AFGenericSocketAddress rsa = gs.getRemoteSocketAddress();
        if (rsa != null) {
          assertEquals(AFGenericSocketAddress.class, rsa.getClass());
        }

        ConnectionResult cr = Objects.requireNonNull(cf.get());
        assertEquals(123, cr.firstByte);
        if (lsa != null) {
          compareGenericAddresses(lsa, cr.remoteSocketAddress);
        }
        if (rsa != null) {
          compareGenericAddresses(rsa, cr.localSocketAddress);
        }
      }
    } finally {
      Files.deleteIfExists(p);
    }
  }

  private static final class ConnectionResult {
    private final int firstByte;
    private final SocketAddress localSocketAddress;
    private final SocketAddress remoteSocketAddress;

    public ConnectionResult(int firstByte, AFGenericSocketAddress localSocketAddress,
        AFGenericSocketAddress remoteSocketAddress) {
      this.firstByte = firstByte;
      this.localSocketAddress = localSocketAddress;
      this.remoteSocketAddress = remoteSocketAddress;
    }
  }

  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
  @Test
  public void testCastToServerSocketIsSameType() throws Exception {
    AFUNIXServerSocketChannel ussc = AFUNIXServerSocketChannel.open();
    ussc.bind(AFUNIXSocketAddress.ofNewTempFile());

    ServerSocketChannel ssc = FileDescriptorCast.using(ussc.getFileDescriptor()).as(
        ServerSocketChannel.class);
    assertEquals(AFUNIXServerSocketChannel.class, ssc.getClass());

    AFUNIXSocket us = AFUNIXSocket.connectTo(ussc.getLocalAddress());
    Socket s = FileDescriptorCast.using(us.getFileDescriptor()).as(Socket.class);
    assertEquals(AFUNIXSocket.class, s.getClass());
  }
}
