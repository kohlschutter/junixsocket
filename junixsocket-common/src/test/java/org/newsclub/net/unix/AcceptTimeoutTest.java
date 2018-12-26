/**
 * junixsocket
 *
 * Copyright 2009-2018 Christian Kohlschütter
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

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Verifies that accept properly times out when an soTimeout was specified.
 * 
 * @author Christian Kohlschütter
 */
public class AcceptTimeoutTest extends SocketTestBase {
  private static final int timingInaccuracyMillis = 30;

  public AcceptTimeoutTest() throws IOException {
    super();
  }

  @Test
  void testCatchTimeout() throws Exception {
    final int timeoutMillis = 100;
    assertTimeout(Duration.ofMillis(2 * timeoutMillis), () -> {
      try (AFUNIXServerSocket sock = startServer()) {
        long time = System.currentTimeMillis();
        sock.setSoTimeout(timeoutMillis);
        try {
          sock.accept();
          fail("Did not receive " + SocketTimeoutException.class.getName());
        } catch (SocketTimeoutException e) {
          // expected
          time = System.currentTimeMillis() - time;

          assertTrue(time >= timeoutMillis && (time - timeoutMillis) <= timingInaccuracyMillis,
              "Timeout not properly honored. Exception thrown after " + time + "ms vs. expected "
                  + timeoutMillis + "ms");
        }
      }
    });
  }

  @Test
  void testTimeoutAfterDelay() throws Exception {
    final int timeoutMillis = 250;
    assertTimeout(Duration.ofMillis(2 * timeoutMillis), () -> {
      try (final AFUNIXServerSocket sock = startServer()) {
        final int connectDelayMillis = 50;
        sock.setSoTimeout(timeoutMillis);

        new Thread() {
          AFUNIXSocket socket = AFUNIXSocket.newInstance();

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
              socket.connect(getServerAddress());
            } catch (IOException e) {
              e.printStackTrace();
            }

          };
        }.start();

        long time = System.currentTimeMillis();
        sock.accept();
        time = System.currentTimeMillis() - time;

        assertTrue(time >= connectDelayMillis && (time
            - connectDelayMillis) <= timingInaccuracyMillis,
            "Timeout not properly honored. Accept succeeded after " + time + "ms vs. expected "
                + timeoutMillis + "ms");
      }
    });
  }
}
