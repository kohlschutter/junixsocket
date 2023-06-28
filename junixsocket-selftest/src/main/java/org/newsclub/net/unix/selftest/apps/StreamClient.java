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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.newsclub.net.unix.AFAddressFamily;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;

// CPD-OFF

public class StreamClient {
  private static void printHelp() {
    System.err.println("Syntax: java " + StreamClient.class.getName() + " <URI>");
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

    ExecutorService exc = Executors.newCachedThreadPool();
    try (AFSocket<?> sock = address.newConnectedSocket();) {
      sock.setReceiveBufferSize(8192);

      Future<Long> sentFuture = exc.submit(() -> {
        // System.in may be a BufferedInputStream, so let's pass the proper stream
        try (InputStream in = new FileInputStream(FileDescriptor.in)) {
          return sock.getOutputStream().transferFrom(in);
        }
      });
      // /* long received = */ sock.getInputStream().transferTo(System.out); // Java 9+
      /* long received = */ IOUtil.transfer(sock.getInputStream(), System.out);

      /* long sent = */ sentFuture.get();
      exc.shutdown();
    }
  }
}
