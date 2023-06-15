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
package org.newsclub.net.unix.demo.server;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;

import org.newsclub.net.unix.demo.DemoHelper;
import org.newsclub.net.unix.server.SocketServer;

/**
 * A demo program to configure and run several {@link SocketServer} demos from the command line.
 *
 * @author Christian Kohlschütter
 */
public final class SocketServerDemo {
  public static void main(String[] args) throws IOException, InterruptedException {
    final DemoServerBase demoServer;

    String demo = DemoHelper.getPropertyValue("demo", null, "echo, null, zero, chargen, send-fd");
    if (demo == null) {
      demoServer = null;
    } else {
      String socketName = DemoHelper.getPropertyValue("socket", "/tmp/junixsocket-" + demo
          + ".sock", "/tmp/test.sock or localhost:1234");

      final SocketAddress listenAddress = DemoHelper.socketAddress(socketName);
      System.out.println("Listen address: " + listenAddress);

      switch (demo) {
        case "echo":
          demoServer = new EchoServer(listenAddress);
          break;
        case "null":
          demoServer = new NullServer(listenAddress);
          break;
        case "zero":
          demoServer = new ZeroServer(listenAddress);
          break;
        case "chargen":
          boolean fast = DemoHelper.getPropertyValue("fast", "true", "true/false",
              Boolean::valueOf);

          demoServer = new ChargenServer(listenAddress, fast);
          break;
        case "send-fd":
          File file = DemoHelper.getPropertyValue("file", null, "path to file", File::new);

          demoServer = new SendFileHandleServer(listenAddress, file);
          break;
        default:
          demoServer = null;
          break;
      }
    }

    if (demoServer == null) {
      System.out.flush();
      System.err.println("You need to specify a valid demo to run.");
      return;
    }

    demoServer.setMaxConcurrentConnections(DemoHelper.getPropertyValue("maxConcurrentConnections",
        "10", "1", Integer::parseInt));
    demoServer.setServerTimeout(DemoHelper.getPropertyValue("serverTimeout", "0", "(time in ms)",
        Integer::parseInt));
    demoServer.setSocketTimeout(DemoHelper.getPropertyValue("socketTimeout", "60000",
        "(time in ms)", Integer::parseInt));
    demoServer.setServerBusyTimeout(DemoHelper.getPropertyValue("serverBusyTimeout", "1000",
        "(time in ms)", Integer::parseInt));

    demoServer.start();
  }
}
