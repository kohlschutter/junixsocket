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

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.SocketClosedException;

/**
 * A simple demo server.
 *
 * Sends a hello message (as a string), then reads back a response string.
 *
 * Finally, sends integers (via {@link DataOutputStream}) from 1 to 5, expects an integer response
 * of twice the sent value each, then sends a "-123" magic number to indicate the end of the
 * conversation.
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
      /*
       * Uncomment the code below to change the bind behavior:
       *
       * By default ("reuseAddress" is true), attempting to bind while another server is running on
       * the same address will cause the first server to terminate, and the new server will take
       * over the address. Depending on the operating system, this may involve connecting to the
       * first server in order to "wake up" the accept call.
       *
       * In this demo code, we use AFSocket.getConnectionStatus to see if the accepted connection is
       * alive by sending
       *
       * When "reuseAddress" is false, attempting to bind while another server is running won't
       * disrupt the first connection. The second bind will throw a SocketException instead.
       *
       * NOTE: "reuseAddress=true" may not yet be supported on certain operating systems, such as
       * IBM i and z/OS. On these platforms, the behavior is as if "reuseAddress=false". Please
       * reach out by filing an issue on https://github.com/kohlschutter/junixsocket/issues if this
       * is a problem for you.
       */
      // server.setReuseAddress(false);

      server.bind(AFUNIXSocketAddress.of(socketFile));
      System.out.println("server: " + server);

      while (!Thread.interrupted() && !server.isClosed()) {
        System.out.println("Waiting for connection...");

        boolean remoteReady = false;
        try (AFUNIXSocket sock = server.accept();
            InputStream is = sock.getInputStream();
            OutputStream os = sock.getOutputStream();
            DataOutputStream dout = new DataOutputStream(os);
            DataInputStream din = new DataInputStream(is);) {
          remoteReady = true;
          System.out.println("Connected: " + sock);

          // This check is optional. Without it, the below write may throw a "Broken pipe" exception
          // if the remote connection was closed right after connect.
          //
          // The check involves sending a zero-byte message to the peer, and catching exceptions
          // as we go. Note that this is deliberately not automated to allow code perform and detect
          // "(port) knocking".
          if (sock.checkConnectionClosed()) {
            System.out.println("Peer closed socket right after connecting");
            continue;
          }

          System.out.println("Saying hello to client " + sock);
          os.write("Hello, dear Client".getBytes("UTF-8"));
          os.flush();

          byte[] buf = new byte[128];
          int read = is.read(buf);
          System.out.println("Client's response: " + new String(buf, 0, read, "UTF-8"));

          System.out.println("Now counting to " + MAX_NUMBER + "...");
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
            if (number >= MAX_NUMBER) {
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
        } catch (SocketClosedException e) {
          if (!remoteReady) {
            // ignore -- the remote connection terminated during accept or when trying to get the
            // input/output streams
          } else {
            // unexpected
            e.printStackTrace();
          }
        } catch (IOException e) {
          // unexpected
          e.printStackTrace();
        }
      }
    } finally {
      System.out.println("Server terminated");
    }
  }
}
