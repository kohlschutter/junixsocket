/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXDatagramChannel;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.AFUNIXSocketPair;
import org.newsclub.net.unix.FileDescriptorCast;
import org.newsclub.net.unix.StdinSocketApp;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.ForkedVM;
import com.kohlschutter.testutil.ProcessUtilRequirement;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressWarnings("PMD.CouplingBetweenObjects")
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public class FileDescriptorCastTest {
  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR)
  public void testSocketPair() throws Exception {
    AFUNIXSocketPair<AFUNIXSocketChannel> socketPair = AFUNIXSocketPair.open();
    AFUNIXSocketChannel sock1chan = socketPair.getSocket1();
    FileDescriptor sock2fd = socketPair.getSocket2().getFileDescriptor();

    assertTrue(sock1chan.isConnected());

    FileDescriptorCast fdc = FileDescriptorCast.using(sock2fd);

    // Limitation: When we emulate socket pairs through non AF_UNIX sockets, we currently do not
    // support casting to Socket.class
    assertEquals(AFUNIXSocket.supports(AFSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR), fdc
        .isAvailable(Socket.class));

    if (!AFUNIXSocket.supports(AFSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR)) {
      FileDescriptorCast.using(sock2fd).as(FileChannel.class);
    } else {
      Socket sock2 = FileDescriptorCast.using(sock2fd).as(Socket.class);
      assertEquals(AFUNIXSocket.class, sock2.getClass());
      assertTrue(sock2.isConnected());
      assertEquals(sock2fd, ((AFUNIXSocket) sock2).getFileDescriptor());
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_NATIVE_SOCKETPAIR)
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

      // NOTE: OpenBSD blocks on such a non-permissible cast, so we set a timeout
      // This will cause a SocketTimeoutException, which we change to a SocketException
      // so our tests can still pass
      sock.setSoTimeout(1);

      assertThrows(SocketException.class, () -> {
        try {
          sock.connect(ass2.getLocalSocketAddress());
        } catch (SocketTimeoutException e) {
          throw new SocketException().initCause(e);
        }
      });
      assertThrows(SocketException.class, () -> {
        try {
          sock.connect(ass.getLocalSocketAddress());
        } catch (SocketTimeoutException e) {
          throw new SocketException().initCause(e);
        }
      });
      assertThrows(SocketException.class, () -> {
        try {
          sock.getInputStream().read();
        } catch (SocketTimeoutException e) {
          throw new SocketException().initCause(e);
        }
      });

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
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
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

  private int getPort(AFSocketAddress addr) {
    if (addr == null) {
      return -1;
    } else {
      return addr.getPort();
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
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

      assertEquals(123, getPort(dc1.getLocalAddress()));

      // if we don't specify a port, we'll lose that information when casting (only stored in Java)
      {
        AFUNIXDatagramChannel dc1c = FileDescriptorCast.using(dc1.getFileDescriptor()).as(
            AFUNIXDatagramChannel.class);
        AFUNIXDatagramChannel dc2c = FileDescriptorCast.using(dc2.getFileDescriptor()).as(
            AFUNIXDatagramChannel.class);

        assertEquals(0, getPort(dc1c.getLocalAddress()));
        assertEquals(0, getPort(dc2c.getRemoteAddress()));
      }

      // we need to specify local and/or remote ports when working directly with file descriptors
      {
        AFUNIXDatagramChannel dc1c = FileDescriptorCast.using(dc1.getFileDescriptor())
            .withLocalPort(123).as(AFUNIXDatagramChannel.class);
        AFUNIXDatagramChannel dc2c = FileDescriptorCast.using(dc2.getFileDescriptor())
            .withRemotePort(123).as(AFUNIXDatagramChannel.class);

        assertEquals(123, getPort(dc1c.getLocalAddress()));
        assertEquals(123, getPort(dc2c.getRemoteAddress()));
      }
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
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

  /**
   * Passes a socket to a newly created Java process as "standard input". The new process sends back
   * "Hello world" over that socket.
   *
   * @throws Exception on failure.
   * @see StdinSocketApp
   */
  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_FD_AS_REDIRECT)
  @ProcessUtilRequirement(canGetJavaCommandArguments = true)
  public void testForkedVMRedirectStdin() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();
    try (AFUNIXServerSocket serverSocket = AFUNIXServerSocket.bindOn(addr);
        AFUNIXSocket clientConn = AFUNIXSocket.connectTo(addr);
        AFUNIXSocket serverConn = serverSocket.accept()) {
      assertTrue(addr.getFile().delete());

      ForkedVM vm = new ForkedVM(StdinSocketApp.class);
      vm.setRedirectInput(FileDescriptorCast.using(clientConn.getFileDescriptor()).as(
          Redirect.class));
      vm.setRedirectError(Redirect.INHERIT);
      // vm.setRedirectOutput(Redirect.INHERIT);
      Process p = vm.fork();
      try {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(serverConn
            .getInputStream(), StandardCharsets.UTF_8))) {
          assertEquals("Hello world", br.readLine());
          String l;
          if ((l = br.readLine()) != null) {
            fail("Unexpected output: " + l);
          }
        }
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue());
      } finally {
        p.destroyForcibly();
      }
    }
  }
}
