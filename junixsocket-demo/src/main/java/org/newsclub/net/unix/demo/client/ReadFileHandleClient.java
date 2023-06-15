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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import org.newsclub.net.unix.AFUNIXSocket;

/**
 * A client that reads the contents of file descriptors that are sent as ancillary messages.
 *
 * The actual in-band data that is received is silently ignored.
 */
public class ReadFileHandleClient extends DemoClientBase {
  @Override
  protected void handleSocket(Socket socket) throws IOException {
    if (!(socket instanceof AFUNIXSocket)) {
      throw new UnsupportedOperationException("File handles can only be sent via UNIX sockets");
    }
    handleSocket((AFUNIXSocket) socket);
  }

  protected void handleSocket(AFUNIXSocket socket) throws IOException {
    // set to a reasonable size
    socket.setAncillaryReceiveBufferSize(1024);

    try (InputStream in = socket.getInputStream()) {
      byte[] buf = new byte[socket.getReceiveBufferSize()];

      while (in.read(buf) != -1) {
        FileDescriptor[] descriptors = socket.getReceivedFileDescriptors();
        if (descriptors != null) {
          for (FileDescriptor fd : descriptors) {
            handleFileDescriptor(fd);
          }
        }
      }
    }
  }

  private void handleFileDescriptor(FileDescriptor fd) throws IOException {
    try (FileInputStream fin = new FileInputStream(fd)) {
      byte[] buf = new byte[4096];
      int read;
      while ((read = fin.read(buf)) != -1) {
        System.out.write(buf, 0, read);
      }
      System.out.flush();
    }
  }
}
