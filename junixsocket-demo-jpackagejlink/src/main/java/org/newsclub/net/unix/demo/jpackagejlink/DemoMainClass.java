/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
package org.newsclub.net.unix.demo.jpackagejlink;

import org.newsclub.net.unix.AFUNIXSocket;

/**
 * The entrypoint class for the jpackage or jlink demo binary.
 *
 * @author Christian Kohlschütter
 */
public final class DemoMainClass {

  private DemoMainClass() {
  }

  /**
   * The entrypoint method for the jpackage or jlink demo binary.
   *
   * @param args The program arguments.
   */
  public static void main(String[] args) {
    // run a simple built-in selftest
    AFUNIXSocket.main(new String[0]);
  }
}
