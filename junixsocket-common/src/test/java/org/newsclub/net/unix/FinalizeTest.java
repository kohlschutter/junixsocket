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

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

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
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class FinalizeTest<A extends SocketAddress> extends SocketTestBase<A> {
  private Process process = null;

  protected FinalizeTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @ForkedVMRequirement(forkSupported = true)
  @Test
  public void testLeak() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      Semaphore sema = new Semaphore(0);
      CompletableFuture<Object> future = new CompletableFuture<>();

      try (ServerThread serverThread = new ServerThread() {
        @Override
        protected void onServerReady() {
          sema.release();
        }

        @SuppressWarnings("PMD.DoNotCallGarbageCollectionExplicitly")
        @SuppressFBWarnings("DM_GC")
        @Override
        protected void handleConnection(final Socket socket) throws IOException {
          try {
            assumeTrue(process.pid() > 0);
            Object preRunCheck = null;
            try {
              try (OutputStream out = socket.getOutputStream();
                  InputStream unused = socket.getInputStream()) {
                preRunCheck = preRunCheck(process);
                out.write('@');
              }
            } finally {
              System.gc();
              future.complete(preRunCheck);
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
        Object preRunCheck = future.get();

        postRunCheck(process, preRunCheck);
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

  protected Object preRunCheck(Process p) throws IOException, InterruptedException {
    return null;
  }

  protected void postRunCheck(Process p, Object preRunCheck) throws IOException,
      InterruptedException {
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

  protected abstract String socketType();
}
