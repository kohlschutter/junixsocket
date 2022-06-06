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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.CommandAvailabilityRequirement;
import com.kohlschutter.testutil.ForkedVM;
import com.kohlschutter.testutil.ForkedVMRequirement;
import com.kohlschutter.util.ExceptionUtil;

/**
 * This tests the issue reported in
 * <a href="https://github.com/kohlschutter/junixsocket/pull/29">issue 29</a>.
 * 
 * We need to ensure that the native file descriptor is closed whenever our socket implementation is
 * garbage collected, even when {@link AFSocket#close()} is not called.
 * 
 * @author Christian Kohlschütter
 */
@CommandAvailabilityRequirement(commands = {"lsof"})
@ForkedVMRequirement(forkSupported = true)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class FinalizeTest<A extends SocketAddress> extends SocketTestBase<A> {
  private Process process = null;

  protected FinalizeTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  public void testLeak() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      Semaphore sema = new Semaphore(0);
      CompletableFuture<Integer> future = new CompletableFuture<>();

      try (ServerThread serverThread = new ServerThread() {
        @Override
        protected void onServerReady() {
          sema.release();
        }

        @Override
        protected void handleConnection(final Socket socket) throws IOException {
          try {
            assumeTrue(process.pid() > 0);
            int linesBefore = -1;
            try (OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream()) {
              linesBefore = lsofUnixSockets(process.pid());
              out.write('@');

              // If that's not true, we need to skip the test
              assumeTrue(linesBefore > 0);
            } finally {
              future.complete(linesBefore);
            }
          } catch (Exception e) {
            future.completeExceptionally(e);
          } finally {
            stopAcceptingConnections();
          }
        }
      }) {
        sema.acquire();
        this.process = launchServerProcess(socketType(), ((AFSocketAddress) serverThread
            .getServerAddress()).getHostString());
        Integer linesBefore = future.get();
        assertNotNull(linesBefore);

        try {
          int linesAfter = 0;
          for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            linesAfter = lsofUnixSockets(process.pid());
            if (linesAfter != linesBefore) {
              break;
            }
            if (!process.isAlive()) {
              break;
            }
          }

          assumeTrue(linesAfter > 0, "lsof may fail to return anything");

          assertTrue(linesAfter < linesBefore,
              "Our unix socket file handle should have been cleared out");
        } finally {
          process.destroy();
          process.waitFor();
        }
      } catch (ExecutionException e) {
        throw ExceptionUtil.unwrapExecutionException(e);
      } finally {
        Process p = this.process;
        if (p != null) {
          p.destroy();
        }
        this.process = null;
      }
    });
  }

  private Process launchServerProcess(String socketType, String socketPath) throws IOException {
    ForkedVM vm = new ForkedVM() {
      @Override
      protected void onJavaMainClass(String arg) {
        super.onJavaOption("-Dtest.junixsocket.socket.type=" + socketType);
        super.onJavaOption("-Dtest.junixsocket.socket=" + socketPath);
        super.onJavaMainClass(FinalizeTestClient.class.getName());
      }

      @Override
      protected void onArguments(List<String> args) {
        super.onArguments(Collections.emptyList());
      }
    };
    vm.setRedirectError(Redirect.INHERIT);
    vm.setRedirectOutput(Redirect.INHERIT);

    return vm.fork();
  }

  @SuppressFBWarnings({"RV_DONT_JUST_NULL_CHECK_READLINE"})
  private static int lsofUnixSockets(long pid) throws IOException, TestAbortedException,
      InterruptedException {
    assertTrue(pid > 0);

    Process p;
    try {
      p = Runtime.getRuntime().exec(new String[] {"lsof", "-U", "-a", "-p", String.valueOf(pid)});
    } catch (Exception e) {
      assumeTrue(false, e.getMessage());
      return -1;
    }
    int lines = 0;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset
        .defaultCharset()))) {
      String l;
      while ((l = in.readLine()) != null) {
        lines++;
        if (l.contains("busybox")) {
          assumeTrue(false, "incompatible lsof binary");
        }
      }
    }
    assumeTrue(p.waitFor() == 0, "lsof should terminate with RC=0");
    return lines;
  }

  protected abstract String socketType();
}
