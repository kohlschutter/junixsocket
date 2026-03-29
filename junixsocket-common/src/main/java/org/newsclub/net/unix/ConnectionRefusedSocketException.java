/*
 * junixsocket
 *
 * Copyright 2009-2026 Christian Kohlschütter
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

import java.net.SocketException;

/**
 * A {@link SocketException} indicating that a socket was not bound/accepting requests when
 * attempting to connect.
 *
 * @author Christian Kohlschütter
 */
public final class ConnectionRefusedSocketException extends SocketException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@link ConnectionRefusedSocketException}.
   */
  public ConnectionRefusedSocketException() {
    super();
  }
}
