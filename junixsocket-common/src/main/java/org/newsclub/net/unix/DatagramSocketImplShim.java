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
import java.net.DatagramSocketImpl;
import java.net.SocketOption;
import java.util.Set;

/**
 * A shim that is filled with Java version-specific overrides. This variant is for Java 9 and above.
 *
 * @author Christian Kohlschütter
 */
abstract class DatagramSocketImplShim extends DatagramSocketImpl {
  protected DatagramSocketImplShim() {
    super();
  }

  @Override
  protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      ((AFDatagramSocketImpl<?>) this).getCore().setOption((AFSocketOption<T>) name, value);
      return;
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      super.setOption(name, value);
    } else {
      setOption(optionId, value);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T getOption(SocketOption<T> name) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      return ((AFDatagramSocketImpl<?>) this).getCore().getOption((AFSocketOption<T>) name);
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      return super.getOption(name);
    } else {
      return (T) getOption(optionId);
    }
  }

  @Override
  protected Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }
}
