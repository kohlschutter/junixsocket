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
package org.newsclub.net.unix.ssl;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

import javax.net.ssl.SSLSocket;

/**
 * A wrapper for {@link SSLSocket} instances, with certain modifications to navigate in the real
 * world.
 * <p>
 * Modified behavior:
 * <ol>
 * <li>Ignore {@link SocketException}s thrown upon {@link #close()}, including
 * {@link #getInputStream()} and {@link #getOutputStream()}; however attempt closing the underlying
 * socket upon {@link #close()} if desired. On Android, "broken pipe" exceptions may be thrown upon
 * close.</li>
 * </ol>
 *
 * @author Christian Kohlschütter
 */
final class BuilderSSLSocket extends FilterSSLSocket {
  /**
   * The underlying {@link Socket}, or {@code null}.
   */
  private final Socket underlyingSocket;

  /**
   * Whether the underlying {@link Socket} should be forcibly closed upon {@link #close()} when
   * {@link SSLSocket#close()} throws an {@link IOException}.
   */
  private final boolean doCloseUnderlyingSocket;

  BuilderSSLSocket(SSLSocket socket, Socket underlyingSocket, boolean doCloseUnderlyingSocket) {
    super(socket);
    this.underlyingSocket = underlyingSocket;
    this.doCloseUnderlyingSocket = doCloseUnderlyingSocket;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new FilterInputStream(super.getInputStream()) {

      @Override
      public void close() throws IOException {
        try {
          super.close();
        } catch (SocketException e) {
          // BouncyCastle may throw "broken pipe" upon close
          // ignore
        }
      }
    };
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return new FilterOutputStream(super.getOutputStream()) {

      @Override
      public void close() throws IOException {
        try {
          super.close();
        } catch (SocketException e) {
          // BouncyCastle may throw "broken pipe" upon close
          // ignore
        }
      }
    };
  }

  @Override
  public synchronized void close() throws IOException {
    try {
      super.close();
    } catch (SocketException e) {
      // BouncyCastle may throw "broken pipe" upon close
      // ignore, but make sure we close the underlying socket if desired
      if (doCloseUnderlyingSocket) {
        Socket s = underlyingSocket;
        if (s != null) {
          try {
            s.close();
          } catch (IOException e1) {
            e1.addSuppressed(e);
            throw e1;
          }
        }
      }
    }
  }
}
