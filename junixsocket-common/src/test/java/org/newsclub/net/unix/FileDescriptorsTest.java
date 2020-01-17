/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests sending and receiving file descriptors.
 * 
 * @author Christian Kohlschütter
 */
@AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_FILE_DESCRIPTORS)
// CPD-OFF - Skip code-duplication checks
public class FileDescriptorsTest extends SocketTestBase {
  public FileDescriptorsTest() throws IOException {
    super();
  }

  @Test
  public void testSendRecvFileDescriptors() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      final ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          AFUNIXSocket socket = (AFUNIXSocket) sock;

          socket.setOutboundFileDescriptors(FileDescriptor.out, FileDescriptor.err);
          socket.getOutputStream().write("HELLO".getBytes("UTF-8"));

          stopAcceptingConnections();
        }
      };

      try (AFUNIXSocket socket = connectToServer(); InputStream in = socket.getInputStream()) {
        socket.setAncillaryReceiveBufferSize(1024);

        byte[] buf = new byte[64];
        FileDescriptor[] fds;
        int numRead;
        fds = socket.getReceivedFileDescriptors();
        assertNull(fds, "Initially, there are no file descriptors");

        numRead = in.read(buf);
        assertEquals(5, numRead, "'HELLO' is five bytes long");
        assertEquals("HELLO", new String(buf, 0, numRead, "UTF-8"));

        fds = socket.getReceivedFileDescriptors();
        assertEquals(2, fds.length, "Now, we should have two file descriptors");

        fds = socket.getReceivedFileDescriptors();
        assertNull(fds, "If we ask again, these new file descriptors should be gone");

        numRead = socket.getInputStream().read(buf);
        assertEquals(-1, numRead, "There shouldn't be anything left to read");
        fds = socket.getReceivedFileDescriptors();
        assertNull(fds, "There shouldn't be any new file descriptors");
      }

      serverThread.getServerSocket().close();
      serverThread.checkException();
    });
  }

  @Test
  public void testBadFileDescriptor() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      final ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          AFUNIXSocket socket = (AFUNIXSocket) sock;

          socket.setOutboundFileDescriptors(new FileDescriptor());
          try {
            // NOTE: send an arbitrary byte — we can't send fds without any in-band data
            socket.getOutputStream().write(123);
            Assertions.fail("Expected a \"Bad file descriptor\" SocketException");
          } catch (SocketException e) {
            // expected
          }

          stopAcceptingConnections();
        }
      };

      try (AFUNIXSocket socket = connectToServer();) {
        socket.setAncillaryReceiveBufferSize(1024);
        socket.getInputStream().read();
      }

      serverThread.getServerSocket().close();
      serverThread.checkException();
    });
  }

  @Test
  public void testNoAncillaryReceiveBuffer() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      final ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          AFUNIXSocket socket = (AFUNIXSocket) sock;

          socket.setOutboundFileDescriptors(FileDescriptor.out, FileDescriptor.err);

          // NOTE: send an arbitrary byte — we can't send fds without any in-band data
          socket.getOutputStream().write(123);

          stopAcceptingConnections();
        }
      };

      try (AFUNIXSocket socket = connectToServer();) {
        // NOTE: we haven't set the ancillary receive buffer size

        try {
          assertEquals(123, socket.getInputStream().read());
        } catch (SocketException e) {
          // on Linux, a SocketException may be thrown (an ancillary message was sent, but not read)
        }
        assertNull(socket.getReceivedFileDescriptors());
        assertEquals(0, socket.getAncillaryReceiveBufferSize());
      }

      serverThread.getServerSocket().close();
      serverThread.checkException();
    });
  }

  @Test
  public void testAncillaryReceiveBufferTooSmall() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      final ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          AFUNIXSocket socket = (AFUNIXSocket) sock;

          socket.setOutboundFileDescriptors(FileDescriptor.out, FileDescriptor.err);

          // NOTE: send an arbitrary byte — we can't send fds without any in-band data
          socket.getOutputStream().write(123);

          stopAcceptingConnections();
        }
      };

      try (AFUNIXSocket socket = connectToServer();) {
        socket.setAncillaryReceiveBufferSize(13);
        try {
          assertEquals(123, socket.getInputStream().read());
          Assertions.fail("Expected a \"No buffer space available\" SocketException");
        } catch (SocketException e) {
          // expected
        }
        assertNull(socket.getReceivedFileDescriptors());
      }

      serverThread.getServerSocket().close();
      serverThread.checkException();
    });
  }

  @Test
  public void testFileInputStream() throws Exception {
    final File tmpFile = File.createTempFile("junixsocket", "test");
    tmpFile.deleteOnExit();

    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      final ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          AFUNIXSocket socket = (AFUNIXSocket) sock;

          try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            fos.write("WORLD!".getBytes("UTF-8"));
          }
          try (FileInputStream fin = new FileInputStream(tmpFile)) {
            socket.setOutboundFileDescriptors(fin.getFD());
            socket.getOutputStream().write("HELLO".getBytes("UTF-8"));
          }

          stopAcceptingConnections();
        }
      };

      try (AFUNIXSocket socket = connectToServer(); InputStream in = socket.getInputStream()) {
        socket.setAncillaryReceiveBufferSize(1024);

        byte[] buf = new byte[64];
        FileDescriptor[] fds;
        int numRead;

        numRead = in.read(buf);
        assertEquals(5, numRead, "'HELLO' is five bytes long");
        assertEquals("HELLO", new String(buf, 0, numRead, "UTF-8"));

        fds = socket.getReceivedFileDescriptors();
        assertEquals(1, fds.length, "Now, we should have two file descriptors");
        FileDescriptor fdesc = fds[0];

        try (FileInputStream fin = new FileInputStream(fdesc)) {
          numRead = fin.read(buf);
          assertEquals(6, numRead, "'WORLD!' is six bytes long");
          assertEquals("WORLD!", new String(buf, 0, numRead, "UTF-8"));
        }
      }

      serverThread.getServerSocket().close();
      serverThread.checkException();
      Files.delete(tmpFile.toPath());
    });
  }

  @Test
  public void testFileInputStreamPartiallyConsumed() throws Exception {
    final File tmpFile = File.createTempFile("junixsocket", "test");
    tmpFile.deleteOnExit();

    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      final ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          AFUNIXSocket socket = (AFUNIXSocket) sock;

          try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            fos.write("WORLD!".getBytes("UTF-8"));
          }
          try (FileInputStream fin = new FileInputStream(tmpFile)) {
            assertEquals('W', fin.read());

            // We send the file descriptor of fin, from which we already consumed one byte.
            socket.setOutboundFileDescriptors(fin.getFD());
            socket.getOutputStream().write("HELLO".getBytes("UTF-8"));
          }

          stopAcceptingConnections();
        }
      };

      try (AFUNIXSocket socket = connectToServer(); InputStream in = socket.getInputStream()) {
        socket.setAncillaryReceiveBufferSize(1024);

        byte[] buf = new byte[64];
        FileDescriptor[] fds;
        int numRead;

        numRead = in.read(buf);
        assertEquals(5, numRead, "'HELLO' is five bytes long");
        assertEquals("HELLO", new String(buf, 0, numRead, "UTF-8"));

        fds = socket.getReceivedFileDescriptors();
        assertEquals(1, fds.length, "Now, we should have two file descriptors");
        FileDescriptor fdesc = fds[0];

        try (FileInputStream fin = new FileInputStream(fdesc)) {
          numRead = fin.read(buf);
          assertEquals(5, numRead, "'ORLD!' is five bytes long");
          assertEquals("ORLD!", new String(buf, 0, numRead, "UTF-8"));
        }
      }

      serverThread.getServerSocket().close();
      serverThread.checkException();
      Files.delete(tmpFile.toPath());
    });
  }
}
