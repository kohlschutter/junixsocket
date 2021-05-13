/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * Tests some otherwise uncovered methods of {@link AFUNIXSocket}.
 * 
 * @author Christian Kohlschütter
 */
public class AFUNIXServerSocketTest {

  @Test
  public void testSupported() throws Exception {
    assertTrue(AFUNIXServerSocket.isSupported());
  }

  @Test
  public void testCloseable() throws Exception {
    AtomicBoolean closed = new AtomicBoolean(false);
    @SuppressWarnings("resource")
    Closeable closeable = new Closeable() {

      @Override
      public void close() throws IOException {
        closed.set(true);
      }
    };

    try (AFUNIXServerSocket socket = AFUNIXServerSocket.newInstance()) {
      socket.addCloseable(null); // no-op
      socket.addCloseable(closeable);
      socket.removeCloseable(null); // no-op
      socket.removeCloseable(closeable);
    }
    assertFalse(closed.get());

    try (AFUNIXServerSocket socket = AFUNIXServerSocket.newInstance()) {
      socket.addCloseable(closeable);
    }
    assertTrue(closed.get());
  }

  @Test
  public void testBindBadArguments() throws Exception {
    try (AFUNIXServerSocket sock = AFUNIXServerSocket.newInstance()) {
      assertFalse(sock.isBound());
      assertThrows(IllegalArgumentException.class, () -> {
        sock.bind(null);
      });
      assertFalse(sock.isBound());
    }
    try (AFUNIXServerSocket sock = AFUNIXServerSocket.newInstance()) {
      assertFalse(sock.isBound());
      assertThrows(IllegalArgumentException.class, () -> {
        sock.bind(new InetSocketAddress("", 0));
      });
      assertFalse(sock.isBound());
    }
  }

  @Test
  public void testUnboundServerSocket() throws IOException {
    try (ServerSocket socket = AFUNIXServerSocket.newInstance()) {
      assertTrue(socket.getReceiveBufferSize() > 0);
    }
  }
}
