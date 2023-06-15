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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.channels.Pipe.SourceChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Tests the behavior of {@link AFPipe}.
 *
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
public final class PipeTest {
  private final ExecutorService exc = Executors.newCachedThreadPool();

  /**
   * Tests sequential writing/reading.
   *
   * @throws IOException on error.
   */
  @Test
  public void testPipe() throws IOException {
    ByteBuffer out = ByteBuffer.allocate(4);
    out.putInt(0x04030201);
    out.flip();
    ByteBuffer in = ByteBuffer.allocate(4);

    AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();
    AFPipe pipe = provider.openPipe();
    try (SinkChannel sink = pipe.sink(); //
        SourceChannel source = pipe.source()) {

      // source.configureBlocking(false);
      // assertEquals(0, source.read(in));
      // source.configureBlocking(true);

      sink.write(out);
      int nRead;
      do {
        nRead = source.read(in);
      } while (nRead == 0);
      in.flip();
      assertEquals(0x04030201, in.getInt());
    }
  }

  /**
   * Tests concurrent writing/reading from the pipe.
   *
   * @throws IOException on error.
   * @throws InterruptedException on error.
   * @throws ExecutionException on error.
   * @throws TimeoutException on error.
   * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8279916">JDK-8279916</a>
   */
  @Test
  public void testPipeRecvHang() throws IOException, InterruptedException, ExecutionException,
      TimeoutException {
    final long startTime = System.currentTimeMillis();
    final long breaktime = startTime + 1 * 1000;
    long endTime;
    int pass;
    for (pass = 0; (endTime = System.currentTimeMillis()) <= breaktime; pass++) {
      Pipe pipe = AFUNIXSelectorProvider.provider().openPipe();

      Future<Long> writer = exc.submit(() -> {
        try (SinkChannel sink = pipe.sink()) {
          long written = 0;
          for (int length = 0x10000; length > 0; length >>= 1) {
            ByteBuffer buf = ByteBuffer.allocate(length);
            written += sink.write(buf);
          }
          return written;
        }
      });

      Future<Long> reader = exc.submit(() -> {
        try (SourceChannel source = pipe.source()) {
          ByteBuffer buf = ByteBuffer.allocate(0x10000);
          long read = 0;
          long numBytes;
          while ((numBytes = source.read(buf)) != -1) {
            read += numBytes;
            buf.clear();
          }
          return read;
        }
      });

      long written = writer.get();
      long read = reader.get(1, TimeUnit.SECONDS);

      assertEquals(written, read);
    }

    long duration = endTime - startTime;
    float passesPerMsec = pass / (float) duration;
    // System.out.println("AFPipe: passes/msec " + passesPerMsec);
    assertNotEquals(0, passesPerMsec);
  }
}
