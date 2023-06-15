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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;

import org.newsclub.net.unix.AFUNIXSocket;

/**
 * A multi-threaded unix socket server that simply reads all input, byte per byte, not doing
 * anything else with it.
 *
 * @author Christian Kohlschütter
 */
public final class SendFileHandleServer extends DemoServerBase {
  private final File file;

  public SendFileHandleServer(SocketAddress listenAddress, File file) {
    super(listenAddress);
    this.file = file;
  }

  @Override
  protected void doServeSocket(Socket socket) throws IOException {
    if (!(socket instanceof AFUNIXSocket)) {
      throw new UnsupportedOperationException("File handles can only be sent via UNIX sockets");
    }
    doServeSocket((AFUNIXSocket) socket);
  }

  protected void doServeSocket(AFUNIXSocket socket) throws IOException {
    try (InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        FileInputStream fin = new FileInputStream(file)) {
      socket.setOutboundFileDescriptors(fin.getFD());
      os.write("FD sent via ancillary message.".getBytes("UTF-8"));
    }
  }
}
