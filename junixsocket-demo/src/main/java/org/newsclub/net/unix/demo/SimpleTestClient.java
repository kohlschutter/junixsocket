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
package org.newsclub.net.unix.demo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * A simple demo client.
 *
 * Reads a server greeting string, then sends a "Hello Server" string as a response.
 *
 * Finally, reads integers (via {@link DataInputStream}), then sends twice the sent value each,
 * unless a "-123" magic number is read to indicate the end of the conversation.
 *
 * @author Christian Kohlschütter
 * @see SimpleTestServer
 */
public class SimpleTestClient {
  public static void main(String[] args) throws IOException {
    final File socketFile = new File(new File(System.getProperty("java.io.tmpdir")),
        "junixsocket-test.sock");

    boolean connected = false;
    try (AFUNIXSocket sock = AFUNIXSocket.connectTo(AFUNIXSocketAddress.of(socketFile));
        InputStream is = sock.getInputStream(); //
        OutputStream os = sock.getOutputStream();
        DataInputStream din = new DataInputStream(is);
        DataOutputStream dout = new DataOutputStream(os);) {
      System.out.println("Connected");
      connected = true;

      byte[] buf = new byte[128];

      int read = is.read(buf);
      System.out.println("Server says: " + new String(buf, 0, read, "UTF-8"));

      System.out.println("Replying to server...");
      os.write("Hello Server".getBytes("UTF-8"));
      os.flush();

      System.out.println("Now reading numbers from the server...");
      while (!Thread.interrupted()) {
        int number = din.readInt();
        if (number == -123) {
          // by convention of this demo, if the number is -123, we stop.
          // If we don't do this, we'll get an EOFException upon the next unsuccessful read.
          break;
        }
        System.out.println(number);

        int ourNumber = number * 2;

        System.out.println("Sending back " + ourNumber);
        dout.writeInt(ourNumber);
      }
    } catch (SocketException e) {
      if (!connected) {
        System.out.println("Cannot connect to server. Have you started it?");
        System.out.println();
      }
      throw e;
    }

    System.out.println("End of communication.");
  }
}
