/**
 * junixsocket
 *
 * Copyright (c) 2009,2014 Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix.demo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * A simple demo server.
 * 
 * @author Christian Kohlschütter
 * @see SimpleTestClient
 */
public class SimpleTestServer {
  public static void main(String[] args) throws IOException {
    final File socketFile =
        new File(new File(System.getProperty("java.io.tmpdir")), "junixsocket-test.sock");

    try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
      server.bind(new AFUNIXSocketAddress(socketFile));
      System.out.println("server: " + server);

      while (!Thread.interrupted()) {
        System.out.println("Waiting for connection...");
        try (Socket sock = server.accept()) {
          System.out.println("Connected: " + sock);

          try (InputStream is = sock.getInputStream(); //
              OutputStream os = sock.getOutputStream()) {
            System.out.println("Saying hello to client " + os);
            os.write("Hello, dear Client".getBytes());
            os.flush();

            byte[] buf = new byte[128];
            int read = is.read(buf);
            System.out.println("Client's response: " + new String(buf, 0, read));
          }
        }
      }
    }
  }
}
