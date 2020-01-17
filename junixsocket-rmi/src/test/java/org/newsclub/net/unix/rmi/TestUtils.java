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
package org.newsclub.net.unix.rmi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Some testing-specific helper functions.
 * 
 * @author Christian Kohlschütter
 */
final class TestUtils {
  private TestUtils() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Reads all bytes from the given InputStream.
   * 
   * Workaround for Java 8 where <code>InputStream#readAllBytes()</code> is not available.
   * 
   * @param in The input stream.
   * @return The bytes.
   * @throws IOException on error.
   */
  public static byte[] readAllBytes(InputStream in) throws IOException {
    byte[] buf = new byte[4096];
    int read;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    while ((read = in.read(buf)) != -1) {
      bos.write(buf, 0, read);
    }
    return bos.toByteArray();
  }
}
