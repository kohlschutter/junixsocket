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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

@SuppressWarnings("all")
public class VirtualThreadConnectTest {
  @Test
  public void testBlockingReadCompletesForAllClients() throws Exception {
    final int clients = 64;
    final int serverDelayMillis = 20;
    final int timeoutSeconds = 60;
    final byte payload = 0x2A;
    final int connectTimeoutMs = 100;

    Path socketPath = Files.createTempFile("junixsocket-vt-", ".sock");

    var failures = new AtomicInteger(0);
    var completed = new CountDownLatch(clients);

    try (var server = AFUNIXServerSocket.newInstance()) {
      var address = AFUNIXSocketAddress.of(socketPath.toFile());
      server.bind(address);

      var acceptor = Executors.newSingleThreadExecutor();
      var handlers = Executors.newCachedThreadPool();

      try {
        for (int i = 0; i < clients; i++) {
          acceptor.submit(() -> {
            try {
              var clientSocket = server.accept();
              handlers.submit(() -> {
                try (clientSocket) {
                  Thread.sleep(serverDelayMillis);
                  clientSocket.getOutputStream().write(payload);
                  clientSocket.getOutputStream().flush();
                } catch (Throwable t) {
                  t.printStackTrace();
                  failures.incrementAndGet();
                }
              });
            } catch (IOException e) {
              failures.incrementAndGet();
            }
          });
        }

        long startNanos = System.nanoTime();
        try (var vexec = Executors.newVirtualThreadPerTaskExecutor()) {
          for (int i = 0; i < clients; i++) {
            vexec.submit(() -> {
              try (var socket = AFUNIXSocket.newInstance()) {
                socket.connect(address, connectTimeoutMs);
                int result = socket.getInputStream().read();
                if (result != payload) {
                  failures.incrementAndGet();
                }
              } catch (Throwable t) {
                t.printStackTrace();
                failures.incrementAndGet();
              } finally {
                completed.countDown();
              }
            });
          }
        }

        boolean doneInTime = completed.await(timeoutSeconds, TimeUnit.SECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertTrue(doneInTime);
        assertEquals(0, failures.get());
      } finally {
        acceptor.shutdownNow();
        handlers.shutdownNow();
        acceptor.awaitTermination(5, TimeUnit.SECONDS);
        handlers.awaitTermination(5, TimeUnit.SECONDS);
      }
    } finally {
      Files.deleteIfExists(socketPath);
    }
  }
}
