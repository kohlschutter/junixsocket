/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
package org.newsclub.net.mysql;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import com.mysql.cj.jdbc.exceptions.CommunicationsException;

public class AFUNIXDatabaseSocketFactoryTest {

  @Test
  public void testDriverManagerConnectionToMissingServer() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();
    try (Connection unused = DriverManager.getConnection(
        "jdbc:mysql://localhost/db?socketFactory=org.newsclub.net.mysql.AFUNIXDatabaseSocketFactory&junixsocket.file="
            + addr.getPath())) {
      fail(
          "Should have thrown an exception since we're trying to connect to a non-existing database server");
    } catch (CommunicationsException e) {
      // expected
    }
  }

  @Test
  public void testConnectTimeout() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();
    try (AFUNIXServerSocket serverSocket = AFUNIXServerSocket.newInstance()) {
      serverSocket.bind(addr, 1);

      AFUNIXDatabaseSocketFactory sf = new AFUNIXDatabaseSocketFactory();

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        try {
          sf.connect("localhost", 0, new Properties());
        } catch (IOException ignore) {
          // ignore
        }
      });
    }
  }

}
