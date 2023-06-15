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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public abstract class AFOutputStream extends OutputStream implements FileDescriptorAccess {
  private static final int DEFAULT_BUFFER_SIZE = 8192;

  AFOutputStream() {
    super();
  }

  /**
   * Reads all bytes from the given input stream and writes the bytes to this output stream in the
   * order that they are read. On return, this input stream will be at end of stream. This method
   * does not close either stream.
   *
   * This method effectively is the reverse notation of
   * {@link InputStream#transferTo(OutputStream)}, which may or may not be optimized for
   * {@link AFSocket}s.
   *
   * @param in The {@link InputStream} to transfer from.
   * @return The number of bytes transferred.
   * @throws IOException on error.
   */
  public long transferFrom(InputStream in) throws IOException {
    Objects.requireNonNull(in, "in");
    if (in instanceof AFInputStream) {
      return ((AFInputStream) in).transferTo(this);
    }

    long transferred = 0;
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    int read;
    while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
      this.write(buffer, 0, read);
      transferred += read;
    }
    return transferred;
  }
}
