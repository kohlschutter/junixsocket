package org.newsclub.net.unix;

import java.net.SocketImpl;

/**
 * A shim that is filled with additional overrides in Java 9+ (and therefore empty in Java 7/8).
 * 
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.AbstractClassWithoutAnyMethod")
abstract class SocketImplShim extends SocketImpl {
  protected SocketImplShim() {
    super();
  }
}
