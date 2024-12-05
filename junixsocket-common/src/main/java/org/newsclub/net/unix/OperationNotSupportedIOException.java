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
package org.newsclub.net.unix;

import java.io.IOException;

/**
 * An {@link IOException} that may be thrown upon some "unsupported operation" condition from native
 * code, using a non-socket file handle, (e.g., ENOTSUP) is returned.
 *
 * @author Christian Kohlschütter
 * @see OperationNotSupportedSocketException
 */
public class OperationNotSupportedIOException extends IOException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@link OperationNotSupportedIOException}.
   */
  public OperationNotSupportedIOException() {
    super();
  }

  /**
   * Constructs a new {@link OperationNotSupportedIOException}.
   *
   * @param msg The error message.
   */
  public OperationNotSupportedIOException(String msg) {
    super(msg);
  }
}
