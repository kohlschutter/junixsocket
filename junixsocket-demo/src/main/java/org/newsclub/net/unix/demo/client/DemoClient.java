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
import java.net.SocketAddress;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.demo.DemoHelper;

/**
 * A demo program to configure and run several {@link AFSocket} client demos from the command line.
 *
 * @author Christian Kohlschütter
 */
public final class DemoClient {
  public static void main(String[] args) throws IOException, InterruptedException {
    final DemoClientBase demoClient;

    String demo = DemoHelper.getPropertyValue("demo", "read-write", "read, read-write, read-fd");
    String socketName = DemoHelper.getPropertyValue("socket", "/tmp/junixsocket.sock",
        "/tmp/test.sock or localhost:1234");

    if (demo == null) {
      demoClient = null;
    } else {
      switch (demo) {
        case "read":
          demoClient = new ReadClient();
          break;
        case "read-write":
          demoClient = new ReadWriteClient();
          break;
        case "read-fd":
          demoClient = new ReadFileHandleClient();
          break;
        default:
          demoClient = null;
          break;
      }
    }

    if (demoClient == null) {
      System.out.flush();
      System.err.println("You need to specify a valid demo to run.");
      return;
    }

    SocketAddress endpoint = DemoHelper.socketAddress(socketName);

    try {
      demoClient.connect(endpoint);
    } finally {
      demoClient.close();
    }
  }
}
