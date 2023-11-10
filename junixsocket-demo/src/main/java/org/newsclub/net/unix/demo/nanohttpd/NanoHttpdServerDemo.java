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
package org.newsclub.net.unix.demo.nanohttpd;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;

import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.demo.DemoHelper;
import org.newsclub.net.unix.demo.okhttp.OkHttpClientDemo;

import fi.iki.elonen.NanoHTTPD;

/**
 * Creates a {@link NanoHTTPD} server, bound to {@code /tmp/junixsocket-http-server.sock}.
 *
 * Http requests on that socket should return "Hello world from &lt;hostname&gt;".
 *
 * @author Christian Kohlschütter
 * @see OkHttpClientDemo
 */
public class NanoHttpdServerDemo extends NanoHTTPD {

  public NanoHttpdServerDemo(SocketAddress socketAddress) throws IOException {
    super(0);
    setServerSocketFactory(new ServerSocketFactory() {

      @Override
      public ServerSocket create() throws IOException {
        if (socketAddress instanceof AFSocketAddress) {
          return ((AFSocketAddress) socketAddress).newForceBoundServerSocket();
        } else {
          ServerSocket serverSocket = new ServerSocket();
          serverSocket.bind(socketAddress);
          return serverSocket;
        }
      }
    });
    System.out.println("Address: " + socketAddress);
    if (socketAddress instanceof AFUNIXSocketAddress) {
      System.out.println("Try: curl --unix-socket " + ((AFUNIXSocketAddress) socketAddress)
          .getPath() + " http://localhost/");
    }
  }

  public static void main(String[] args) throws IOException {
    SocketAddress addr = DemoHelper.parseAddress(args, //
        AFUNIXSocketAddress.of(new File("/tmp/junixsocket-http-server.sock")));

    new NanoHttpdServerDemo(addr).start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
  }

  @Override
  public Response serve(IHTTPSession session) {
    return newFixedLengthResponse("Hello world from " + getSystemHostname() + "\n");
  }

  private static String getSystemHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}
