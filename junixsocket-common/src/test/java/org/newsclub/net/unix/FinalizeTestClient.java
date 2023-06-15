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
package org.newsclub.net.unix;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A potentially file descriptor-leaking client.
 *
 * See <a href="https://github.com/kohlschutter/junixsocket/pull/29">issue 29</a> for details.
 *
 * @see FinalizeTest
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public class FinalizeTestClient {
  @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED"})
  public static void main(String[] args) throws Exception {
    String socketType = System.getProperty("test.junixsocket.socket.type", "");
    String socketName = System.getProperty("test.junixsocket.socket", "");
    if (socketType.isEmpty() || socketName.isEmpty()) {
      throw new IllegalArgumentException(
          "Both test.junixsocket.socket and test.junixsocket.socket.type must be specified as system properties");
    }

    Socket socket;
    AFSocketAddress addr;
    if ("UNIX".equals(socketType)) {
      addr = AFUNIXSocketAddress.unwrap(socketName, 0);
    } else if ("TIPC".equals(socketType)) {
      addr = AFTIPCSocketAddress.unwrap(socketName, 0);
    } else if ("VSOCK".equals(socketType)) {
      addr = AFVSOCKSocketAddress.unwrap(socketName, 0);
    } else {
      throw new IllegalArgumentException("Unsupported socket type: " + socketType);
    }
    socket = AFSocket.connectTo(Objects.requireNonNull(addr));
    socket.getInputStream().read();
    while (true) {
      // create some pressure on GC
      new String("junixsocket".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
  }
}
