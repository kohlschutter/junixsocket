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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Reads and writes data either using byte arrays or byte-for-byte (which may be implemented
 * differently).
 * 
 * @author Christian Kohlschütter
 */
public class ReadWriteTest extends SocketTestBase {
  private static final byte[] DATA = {-127, -2, -1, 0, 1, 2, 127, 1, 2, 4, 8, 16, 31};

  public ReadWriteTest() throws IOException {
    super();
  }

  @Test
  public void testReceiveWithByteArraySendWithByteArray() {
    assertTimeout(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteArrayWritingServerThread()) {
        receiveDataWithByteArray();
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
    assertTimeout(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteArrayWritingServerThread()) {
        receiveDataByteForByte();
      }
    });
  }

  @Test
  public void testReceiveWithByteArraySendByteForByte() {
    assertTimeout(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteForByteWritingServerThread()) {
        receiveDataWithByteArray();
      }
    });
  }

  @Test
  public void testReceiveDataByteForByteSendByteForByte() {
    assertTimeout(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ByteForByteWritingServerThread()) {
        receiveDataByteForByte();
      }
    });
  }

  private final class ByteArrayWritingServerThread extends ServerThread {
    public ByteArrayWritingServerThread() throws IOException {
      super();
    }

    @Override
    protected void handleConnection(final AFUNIXSocket sock) throws IOException {
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
    public ByteForByteWritingServerThread() throws IOException {
      super();
    }

    @Override
    protected void handleConnection(final AFUNIXSocket sock) throws IOException {
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

  private void receiveDataWithByteArray() throws IOException {
    try (AFUNIXSocket sock = connectToServer(); //
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

  private void receiveDataByteForByte() throws IOException {
    try (AFUNIXSocket sock = connectToServer(); //
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
