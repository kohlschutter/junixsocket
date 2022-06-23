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
package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Verifies that accept properly times out when an soTimeout was specified.
 * 
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class AcceptTimeoutTest<A extends SocketAddress> extends SocketTestBase<A> {
  private static final int TIMING_INACCURACY_MILLIS = 5000;

  protected AcceptTimeoutTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testCatchTimeout() throws Exception {
    final int timeoutMillis = 500;
    assertTimeoutPreemptively(Duration.ofMillis(5 * timeoutMillis), () -> {
      try (ServerSocket sock = startServer()) {
        long time = System.currentTimeMillis();
        sock.setSoTimeout(timeoutMillis);
        long actualTimeout = sock.getSoTimeout();
        if (actualTimeout == 0) {
          // timeout not supported. So far we know this is only true for z/OS
          if ("z/OS".equals(System.getProperty("os.name"))) {
            return;
          }
        }
        assertTrue(Math.abs(timeoutMillis - actualTimeout) <= TIMING_INACCURACY_MILLIS,
            "We should roughly get the same timeout back that we set before, but was "
                + actualTimeout + " instead of " + timeoutMillis);
        try (Socket socket = sock.accept()) {
          fail("Did not receive " + SocketTimeoutException.class.getName() + "; socket=" + socket);
        } catch (SocketException | SocketTimeoutException e) {
          // expected
          time = System.currentTimeMillis() - time;

          assertTrue(Math.abs(time - timeoutMillis) <= TIMING_INACCURACY_MILLIS,
              "Timeout not properly honored. Exception thrown after " + time + "ms vs. expected "
                  + timeoutMillis + "ms");
        }
      }
    });
  }

  @Test
  public void testTimeoutAfterDelay() throws Exception {
    final int timeoutMillis = 5000;
    assertTimeoutPreemptively(Duration.ofMillis(2 * timeoutMillis), () -> {
      try (ServerSocket serverSock = startServer()) {
        final int connectDelayMillis = 50;
        serverSock.setSoTimeout(timeoutMillis);

        long actualTimeout = serverSock.getSoTimeout();
        if (actualTimeout == 0) {
          // timeout not supported. So far we know this is only true for z/OS
          if ("z/OS".equals(System.getProperty("os.name"))) {
            return;
          }
        }
        assertTrue(Math.abs(timeoutMillis - actualTimeout) <= 10,
            "We should roughly get the same timeout back that we set before, but was "
                + actualTimeout + " instead of " + timeoutMillis);

        new Thread() {
          private final Socket socket = newSocket();

          {
            setDaemon(true);
          }

          @Override
          public void run() {
            try {
              Thread.sleep(connectDelayMillis);
            } catch (InterruptedException e) {
              return;
            }

            try {
              socket.connect(serverSock.getLocalSocketAddress());
            } catch (IOException e) {
              e.printStackTrace();
            }

          }
        }.start();

        long time = System.currentTimeMillis();
        try (Socket socket = serverSock.accept();) {
          assertNotNull(socket);
        }
        time = System.currentTimeMillis() - time;

        assertTrue(time >= connectDelayMillis && (time < timeoutMillis || (time
            - connectDelayMillis) <= TIMING_INACCURACY_MILLIS),
            "Timeout not properly honored. Accept succeeded after " + time + "ms vs. expected "
                + timeoutMillis + "ms");
      }
    });
  }

  @Test
  public void testAcceptWithoutBindToService() throws Exception {
    ServerSocket ss = newServerSocket();
    assertThrows(SocketException.class, () -> ss.accept());
  }
}
