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

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedByInterruptException;

import org.eclipse.jdt.annotation.NonNull;

/**
 * A {@link SocketException} indicating that a socket was closed by interrupt.
 *
 * @author Christian Kohlschütter
 */
final class SocketClosedByInterruptException extends SocketClosedException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@link SocketClosedByInterruptException}.
   */
  private SocketClosedByInterruptException() {
    super("Closed by interrupt");
  }

  static SocketClosedByInterruptException newInstanceAndClose(Closeable closeable) {
    Throwable suppressed = null;
    try {
      if (closeable != null) {
        closeable.close();
      }
    } catch (RuntimeException | IOException e) {
      suppressed = e;
    }
    SocketClosedByInterruptException exc = new SocketClosedByInterruptException();
    if (suppressed != null) {
      exc.addSuppressed(suppressed);
    }
    return exc;
  }

  public @NonNull ClosedByInterruptException asClosedByInterruptException() {
    ClosedByInterruptException exc = new ClosedByInterruptException();
    exc.initCause(this);
    return exc;
  }
}
