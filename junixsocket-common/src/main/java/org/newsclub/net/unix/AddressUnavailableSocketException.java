package org.newsclub.net.unix;

import java.net.SocketException;

/**
 * A {@link SocketException} that may be thrown upon some "address unavailable" condition from
 * native code (e.g., EADDRNOTAVAIL is returned).
 * 
 * @author Christian Kohlschütter
 */
public class AddressUnavailableSocketException extends InvalidSocketException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@link AddressUnavailableSocketException}.
   */
  public AddressUnavailableSocketException() {
    super();
  }

  /**
   * Constructs a new {@link AddressUnavailableSocketException}.
   * 
   * @param msg The error message.
   */
  public AddressUnavailableSocketException(String msg) {
    super(msg);
  }
}
