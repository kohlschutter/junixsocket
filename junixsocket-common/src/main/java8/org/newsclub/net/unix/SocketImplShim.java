package org.newsclub.net.unix;

import java.io.IOException;
import java.net.SocketImpl;
import java.net.SocketOption;
import java.util.Collections;
import java.util.Set;

/**
 * A shim that is filled with Java version-specific overrides. This variant is for Java 7 and 8.
 *
 * @author Christian Kohlschütter
 */
abstract class SocketImplShim extends SocketImpl {
  protected SocketImplShim() {
    super();
  }

  @SuppressWarnings("all")
  @Override
  protected final void finalize() {
    try {
      close();
    } catch (Exception e) {
      // nothing that can be done here
    }
  }

  protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
    throw new IOException("Unsupported option");
  }

  protected <T> T getOption(SocketOption<T> name) throws IOException {
    throw new IOException("Unsupported option");
  }

  protected Set<SocketOption<?>> supportedOptions() {
    return Collections.emptySet();
  }
}
