package org.newsclub.net.unix;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.SocketOption;

abstract class DatagramSocketShim extends DatagramSocket {

  protected DatagramSocketShim(DatagramSocketImpl impl) {
    super(impl);
  }

  @Override
  public <T> T getOption(SocketOption<T> name) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      return getOption((AFSocketOption<T>) name);
    } else {
      return super.getOption(name);
    }
  }

  @Override
  public <T> DatagramSocket setOption(SocketOption<T> name, T value) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      return setOption((AFSocketOption<T>) name, value);
    } else {
      return super.setOption(name, value);
    }
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
  @SuppressWarnings("PMD.LinguisticNaming")
  public abstract <T> DatagramSocket setOption(AFSocketOption<T> name, T value) throws IOException;
}
