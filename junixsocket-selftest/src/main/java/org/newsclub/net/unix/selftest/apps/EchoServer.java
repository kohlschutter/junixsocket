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
package org.newsclub.net.unix.selftest.apps;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFAddressFamily;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.server.AFSocketServer;

// CPD-OFF
public class EchoServer {
  private static void printHelp() {
    System.err.println("Syntax: java " + EchoServer.class.getName() + " <URI>");
    System.err.println();
    System.err.println("Supported schemes: " + AFAddressFamily.uriSchemes());
  }

  public static void main(String[] args) throws IOException, InterruptedException,
      ExecutionException {
    if (args.length == 0) {
      printHelp();
      System.exit(1);
      return;
    }

    AFSocketAddress address;
    try {
      address = SocketAddressUtil.parseAddress(args[0]);
    } catch (Exception e) {
      printHelp();
      System.err.println();
      e.printStackTrace();
      System.exit(1);
      return;
    }

    if (new AFSocketServer<AFSocketAddress>(address) {
      @Override
      protected void doServeSocket(AFSocket<?> sock) throws IOException {
        System.out.println("Connected: " + sock);
        long numBytes = -1;
        long time = System.currentTimeMillis();
        try {
          // numBytes = sock.getInputStream().transferTo(sock.getOutputStream()); // NOPMD
          numBytes = IOUtil.transfer(sock.getInputStream(), sock.getOutputStream());
        } finally {
          time = System.currentTimeMillis() - time;
          System.out.println("Disconnected: " + sock + "; echoed bytes: " + (numBytes == -1
              ? "unknown" : numBytes) + "; speed: " + ((numBytes == -1 || time == 0) ? "unknown"
                  : (numBytes * 1000L / time) + "/s"));
        }
      }
    }.startAndWaitToBecomeReady(10, TimeUnit.SECONDS)) {
      System.out.println("Server ready, listening on " + address);
    }
  }
}
