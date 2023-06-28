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
package org.newsclub.net.unix.selftest.apps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class IOUtil {
  private IOUtil() {
    throw new IllegalStateException("No instances");
  }

  static long transfer(InputStream in, OutputStream out) throws IOException {
    long transferred = 0;
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer, 0, 8192)) >= 0) {
      out.write(buffer, 0, read);
      transferred += read;
    }
    return transferred;
  }
}
