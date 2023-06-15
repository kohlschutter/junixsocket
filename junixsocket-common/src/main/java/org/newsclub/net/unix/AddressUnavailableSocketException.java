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

import java.net.SocketException;

/**
 * A {@link SocketException} that may be thrown upon some "address unavailable" condition from
 * native code (e.g., EADDRNOTAVAIL is returned).
 *
 * @author Christian Kohlschütter
 */
public class AddressUnavailableSocketException extends InvalidSocketException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@link AddressUnavailableSocketException}.
   */
  public AddressUnavailableSocketException() {
    super();
  }

  /**
   * Constructs a new {@link AddressUnavailableSocketException}.
   *
   * @param msg The error message.
   */
  public AddressUnavailableSocketException(String msg) {
    super(msg);
  }
}
