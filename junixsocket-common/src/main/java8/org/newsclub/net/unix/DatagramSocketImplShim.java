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

import java.net.DatagramSocketImpl;

/**
 * A shim that is filled with Java version-specific overrides. This variant is for Java 7 and 8.
 *
 * @author Christian Kohlschütter
 */
abstract class DatagramSocketImplShim extends DatagramSocketImpl {
  protected DatagramSocketImplShim() {
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
}
