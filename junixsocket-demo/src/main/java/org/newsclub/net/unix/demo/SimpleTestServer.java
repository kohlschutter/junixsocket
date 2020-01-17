/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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
package org.newsclub.net.unix.demo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
public final class SimpleTestServer {
  private static final int MAX_NUMBER = 5;

  private SimpleTestServer() {
    throw new UnsupportedOperationException("No instances");
  }

  public static void main(String[] args) throws IOException {
    final File socketFile = new File(new File(System.getProperty("java.io.tmpdir")),
        "junixsocket-test.sock");
    System.out.println(socketFile);

    try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
      // server.setReuseAddress(false);
      server.bind(new AFUNIXSocketAddress(socketFile));
      System.out.println("server: " + server);

      while (!Thread.interrupted()) {
        System.out.println("Waiting for connection...");
        try (Socket sock = server.accept()) {
          System.out.println("Connected: " + sock);

          try (InputStream is = sock.getInputStream(); //
              OutputStream os = sock.getOutputStream()) {
            System.out.println("Saying hello to client " + os);
            os.write("Hello, dear Client".getBytes("UTF-8"));
            os.flush();

            byte[] buf = new byte[128];
            int read = is.read(buf);
            System.out.println("Client's response: " + new String(buf, 0, read, "UTF-8"));

            System.out.println("Now counting to 5...");
            DataOutputStream dout = new DataOutputStream(os);
            DataInputStream din = new DataInputStream(is);
            int number = 0;
            while (!Thread.interrupted()) {
              number++;
              System.out.println("write " + number);
              dout.writeInt(number);
              try {
                Thread.sleep(1000);
              } catch (InterruptedException e) {
                e.printStackTrace();
                break;
              }
              if (number > MAX_NUMBER) {
                System.out.println("write -123 (end of numbers)");
                dout.writeInt(-123); // in this demo, -123 is our magic number to indicate the end
                break;
              }

              // verify the number from the client
              // in the demo, the client just sends 2 * our number
              int theirNumber = din.readInt();
              System.out.println("received " + theirNumber);
              if (theirNumber != (number * 2)) {
                throw new IllegalStateException("Received the wrong number: " + theirNumber);
              }
            }
          }
        } catch (IOException e) {
          if (server.isClosed()) {
            throw e;
          } else {
            e.printStackTrace();
          }
        }
      }
    }
  }
}
