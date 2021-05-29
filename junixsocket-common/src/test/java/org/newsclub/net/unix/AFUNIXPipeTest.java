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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.channels.Pipe.SourceChannel;

import org.junit.jupiter.api.Test;

public class AFUNIXPipeTest {
  @Test
  public void testPipe() throws IOException {
    testPipe0(false);
  }

  @Test
  public void testSelectablePipe() throws IOException {
    testPipe0(true);
  }

  private void testPipe0(boolean selectable) throws IOException {
    ByteBuffer out = ByteBuffer.allocate(4);
    out.putInt(0x04030201);
    out.flip();
    ByteBuffer in = ByteBuffer.allocate(4);

    AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();
    AFUNIXPipe pipe = selectable ? provider.openSelectablePipe() : provider.openPipe();
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
}
