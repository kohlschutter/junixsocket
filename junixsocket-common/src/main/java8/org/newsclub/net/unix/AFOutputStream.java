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
