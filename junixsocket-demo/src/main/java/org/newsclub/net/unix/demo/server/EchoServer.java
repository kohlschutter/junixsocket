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
package org.newsclub.net.unix.demo.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * A multi-threaded unix socket server that simply echoes all input, byte per byte.
 *
 * @author Christian Kohlschütter
 */
public final class EchoServer extends DemoServerBase {
  public EchoServer(SocketAddress listenAddress) {
    super(listenAddress);
  }

  @Override
  protected void doServeSocket(Socket socket) throws IOException {
    int bufferSize = socket.getReceiveBufferSize();
    byte[] buffer = new byte[bufferSize];

    try (InputStream is = socket.getInputStream(); //
        OutputStream os = socket.getOutputStream()) {
      int read;
      while ((read = is.read(buffer)) != -1) {
        os.write(buffer, 0, read);
      }
    }
  }
}
