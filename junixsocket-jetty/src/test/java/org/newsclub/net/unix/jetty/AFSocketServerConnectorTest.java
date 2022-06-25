/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
package org.newsclub.net.unix.jetty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.time.Duration;

import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.domain.AFUNIXAddressSpecifics;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AFSocketServerConnectorTest {
  @BeforeAll
  public static void setUp() {
    System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty.server", "error");
  }

  @Test
  public void testServerAFUNIX() throws Exception {
    AFSocketAddress addr = (AFSocketAddress) AFUNIXAddressSpecifics.INSTANCE.newTempAddress();
    Server server1 = newServer(addr);
    checkConnection(addr);
    assertTrue(server1.isRunning());

    Server server2 = newServer(addr);

    // isDeleteOnClose is smart enough to not delete the wrong socket
    assertTrue(addr.getFile().exists());

    server1.join();
    assertFalse(server1.isRunning());
    server2.stop();
    server2.join();
    assertFalse(addr.getFile().exists()); // isDeleteOnClose=true by default
  }

  private void checkConnection(AFSocketAddress addr) throws Exception {
    OkHttpClient.Builder builder = new OkHttpClient.Builder() //
        .socketFactory(new AFSocketFactory.FixedAddressSocketFactory(addr)) //
        .callTimeout(Duration.ofMinutes(1));

    OkHttpClient client = builder.build();

    Request request = new Request.Builder().url("http://localhost/").build();
    try (Response response = client.newCall(request).execute()) {
      assertEquals(404, response.code());
      assertNotNull(response.header("Server"));

      ResponseBody body = response.body();
      assertNotNull(body);

      BufferedReader br = new BufferedReader(body.charStream());
      int l = 0;
      while (br.readLine() != null) {
        l++;
      }
      assertNotEquals(0, l);
    }
  }

  private static Server newServer(AFSocketAddress addr) throws Exception {
    Server server = new Server();

    // below code is based upon
    // https://www.eclipse.org/jetty/documentation/jetty-10/programming-guide/index.html

    // The number of acceptor threads.
    int acceptors = 1;

    // The number of selectors.
    int selectors = 1;

    // Create a ServerConnector instance.
    AFSocketServerConnector connector = new AFSocketServerConnector(server, acceptors, selectors,
        new HttpConnectionFactory());

    // The AFSocketAddress to listen to.
    connector.setListenSocketAddress(addr);

    // The accept queue size.
    connector.setAcceptQueueSize(128);

    // Try to automatically stop server if another instance reuses our address
    connector.setMayStopServer(true);

    server.addConnector(connector);
    server.start();

    return server;
  }
}
