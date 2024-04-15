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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.SocketClosedException;
import org.newsclub.net.unix.ThreadUtil;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public class MassiveParallelTest extends
    org.newsclub.net.unix.MassiveParallelTest<AFUNIXSocketAddress> {

  protected MassiveParallelTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.AvoidCatchingThrowable"})
  public void testAcceptConnect() throws Exception {
    AFUNIXSocketAddress listenAddr = AFUNIXSocketAddress.ofNewTempFile();

    final int nProc = Runtime.getRuntime().availableProcessors();
    final int numServerThreads = nProc;
    final int numConnections = 100 * nProc;

    try (AFUNIXServerSocket serverSocket = AFUNIXServerSocket.newInstance()) {
      serverSocket.bind(listenAddr, numServerThreads);

      ExecutorService esServers = Executors.newFixedThreadPool(numServerThreads);
      ExecutorService esClients = ThreadUtil.newVirtualThreadPerTaskExecutorIfPossible();

      CountDownLatch cl = new CountDownLatch(numConnections);

      long nanos = System.nanoTime();

      for (int i = 0; i < numServerThreads; i++) {
        esServers.submit(() -> {
          try {
            while (cl.getCount() > 0) {
              try {
                AFUNIXSocket socket = serverSocket.accept();
                try (OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream()) {
                  out.write("Hello".getBytes(StandardCharsets.UTF_8));
                  out.flush();
                  assertEquals(0, in.read());
                }
              } catch (SocketClosedException e) {
                // ignore
              }
            }
          } catch (Throwable e) {
            // FIXME
            e.printStackTrace();
          }
        });
      }

      for (int i = 0; i < numConnections; i++) {
        esClients.submit(() -> {
          try {
            AFUNIXSocket socket = AFUNIXSocket.newInstance();
            while (!Thread.interrupted()) {
              try {
                socket.connect(listenAddr, 0);
                break;
              } catch (SocketException e) {
                // try again
              }
            }
            try (InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream()) {
              for (int k = 0; k < 5; k++) {
                assertNotEquals(-1, in.read());
              }
              out.write(0);
              out.flush();
            }
          } catch (Throwable e) {
            // FIXME
            e.printStackTrace();
          } finally {
            cl.countDown();
          }
        });
      }

      esClients.shutdown();
      esClients.awaitTermination(10, TimeUnit.SECONDS);
      esServers.shutdownNow();

      assertEquals(0, cl.getCount());
      cl.await();

      nanos = System.nanoTime() - nanos;
      System.out.println(nanos / 1_000_000.0f + " ms");
    }
  }
}
