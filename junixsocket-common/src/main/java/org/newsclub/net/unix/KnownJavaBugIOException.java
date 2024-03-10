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
 * Thrown when a known Java/JRE/JDK bug was encountered.
 *
 * @author Christian Kohlschütter
 */
public class KnownJavaBugIOException extends IOException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs an {@code KnownJDKBugIOException} with {@code null} as its error detail message.
   */
  public KnownJavaBugIOException() {
    super();
  }

  /**
   * Constructs an {@code IOException} with the specified detail message and cause.
   *
   * @param message The message.
   * @param cause The cause.
   */
  public KnownJavaBugIOException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs an {@code IOException} with the specified detail message.
   *
   * @param message The message.
   */
  public KnownJavaBugIOException(String message) {
    super(message);
  }

  /**
   * Constructs an {@code IOException} with the specified cause and {@code null} as its error detail
   * message.
   *
   * @param cause The cause.
   */
  public KnownJavaBugIOException(Throwable cause) {
    super(cause);
  }
}
