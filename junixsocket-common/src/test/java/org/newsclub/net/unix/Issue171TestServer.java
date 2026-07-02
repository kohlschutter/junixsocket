/*
 * junixsocket
 *
 * Copyright 2009-2026 Christian Kohlschütter
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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.Nullable;

final class Issue171TestServer {
  private final AtomicBoolean keepRunning = new AtomicBoolean(true);
  private final ServerSocketChannel ssc;
  private final SocketAddress sa;
  private final Map<Channel, Session> sessions = new HashMap<>();

  private static final class Session {
    int emptyReads = 0;
    final ByteBuffer buffer = ByteBuffer.allocate(1024);
  }

  Issue171TestServer(ServerSocketChannel ssc, SocketAddress sa) {
    this.ssc = ssc;
    this.sa = sa;
  }

  @SuppressWarnings("PMD.CognitiveComplexity")
  CompletableFuture<@Nullable Throwable> start() throws IOException {
    ssc.bind(sa);
    ssc.configureBlocking(false);

    Selector selector = ssc.provider().openSelector();
    ssc.register(selector, SelectionKey.OP_ACCEPT);

    CompletableFuture<@Nullable Throwable> server = CompletableFuture.supplyAsync(() -> {
      try {
        while (!Thread.interrupted() && keepRunning.get()) {
          int select = selector.select();
          if (select == 0) {
            continue;
          }

          for (Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); iter.hasNext();) {
            SelectionKey key = iter.next();
            iter.remove();

            try {
              if (key.isAcceptable()) {
                handleAccept(selector);
              } else if (key.isReadable()) {
                handleRead(key);
              }
            } catch (Exception e) {
              stop();
              key.cancel();
              try {
                key.channel().close();
              } catch (IOException ignore) {
                // ignore
              }
              return e;
            }
          }

        }
      } catch (Throwable t) {
        return t;
      }
      return keepRunning.get() ? new InterruptedIOException() : null;
    });

    return server;
  }

  void stop() {
    keepRunning.set(false);
  }

  private void handleAccept(Selector selector) throws IOException {
    SocketChannel client = ssc.accept();
    if (client == null) {
      return;
    }
    client.configureBlocking(false);
    client.register(selector, SelectionKey.OP_READ);
    sessions.put(client, new Session());
  }

  private void handleRead(SelectionKey key) throws IOException {
    @SuppressWarnings("resource")
    SocketChannel client = (SocketChannel) key.channel();

    Session session = sessions.get(client);
    if (session == null) {
      client.close();
      return;
    }

    session.buffer.clear();
    while (!Thread.interrupted()) {
      if (!session.buffer.hasRemaining()) {
        break;
      }

      int read = client.read(session.buffer);
      if (read < 0) {
        if (read < -1) {
          fail("Unexpected return value for read: " + read);
        }
        closeSession(client);
        return;
      }

      if (read == 0) {
        if (++session.emptyReads >= 3) {
          fail("Too many empty reads; aborting");
        }
      }
    }
  }

  private void closeSession(SocketChannel client) throws IOException {
    sessions.remove(client);
    client.close();

    if (sessions.isEmpty()) {
      stop();
    }
  }
}