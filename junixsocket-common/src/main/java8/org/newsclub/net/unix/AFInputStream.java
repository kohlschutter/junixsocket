package org.newsclub.net.unix;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public abstract class AFInputStream extends InputStream implements FileDescriptorAccess {
  private static final int DEFAULT_BUFFER_SIZE = 8192;

  AFInputStream() {
    super();
  }

  /**
   * Backport Java 9 functionality
   */
  public long transferTo(OutputStream out) throws IOException {
    Objects.requireNonNull(out, "out");
    long transferred = 0;
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    int read;
    while ((read = this.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
      out.write(buffer, 0, read);
      transferred += read;
    }
    return transferred;
  }
}
