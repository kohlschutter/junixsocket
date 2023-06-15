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
package org.newsclub.net.unix.demo.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * A client that just reads and echoes the data to stdout.
 */
public class ReadClient extends DemoClientBase {
  @Override
  protected void handleSocket(Socket socket) throws IOException {
    try (InputStream in = socket.getInputStream()) {
      byte[] buf = new byte[socket.getReceiveBufferSize()];
      int read;

      while ((read = in.read(buf)) != -1) {
        System.out.write(buf, 0, read);
      }
    }
  }
}
