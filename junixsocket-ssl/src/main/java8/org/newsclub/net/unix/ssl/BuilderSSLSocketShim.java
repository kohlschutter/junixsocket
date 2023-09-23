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
package org.newsclub.net.unix.ssl;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketOption;
import java.util.Set;

import javax.net.ssl.SSLSocket;

/**
 * A shim class that supports some methods that are only available with Java 9 or newer; this is the
 * Java 8 version without these methods.
 *
 * See {@code src/main/java/...} for the Java 9 version of this class.
 *
 * @author Christian Kohlschütter
 */
abstract class BuilderSSLSocketShim extends SSLSocket {
  /**
   * The wrapped {@link SSLSocket}.
   */
  protected final SSLSocket sslSocket;

  /**
   * The underlying {@link Socket}, or {@code null}.
   */
  protected final Socket underlyingSocket;

  /**
   * Whether the underlying {@link Socket} should be forcibly closed upon {@link #close()} when
   * {@link SSLSocket#close()} throws an {@link IOException}.
   */
  protected final boolean doCloseUnderlyingSocket;

  BuilderSSLSocketShim(SSLSocket socket, Socket underlyingSocket, boolean doCloseUnderlyingSocket) {
    super();
    this.sslSocket = socket;
    this.underlyingSocket = underlyingSocket;
    this.doCloseUnderlyingSocket = doCloseUnderlyingSocket;
  }
}
