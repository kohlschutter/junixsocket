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
package org.newsclub.net.unix.demo.server;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * A multi-threaded unix socket server that implements a TCP-style character generator compliant
 * with RFC864.
 *
 * @author Christian Kohlschütter
 */
public final class ChargenServer extends DemoServerBase {
  private static final Chargen SIMPLE_CHARGEN = new SimpleChargen();
  private final boolean fast;
  private FastChargen cachedChargen = null;

  /**
   * Defines a TCP-style character generator compliant with RFC864.
   *
   * @see <a href="https://tools.ietf.org/html/rfc864">RFC864</a>
   */
  private interface Chargen {
    void write(Socket socket) throws IOException;
  }

  public ChargenServer(SocketAddress listenAddress) {
    this(listenAddress, true);
  }

  public ChargenServer(SocketAddress listenAddress, boolean fast) {
    super(listenAddress);
    this.fast = fast;
  }

  @Override
  protected void onServerStarting() {
    super.onServerStarting();
    System.out.println("- Fast chargen: " + fast);
  }

  @Override
  protected void doServeSocket(Socket socket) throws IOException {
    try (OutputStream os = socket.getOutputStream()) {
      getChargen(socket).write(socket);
    }
  }

  private synchronized Chargen getChargen(Socket socket) throws SocketException {
    if (!fast) {
      return SIMPLE_CHARGEN;
    }

    int bufferSize = socket.getSendBufferSize();
    FastChargen chargen = cachedChargen;
    if (chargen == null || chargen.cacheSize != bufferSize) {
      chargen = new FastChargen(bufferSize);
      cachedChargen = chargen;
    }
    return chargen;
  }

  /**
   * A simple chargen implementation.
   *
   * Even though this looks straightforward, it's not the fastest implementation.
   *
   * @see FastChargen
   */
  private static final class SimpleChargen implements Chargen {
    @Override
    public void write(Socket socket) throws IOException {
      try (BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream(), socket
          .getSendBufferSize())) {
        while (true) {
          int offset = 1;
          for (int row = 0; row < 95; row++) {
            for (int i = 0; i < 72; i++) {
              int asciiChar = (32 + (offset + i) % 95);
              bos.write(asciiChar);
            }
            bos.write(13); // CR
            bos.write(10); // LF

            offset++;
          }
        }
      }
    }
  }

  /**
   * A fast chargen implementation, using a pre-built data buffer that is just large enough to
   * always send a full array of bytes matching the socket's send buffer capacity.
   *
   * @see SimpleChargen
   */
  private static final class FastChargen implements Chargen {
    /**
     * Size of the ever-repeating chargen pattern.
     */
    private final int ourDataSize;

    /**
     * Size of the cache that contains the ever-repeating chargen pattern data, and some more of it.
     */
    private final int cacheSize;

    /**
     * The cache.
     */
    private final byte[] cache;

    FastChargen(int sendBufferSize) {
      this.cacheSize = sendBufferSize;

      final int lineWidth = 72;
      final int firstPrintableCharacter = 32; // ASCII printable start
      final int lastPrintableCharacter = 126; // ASCII printable end

      final int numPrintableCharacters = (lastPrintableCharacter + 1) - firstPrintableCharacter;
      final int linefeedLen = 2; // CR+LF

      this.ourDataSize = numPrintableCharacters * (lineWidth + linefeedLen);
      final int bufLen = sendBufferSize + ourDataSize;

      ByteArrayOutputStream bos = new ByteArrayOutputStream(bufLen);
      int nWritten = 0;

      bigLoop : while (nWritten < bufLen) {
        int offset = 1;
        for (int row = 0; row < numPrintableCharacters; row++) {
          for (int i = 0; i < lineWidth; i++) {
            int asciiChar = (32 + (offset + i) % numPrintableCharacters);
            bos.write(asciiChar);
            if (++nWritten == bufLen) {
              break bigLoop;
            }
          }
          bos.write(13); // CR
          bos.write(10); // LF
          if (++nWritten == bufLen) {
            break bigLoop;
          }
          offset++;
        }
      }

      this.cache = bos.toByteArray();
    }

    @Override
    public void write(Socket socket) throws IOException {
      try (OutputStream os = socket.getOutputStream()) {
        int offset = 0;
        while (true) {
          os.write(cache, offset, cacheSize);
          offset = (offset + cacheSize) % ourDataSize;
        }
      }
    }
  }
}
