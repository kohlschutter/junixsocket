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

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketException;

/**
 * A simple demo client.
 * 
 * @author Christian Kohlschütter
 * @see SimpleTestServer
 */
public class SimpleTestClient {
  public static void main(String[] args) throws IOException {
    final File socketFile =
        new File(new File(System.getProperty("java.io.tmpdir")), "junixsocket-test.sock");

    try (AFUNIXSocket sock = AFUNIXSocket.newInstance()) {
      try {
        sock.connect(new AFUNIXSocketAddress(socketFile));
      } catch (AFUNIXSocketException e) {
        System.out.println("Cannot connect to server. Have you started it?");
        System.out.flush();
        throw e;
      }
      System.out.println("Connected");

      try (InputStream is = sock.getInputStream(); //
          OutputStream os = sock.getOutputStream();) {

        byte[] buf = new byte[128];

        int read = is.read(buf);
        System.out.println("Server says: " + new String(buf, 0, read));

        System.out.println("Replying to server...");
        os.write("Hello Server".getBytes());
        os.flush();
      }
    }

    System.out.println("End of communication.");
  }
}
