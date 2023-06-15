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

/**
 * Describes the "protocol" part of a socket.
 *
 * @author Christian Kohlschütter
 */
public enum AFSocketProtocol {
  /**
   * The "default" protocol.
   */
  DEFAULT(0);

  private final int id;

  AFSocketProtocol(int id) {
    this.id = id;
  }

  int getId() {
    return id;
  }
}
