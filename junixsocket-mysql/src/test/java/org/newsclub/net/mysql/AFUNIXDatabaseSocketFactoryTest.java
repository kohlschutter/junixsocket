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

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class AFUNIXDatabaseSocketFactoryTest extends MysqlSocketFactoryTestBase {

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

  @Override
  protected String socketFactory() {
    return "org.newsclub.net.mysql.AFUNIXDatabaseSocketFactory";
  }
}
