package org.newsclub.net.unix;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;

abstract class DatagramSocketShim extends DatagramSocket {

  protected DatagramSocketShim(DatagramSocketImpl impl) {
    super(impl);
  }

  /**
   * Returns the value of a junixsocket socket option.
   *
   * @param <T> The type of the socket option value.
   * @param name The socket option.
   * @return The value of the socket option.
   * @throws IOException on error.
   */
  public abstract <T> T getOption(AFSocketOption<T> name) throws IOException;

  /**
   * Sets the value of a socket option.
   *
   * @param <T> The type of the socket option value.
   * @param name The socket option.
   * @param value The value of the socket option.
   * @return this DatagramSocket.
   * @throws IOException on error.
   */
  public abstract <T> DatagramSocket setOption(AFSocketOption<T> name, T value) throws IOException;
}
