/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.SocketOption;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

@IgnoreJRERequirement // see src/main/java8
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
