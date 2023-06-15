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
import java.net.SocketImpl;
import java.net.SocketOption;
import java.util.Collections;
import java.util.Set;

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

  protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
    throw new IOException("Unsupported option");
  }

  protected <T> T getOption(SocketOption<T> name) throws IOException {
    throw new IOException("Unsupported option");
  }

  protected Set<SocketOption<?>> supportedOptions() {
    return Collections.emptySet();
  }
}
