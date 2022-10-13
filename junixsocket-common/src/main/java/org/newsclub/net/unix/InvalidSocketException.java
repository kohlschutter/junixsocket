package org.newsclub.net.unix;

import java.net.SocketException;

/**
 * A {@link SocketException} that may be thrown upon some "invalid" state, mostly detected in native
 * code.
 * 
 * @author Christian Kohlschütter
 * @see InvalidArgumentSocketException
 * @see AddressUnavailableSocketException
 */
public class InvalidSocketException extends SocketException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@link InvalidSocketException}.
   */
  public InvalidSocketException() {
    super();
  }

  /**
   * Constructs a new {@link InvalidSocketException}.
   * 
   * @param msg The error message.
   */
  public InvalidSocketException(String msg) {
    super(msg);
  }
}
