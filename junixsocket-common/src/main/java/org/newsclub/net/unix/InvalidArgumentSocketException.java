package org.newsclub.net.unix;

import java.net.SocketException;

/**
 * A {@link SocketException} that may be thrown upon some "invalid argument" being passed into
 * native code (i.e., EINVAL is returned).
 * 
 * @author Christian Kohlschütter
 */
public class InvalidArgumentSocketException extends SocketException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@link InvalidArgumentSocketException}.
   */
  public InvalidArgumentSocketException() {
    super();
  }

  /**
   * Constructs a new {@link InvalidArgumentSocketException}.
   * 
   * @param msg The error message.
   */
  public InvalidArgumentSocketException(String msg) {
    super(msg);
  }
}
