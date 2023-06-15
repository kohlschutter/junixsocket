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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.SocketTestBase;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement({
    AFSocketCapability.CAPABILITY_UNIX_DOMAIN, AFSocketCapability.CAPABILITY_ABSTRACT_NAMESPACE})
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION",
    "NM_SAME_SIMPLE_NAME_AS_SUPERCLASS"})
public class AbstractNamespaceTest extends SocketTestBase<AFUNIXSocketAddress> {

  public AbstractNamespaceTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  private void testBind(AFUNIXSocketAddress addr) throws Exception {
    testBind(addr, addr);
  }

  private void testBind(AFUNIXSocketAddress addr, AFUNIXSocketAddress expected) throws Exception {
    try (AFUNIXServerSocket serverSocket = (AFUNIXServerSocket) newServerSocket()) {
      serverSocket.bind(addr);

      CompletableFuture<AFUNIXSocketAddress> cf = CompletableFuture.supplyAsync(() -> {
        AFUNIXSocket socket;
        try {
          socket = AFUNIXSocket.connectTo(addr);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
        return socket.getRemoteSocketAddress();
      });
      AFUNIXSocket accept = serverSocket.accept();
      assertEquals(accept.getLocalSocketAddress(), cf.get());
      assertEquals(expected, cf.get());
    }
  }

  @Test
  public void testBind() throws Exception {
    testBind(AFUNIXSocketAddress.inAbstractNamespace("juxtest-" + UUID.randomUUID()));
  }

  @Test
  public void testBindTrailingZeroes() throws Exception {
    // trailing zeroes should be treated like other bytes
    testBind(AFUNIXSocketAddress.of(new byte[] {0, 10, 0}));
    testBind(AFUNIXSocketAddress.of(new byte[] {0, 10, 0, 0}));
    testBind(AFUNIXSocketAddress.of(new byte[] {0, 'J', 0, 'U'}));

    // any sequence of 0's -> null
    testBind(AFUNIXSocketAddress.of(new byte[] {0, 0, 0}), null);
  }

  @Test
  public void testBindLongAbstractAddress() throws Exception {
    byte[] addr = new byte[108];
    addr[1] = '1';
    addr[79] = 'X';
    testBind(AFUNIXSocketAddress.of(addr));
  }
}
