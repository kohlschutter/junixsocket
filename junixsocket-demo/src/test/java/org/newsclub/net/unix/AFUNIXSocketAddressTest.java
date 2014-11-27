/**
 * junixsocket
 *
 * Copyright (c) 2009,2014 Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public final class AFUNIXSocketAddressTest {

  @Test
  public void testPort() throws IOException {
    assertEquals(0, new AFUNIXSocketAddress(new File("/tmp/whatever")).getPort());
    assertEquals(123, new AFUNIXSocketAddress(new File("/tmp/whatever"), 123).getPort());
    assertEquals(44444, new AFUNIXSocketAddress(new File("/tmp/whatever"), 44444).getPort());
    try {
      new AFUNIXSocketAddress(new File("/tmp/whatever"), -1);
      fail("Expected IllegalArgumentException for illegal port");
    } catch (final IllegalArgumentException e) {
      // expected
    }
    try {
      new AFUNIXSocketAddress(new File("/tmp/whatever"), 65536);
    } catch (final IllegalArgumentException e) {
      fail("AFUNIXSocketAddress supports ports larger than 65535");
    }
  }
}
