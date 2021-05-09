package org.newsclub.net.unix;

import java.net.SocketImpl;

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
}
