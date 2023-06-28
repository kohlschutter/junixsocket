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
