/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.kohlschutter.util.IOUtil;

public class FileDescriptorCastTest {
  @Test
  public void testInvalidFileDescriptor() throws IOException {
    assertThrows(IOException.class, () -> FileDescriptorCast.using(new FileDescriptor()));
    assertThrows(NullPointerException.class, () -> FileDescriptorCast.using(null));
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
    try (AFUNIXPipe pipe = AFUNIXPipe.open();
        AFUNIXPipe.SourceChannel source = pipe.source();
        AFUNIXPipe.SinkChannel sink = pipe.sink();) {

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
        assertEquals(4, in.available());
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

  @Test
  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR)
  public void testSocketPair() throws Exception {
    AFUNIXSocketPair<AFUNIXSocketChannel> socketPair = AFUNIXSocketPair.open();
    AFUNIXSocketChannel sock1chan = socketPair.getSocket1();
    FileDescriptor sock2fd = socketPair.getSocket2().getFileDescriptor();

    assertTrue(sock1chan.isConnected());

    FileDescriptorCast fdc = FileDescriptorCast.using(sock2fd);

    // Limitation: When we emulate socket pairs through non AF_UNIX sockets, we currently do not
    // support casting to Socket.class
    assertEquals(AFUNIXSocket.supports(AFUNIXSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR), fdc
        .isAvailable(Socket.class));

    if (!AFUNIXSocket.supports(AFUNIXSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR)) {
      FileDescriptorCast.using(sock2fd).as(FileChannel.class);
    } else {
      Socket sock2 = FileDescriptorCast.using(sock2fd).as(Socket.class);
      assertEquals(AFUNIXSocket.class, sock2.getClass());
      assertTrue(sock2.isConnected());
      assertEquals(sock2fd, ((AFUNIXSocket) sock2).getFileDescriptor());
    }
  }

  @Test
  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR)
  public void testSocketPairNative() throws Exception {
    AFUNIXSocketPair<AFUNIXSocketChannel> socketPair = AFUNIXSocketPair.open();
    AFUNIXSocketChannel sock1chan = socketPair.getSocket1();
    FileDescriptor sock2fd = socketPair.getSocket2().getFileDescriptor();

    assertTrue(sock1chan.isConnected());

    AFUNIXSocketChannel sock2chan = FileDescriptorCast.using(sock2fd).as(AFUNIXSocketChannel.class);
    assertEquals(sock2fd, sock2chan.getFileDescriptor());
    assertTrue(sock2chan.isConnected());

    ByteBuffer bb = ByteBuffer.allocate(32);
    bb.putInt(0x12345678);
    bb.flip();
    sock1chan.write(bb);
    bb.clear();

    assertEquals(4, sock2chan.read(bb));
    bb.flip();
    assertEquals(0x12345678, bb.getInt());
  }

  @Test
  public void testUnconnectedServerAsSocket() throws Exception {
    try (AFUNIXServerSocket ass = AFUNIXServerSocket.bindOn(AFUNIXSocketAddress.ofNewTempFile(),
        true);
        AFUNIXServerSocket ass2 = AFUNIXServerSocket.bindOn(AFUNIXSocketAddress.ofNewTempFile(),
            true)) {
      FileDescriptor fd = ass.getFileDescriptor();
      assertTrue(fd.valid());

      AFUNIXSocket sock = FileDescriptorCast.using(fd).as(AFUNIXSocket.class);
      assertTrue(sock.isBound());
      assertFalse(sock.isConnected());

      // We can cast a server socket (which is listening by default) to a socket, but we can't
      // connect

      assertThrows(SocketException.class, () -> sock.connect(ass2.getLocalSocketAddress()));
      assertThrows(SocketException.class, () -> sock.connect(ass.getLocalSocketAddress()));
      assertThrows(SocketException.class, () -> sock.getInputStream().read());

      assertNull(sock.getRemoteSocketAddress());
      assertFalse(sock.isConnected());
    }
  }

  @Test
  public void testServer() throws Exception {
    AFUNIXSocketAddress serverAddress = AFUNIXSocketAddress.ofNewTempFile();
    try (AFUNIXServerSocket ass = AFUNIXServerSocket.bindOn(serverAddress, true);
        AFUNIXSocket sock = AFUNIXSocket.connectTo(serverAddress)) {
      AFUNIXSocket sock1 = FileDescriptorCast.using(sock.getFileDescriptor()).as(
          AFUNIXSocket.class);
      assertEquals(sock.isBound(), sock1.isBound());
      assertEquals(sock.isConnected(), sock1.isConnected());
      assertTrue(sock1.isConnected());
      assertEquals(sock.getRemoteSocketAddress(), sock1.getRemoteSocketAddress());
      assertEquals(ass.getLocalSocketAddress(), sock.getRemoteSocketAddress());

      AFUNIXServerSocket serverSock = FileDescriptorCast.using(ass.getFileDescriptor()).as(
          AFUNIXServerSocket.class);
      AFUNIXSocket sock0 = serverSock.accept();
      assertEquals(ass.getLocalSocketAddress(), sock0.getLocalSocketAddress());

      sock0.getOutputStream().write(23);
      assertEquals(23, sock1.getInputStream().read());
    }
  }

  @Test
  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_DATAGRAMS)
  public void testDatagramSocket() throws Exception {
    AFUNIXSocketAddress addr1 = AFUNIXSocketAddress.ofNewTempFile();
    AFUNIXSocketAddress addr2 = AFUNIXSocketAddress.ofNewTempFile();
    try (AFUNIXDatagramChannel dc1 = AFUNIXDatagramChannel.open();
        AFUNIXDatagramChannel dc2 = AFUNIXDatagramChannel.open()) {
      dc1.setDeleteOnClose(true);
      dc2.setDeleteOnClose(true);
      ByteBuffer bb = ByteBuffer.allocate(32);
      bb.putInt(1234);
      bb.flip();

      dc1.getOption(StandardSocketOptions.SO_SNDBUF);

      AFUNIXDatagramChannel dc1c = FileDescriptorCast.using(dc1.getFileDescriptor()).as(
          AFUNIXDatagramChannel.class);
      AFUNIXDatagramChannel dc2c = FileDescriptorCast.using(dc2.getFileDescriptor()).as(
          AFUNIXDatagramChannel.class);

      assertFalse(dc1.isBound());
      assertFalse(dc1c.isBound());
      assertFalse(dc2.isBound());
      assertFalse(dc2c.isBound());
      dc1.bind(addr1);
      dc2.bind(addr2);
      assertTrue(dc1.isBound());
      assertTrue(dc1c.isBound());
      assertTrue(dc2.isBound());
      assertTrue(dc2c.isBound());

      assertEquals(dc1.getLocalAddress(), dc1c.getLocalAddress());
      assertEquals(dc2.getLocalAddress(), dc2c.getLocalAddress());

      dc1c.connect(addr2);
      dc2c.connect(addr1);
      assertTrue(dc1c.isConnected());
      assertTrue(dc1.isConnected());

      assertEquals(dc1.getLocalAddress(), dc2c.getRemoteAddress());
      assertEquals(dc1.getRemoteAddress(), dc2c.getLocalAddress());
      assertEquals(dc1c.getLocalAddress(), dc2.getRemoteAddress());
      assertEquals(dc1c.getRemoteAddress(), dc2.getLocalAddress());

      assertEquals(4, dc1c.write(bb));
      bb.clear();
      assertEquals(4, dc2c.read(bb));
      bb.flip();
      assertEquals(1234, bb.getInt());
    }
  }

  @Test
  public void testSocketPorts() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempPath(123);
    try (AFUNIXServerSocket ass = AFUNIXServerSocket.bindOn(addr)) {
      assertEquals(123, ass.getLocalPort());

      AFUNIXServerSocket ass1 = FileDescriptorCast.using(ass.getFileDescriptor()).withLocalPort(123)
          .as(AFUNIXServerSocket.class);
      assertEquals(123, ass.getLocalPort());

      AFUNIXSocket socket = AFUNIXSocket.connectTo(addr);
      assertEquals(123, socket.getRemoteSocketAddress().getPort());

      AFUNIXSocket socket1 = FileDescriptorCast.using(socket.getFileDescriptor()).withRemotePort(
          123).as(AFUNIXSocket.class);
      assertEquals(socket.getRemoteSocketAddress(), socket1.getRemoteSocketAddress());
      assertEquals(123, socket1.getRemoteSocketAddress().getPort());

      AFUNIXSocket ss = ass1.accept();
      assertEquals(123, ss.getLocalPort());
      assertEquals(123, ss.getLocalSocketAddress().getPort());
    }
  }

  @Test
  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_DATAGRAMS)
  public void testDatagramPorts() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempPath(123);
    assertEquals(123, addr.getPort());
    try (AFUNIXDatagramChannel dc1 = AFUNIXDatagramChannel.open();
        AFUNIXDatagramChannel dc2 = AFUNIXDatagramChannel.open()) {
      dc1.setDeleteOnClose(true);
      dc2.setDeleteOnClose(true);

      assertNull(dc1.getLocalAddress());
      assertNull(dc1.getRemoteAddress());
      assertNull(dc2.getLocalAddress());
      assertNull(dc2.getRemoteAddress());

      dc1.bind(addr);
      dc2.connect(addr);

      assertEquals(dc1.getLocalAddress(), dc2.getRemoteAddress());

      assertEquals(123, dc1.getLocalAddress().getPort());

      // if we don't specify a port, we'll lose that information when casting (only stored in Java)
      {
        AFUNIXDatagramChannel dc1c = FileDescriptorCast.using(dc1.getFileDescriptor()).as(
            AFUNIXDatagramChannel.class);
        AFUNIXDatagramChannel dc2c = FileDescriptorCast.using(dc2.getFileDescriptor()).as(
            AFUNIXDatagramChannel.class);

        assertEquals(0, dc1c.getLocalAddress().getPort());
        assertEquals(0, dc2c.getRemoteAddress().getPort());
      }

      // we need to specify local and/or remote ports when working directly with file descriptors
      {
        AFUNIXDatagramChannel dc1c = FileDescriptorCast.using(dc1.getFileDescriptor())
            .withLocalPort(123).as(AFUNIXDatagramChannel.class);
        AFUNIXDatagramChannel dc2c = FileDescriptorCast.using(dc2.getFileDescriptor())
            .withRemotePort(123).as(AFUNIXDatagramChannel.class);

        assertEquals(123, dc1c.getLocalAddress().getPort());
        assertEquals(123, dc2c.getRemoteAddress().getPort());
      }
    }
  }

  @Test
  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_DATAGRAMS)
  public void testDatagramFileChannel() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempPath(123);
    try (AFUNIXDatagramChannel dc1 = AFUNIXDatagramChannel.open();
        AFUNIXDatagramChannel dc2 = AFUNIXDatagramChannel.open()) {
      dc1.setDeleteOnClose(true);
      dc2.setDeleteOnClose(true);
      dc1.bind(addr);
      dc2.connect(addr);

      FileDescriptorCast fdc1 = FileDescriptorCast.using(dc1.getFileDescriptor());
      assertTrue(fdc1.isAvailable(DatagramChannel.class));
      assertTrue(fdc1.isAvailable(AFUNIXDatagramChannel.class));
      assertTrue(fdc1.isAvailable(FileChannel.class));
      assertTrue(fdc1.availableTypes().contains(ReadableByteChannel.class));
      assertTrue(fdc1.availableTypes().contains(WritableByteChannel.class));

      FileChannel fc = fdc1.as(FileChannel.class);
      ByteBuffer bb = ByteBuffer.allocate(4);
      bb.putInt(0x12345678);
      bb.flip();
      dc2.write(bb);
      bb.clear();
      assertEquals(4, fc.read(bb));
      bb.flip();
      assertEquals(0x12345678, bb.getInt());
    }
  }
}
