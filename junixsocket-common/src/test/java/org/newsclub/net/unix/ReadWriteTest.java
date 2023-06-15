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
package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Reads and writes data either using byte arrays or byte-for-byte (which may be implemented
 * differently).
 *
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class ReadWriteTest<A extends SocketAddress> extends SocketTestBase<A> {
  private static final byte[] DATA = {-127, -2, -1, 0, 1, 2, 127, 1, 2, 4, 8, 16, 31};

  protected ReadWriteTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testReceiveWithByteArraySendWithByteArray() {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteArrayWritingServerThread()) {
        receiveDataWithByteArray(serverThread.getServerAddress());
      }
    });
  }

  /**
   * Tests if {@link InputStream#available()} works as expected. The server sends 23 bytes. The
   * client waits for 100ms. After that, the client should be able to read exactly 23 bytes without
   * blocking. Then, we try the opposite direction.
   */
  @Test
  public void testReceiveDataByteForByteSendWithByteArray() {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteArrayWritingServerThread()) {
        receiveDataByteForByte(serverThread.getServerAddress());
      }
    });
  }

  @Test
  public void testReceiveWithByteArraySendByteForByte() {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteForByteWritingServerThread()) {
        receiveDataWithByteArray(serverThread.getServerAddress());
      }
    });
  }

  @Test
  public void testReceiveDataByteForByteSendByteForByte() {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteForByteWritingServerThread()) {
        receiveDataByteForByte(serverThread.getServerAddress());
      }
    });
  }

  private final class ByteArrayWritingServerThread extends ServerThread {
    public ByteArrayWritingServerThread() throws IOException, InterruptedException {
      super();
    }

    @Override
    protected void handleConnection(final Socket sock) throws IOException {
      try (OutputStream out = sock.getOutputStream(); //
          InputStream in = sock.getInputStream()) {

        out.write(DATA);

        byte[] buf = new byte[4096];
        int numReceived = in.read(buf);
        assertEquals(1, numReceived);
        assertEquals(buf[0], DATA.length);
      }
    }
  }

  private final class ByteForByteWritingServerThread extends ServerThread {
    public ByteForByteWritingServerThread() throws IOException, InterruptedException {
      super();
    }

    @Override
    protected void handleConnection(final Socket sock) throws IOException {
      try (OutputStream out = sock.getOutputStream(); //
          InputStream in = sock.getInputStream()) {

        for (byte b : DATA) {
          out.write(b);
        }

        int dataLength = in.read();
        assertEquals(DATA.length, dataLength);

        assertEquals(-1, in.read());
      }
    }
  }

  private void receiveDataWithByteArray(SocketAddress serverAddress) throws IOException {
    try (Socket sock = connectTo(serverAddress); //
        InputStream in = sock.getInputStream(); //
        OutputStream out = sock.getOutputStream()) {

      byte[] buf = new byte[4096];
      int numReceived = 0;
      int read;
      do {
        read = in.read(buf, numReceived, buf.length - numReceived);
        if (read >= 0) {
          numReceived += read;
        } else {
          break;
        }
      } while (numReceived < DATA.length);
      assertEquals(DATA.length, numReceived);

      byte[] received = new byte[numReceived];
      System.arraycopy(buf, 0, received, 0, numReceived);
      assertArrayEquals(DATA, received);

      out.write(numReceived);
    }
  }

  private void receiveDataByteForByte(SocketAddress serverAddress) throws IOException {
    try (Socket sock = connectTo(serverAddress); //
        InputStream in = sock.getInputStream(); //
        OutputStream out = sock.getOutputStream()) {

      byte[] buf = new byte[4096];
      int numReceived = 0;

      do {
        int ret = in.read();
        if (ret == -1) {
          break;
        }
        assertTrue(ret >= 0);
        buf[numReceived++] = (byte) ret;
      } while (numReceived != DATA.length);

      assertEquals(numReceived, DATA.length);
      byte[] received = new byte[numReceived];
      System.arraycopy(buf, 0, received, 0, numReceived);
      assertArrayEquals(DATA, received);

      out.write(numReceived);
    }
  }
}
