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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.ForkedVM;
import com.kohlschutter.util.ExceptionUtil;

/**
 * This tests the issue reported in
 * <a href="https://github.com/kohlschutter/junixsocket/pull/29">issue 29</a>.
 * 
 * We need to ensure that the native file descriptor is closed whenever our socket implementation is
 * garbage collected, even when {@link AFUNIXSocket#close()} is not called.
 * 
 * @author Christian Kohlschütter
 */
public class FinalizeTest extends SocketTestBase {
  private Process process = null;

  public FinalizeTest() throws IOException {
    super();
  }

  @Test
  public void testLeak() throws Exception {
    Semaphore sema = new Semaphore(0);
    CompletableFuture<Integer> future = new CompletableFuture<>();

    try (ServerThread serverThread = new ServerThread() {
      @Override
      protected void onServerReady() {
        sema.release();
      }

      @Override
      protected void handleConnection(final AFUNIXSocket socket) throws IOException {
        try {
          assumeTrue(process.pid() > 0);
          int linesBefore;
          try (OutputStream out = socket.getOutputStream();
              InputStream in = socket.getInputStream()) {
            linesBefore = lsofUnixSockets(process.pid());
            out.write('@');

            // If that's not true, we need to skip the test
            assumeTrue(linesBefore > 0);
          }

          future.complete(linesBefore);
        } catch (Exception e) {
          future.completeExceptionally(e);
        } finally {
          stopAcceptingConnections();
        }
      }
    }) {
      sema.acquire();
      this.process = launchServerProcess(getSocketFile().getAbsolutePath());
      Integer linesBefore = future.get();
      assertNotNull(linesBefore);

      int linesAfter = 0;
      for (int i = 0; i < 10; i++) {
        Thread.sleep(100);
        linesAfter = lsofUnixSockets(process.pid());
        if (linesAfter != linesBefore) {
          break;
        }
      }

      assertEquals(linesBefore - 1, linesAfter,
          "Our unix socket file handle should have been cleared out");
      process.destroy();
      process.waitFor();
    } catch (ExecutionException e) {
      throw ExceptionUtil.unwrapExecutionException(e);
    } finally {
      this.process.destroy();
      this.process = null;
    }
  }

  private Process launchServerProcess(String socketPath) throws IOException {
    return new ForkedVM() {
      @Override
      protected void onJavaMainClass(String arg) {
        super.onJavaOption("-Dtest.junixsocket.socket=" + socketPath);
        super.onJavaMainClass(FinalizeTestClient.class.getName());
      }

      @Override
      protected void onArguments(List<String> args) {
        super.onArguments(Collections.emptyList());
      }
    }.fork();
  }

  @SuppressFBWarnings({"RV_DONT_JUST_NULL_CHECK_READLINE"})
  private static int lsofUnixSockets(long pid) throws IOException, TestAbortedException,
      InterruptedException {
    assertTrue(pid > 0);

    Process p;
    try {
      p = Runtime.getRuntime().exec(new String[] {"lsof", "-U", "-a", "-p", String.valueOf(pid)});
    } catch (IOException e) {
      assumeTrue(false, e.getMessage());
      return -1;
    }
    int lines = 0;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset
        .defaultCharset()))) {
      while ((in.readLine()) != null) {
        lines++;
      }
    }
    assumeTrue(p.waitFor() == 0, "lsof should terminate with RC=0");
    return lines;
  }
}
