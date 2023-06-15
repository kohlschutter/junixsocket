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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

/**
 * A simple bidirectional Unix socket client that reads from/writes to stdin/stdout.
 */
public final class ReadWriteClient extends DemoClientBase {
  @Override
  protected void handleSocket(Socket socket) throws IOException {
    try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
      final byte[] readBuf = new byte[socket.getReceiveBufferSize()];
      final byte[] writeBuf = new byte[socket.getSendBufferSize()];

      final CountDownLatch cdl = new CountDownLatch(2);

      new Thread() {
        @Override
        public void run() {
          int bytes;
          try {
            while ((bytes = in.read(readBuf)) != -1) {
              System.out.write(readBuf, 0, bytes);
              System.out.flush();
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
          cdl.countDown();
        }
      }.start();

      new Thread() {
        @Override
        public void run() {
          int bytes;
          try {
            while ((bytes = System.in.read(writeBuf)) != -1) {
              out.write(writeBuf, 0, bytes);
              out.flush();
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
          cdl.countDown();
        }
      }.start();

      cdl.await();
    } catch (InterruptedException e) {
      throw (InterruptedIOException) new InterruptedIOException().initCause(e);
    }
  }
}
