/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
import java.net.SocketImpl;
import java.net.SocketOption;
import java.net.SocketOptions;
import java.net.StandardSocketOptions;
import java.util.Set;

/**
 * A shim that is filled with additional overrides in Java 9+ (and therefore empty in Java 7/8).
 * 
 * @author Christian Kohlschütter
 */
abstract class SocketImplShim extends SocketImpl {

  @Override
  @SuppressWarnings({"PMD.CompareObjectsWithEquals"})
  protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
    // Interesting fact of the day:
    // The old int-optID API expects SocketException to be thrown for unknown values,
    // and the new Java 9+ SocketOption API expects UnsupportedOperationException to be
    // thrown. However, at least up to Java 11, the new API calls the old API under the hood
    // (see SocketImpl), so in reality, SocketException is thrown then.
    // In Java 15 (?), they refactored the class, so the automatic mapping no longer occurs
    // for custom socket implementations, which means we have to roll our own mapping.

    if (name == StandardSocketOptions.SO_KEEPALIVE) {
      setOption(SocketOptions.SO_KEEPALIVE, value);
    } else if (name == StandardSocketOptions.SO_SNDBUF) {
      setOption(SocketOptions.SO_SNDBUF, value);
    } else if (name == StandardSocketOptions.SO_RCVBUF) {
      setOption(SocketOptions.SO_RCVBUF, value);
    } else if (name == StandardSocketOptions.SO_REUSEADDR) {
      setOption(SocketOptions.SO_REUSEADDR, value);
    } else if (name == StandardSocketOptions.SO_LINGER) {
      setOption(SocketOptions.SO_LINGER, value);
    } else if (name == StandardSocketOptions.IP_TOS) {
      setOption(SocketOptions.IP_TOS, value);
    } else if (name == StandardSocketOptions.TCP_NODELAY) {
      setOption(SocketOptions.TCP_NODELAY, value);
    } else {
      super.setOption(name, value);
    }
  }

  @SuppressWarnings({"unchecked", "PMD.CompareObjectsWithEquals"})
  @Override
  protected <T> T getOption(SocketOption<T> name) throws IOException {
    if (name == StandardSocketOptions.SO_KEEPALIVE) {
      return (T) getOption(SocketOptions.SO_KEEPALIVE);
    } else if (name == StandardSocketOptions.SO_SNDBUF) {
      return (T) getOption(SocketOptions.SO_SNDBUF);
    } else if (name == StandardSocketOptions.SO_RCVBUF) {
      return (T) getOption(SocketOptions.SO_RCVBUF);
    } else if (name == StandardSocketOptions.SO_REUSEADDR) {
      return (T) getOption(SocketOptions.SO_REUSEADDR);
    } else if (name == StandardSocketOptions.SO_LINGER) {
      return (T) getOption(SocketOptions.SO_LINGER);
    } else if (name == StandardSocketOptions.IP_TOS) {
      return (T) getOption(SocketOptions.IP_TOS);
    } else if (name == StandardSocketOptions.TCP_NODELAY) {
      return (T) getOption(SocketOptions.TCP_NODELAY);
    } else {
      return super.getOption(name);
    }
  }

  @Override
  protected Set<SocketOption<?>> supportedOptions() {
    return Set.of(//
        StandardSocketOptions.SO_REUSEADDR, //
        StandardSocketOptions.SO_RCVBUF, //
        StandardSocketOptions.SO_SNDBUF, //
        StandardSocketOptions.SO_KEEPALIVE, //
        StandardSocketOptions.SO_LINGER);
  }
}
