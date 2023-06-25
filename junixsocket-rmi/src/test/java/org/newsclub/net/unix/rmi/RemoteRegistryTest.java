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
package org.newsclub.net.unix.rmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.rmi.ServerException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.ForkedVM;
import com.kohlschutter.testutil.ForkedVMRequirement;
import com.kohlschutter.testutil.OutputBridge;
import com.kohlschutter.testutil.OutputBridge.ProcessStream;

@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public class RemoteRegistryTest {
  @Test
  @ForkedVMRequirement(forkSupported = true)
  public void testRemoteRegistry() throws Exception {
    File socketDir = tempSocketDir();

    try (SpawnedRegistryAccess sra = new SpawnedRegistryAccess(TestRegistryServer.class
        .getSimpleName(), socketDir) {

      @Override
      protected int exportHelloDelayMillis() {
        return 500;
      }

    }) {
      tryToSayHello(sra);
      sra.shutdown();
    } catch (Exception e) {
      throw e;
    }

    assertEquals(0, countRMIFiles(socketDir));
    assertTrue(socketDir.delete());
  }

  @Test
  @ForkedVMRequirement(forkSupported = true)
  public void testRemoteRegistryStandardPath() throws Exception {
    File socketDir = AFUNIXNaming.getDefaultSocketDirectory();
    assumeTrue(countRMIFiles(socketDir) == 0,
        "Test cannot be performed: *.rmi files exist in directory: " + socketDir);

    for (int i = 1, n = 2; i <= n; i++) {
      System.out.println("Attempt " + i + "/" + n);
      try (SpawnedRegistryAccess sra = new SpawnedRegistryAccess(TestRegistryServer.class
          .getSimpleName()) {

        @Override
        protected AFNaming getNamingInstance() throws IOException {
          return AFUNIXNaming.getInstance();
        }
      }) {
        tryToSayHello(sra);
        sra.shutdown();
      } catch (Exception e) {
        throw e;
      }
      assertEquals(0, countRMIFiles(socketDir));
    }
  }

  @Test
  @ForkedVMRequirement(forkSupported = true)
  public void testRemoteShutdownNotAllowed() throws Exception {
    File socketDir = tempSocketDir();

    try (SpawnedRegistryAccess sra = new SpawnedRegistryAccess(TestRegistryServer.class
        .getSimpleName(), socketDir) {

      @Override
      protected boolean remoteShutdownAllowed() {
        return false;
      }

      @Override
      protected int shutdownAfterSecs() {
        return 30;
      }
    }) {
      AFRegistry registry = sra.getRegistry();
      assertNotNull(registry, "Could not access the AFUNIXRegistry created by the forked VM");

      assertThrows(ServerException.class, () -> sra.getRegistry().getNaming().shutdownRegistry());

      sra.shutdownAndWait(true);
    } catch (Exception e) {
      throw e;
    }
    assertEquals(0, countRMIFiles(socketDir));
  }

  private void tryToSayHello(SpawnedRegistryAccess sra) throws Exception {
    AFRegistry registry = sra.getRegistry();
    assertNotNull(registry, "Could not access the AFUNIXRegistry created by the forked VM");

    AFNaming naming = registry.getNaming();
    try {
      Hello hello = (Hello) naming.lookup("hello", 20, TimeUnit.SECONDS);
      assertEquals("Hello", hello.hello());
    } finally {
      naming.shutdownRegistry();

      int rc = sra.shutdownAndWait();
      assertEquals(0, rc, TestRegistryServer.class.getName()
          + " process terminated with return code " + rc);
    }
  }

  private static class SpawnedRegistryAccess implements AutoCloseable {
    private final File socketDir;
    private final ExecutorService executors;
    private final Process registryProcess;
    private final CompletableFuture<AFRegistry> registryFuture;
    private final AtomicBoolean markedShutdown = new AtomicBoolean(false);
    private final String prefix;
    private final OutputBridge bridgeOut;
    private final OutputBridge bridgeErr;

    SpawnedRegistryAccess(String id) throws IOException {
      this(id, AFUNIXNaming.getDefaultSocketDirectory());
    }

    SpawnedRegistryAccess(String id, File socketDir) throws IOException {
      this.socketDir = socketDir;
      this.executors = Executors.newCachedThreadPool();
      this.registryFuture = new CompletableFuture<>();
      this.registryProcess = launchRegistryProcess();

      long pid;
      try {
        pid = registryProcess.pid();
      } catch (UnsupportedOperationException e) {
        pid = -1;
      }
      this.prefix = "(" + id + (pid != -1 ? (" " + registryProcess.pid()) : "") + ") ";

      this.bridgeOut = new OutputBridge(registryProcess, ProcessStream.STDOUT, prefix);
      this.bridgeErr = new OutputBridge(registryProcess, ProcessStream.STDERR, prefix);

      watchProcessAsync();
      asyncGetRegistry();
    }

    void shutdown() {
      markedShutdown.set(true);
      executors.shutdownNow();
    }

    int shutdownAndWait() throws InterruptedException {
      return shutdownAndWait(false);
    }

    int shutdownAndWait(boolean destroy) throws InterruptedException {
      shutdown();
      if (destroy) {
        registryProcess.destroy();
      }
      return registryProcess.waitFor();
    }

    private boolean isShutdown() {
      return markedShutdown.get() || executors.isShutdown();
    }

    public AFRegistry getRegistry() throws InterruptedException, ExecutionException {
      return registryFuture.get();
    }

    @Override
    public void close() throws Exception {
      shutdown();

      registryProcess.destroyForcibly();
    }

    private Process launchRegistryProcess() throws IOException {
      return new ForkedVM() {
        @Override
        protected void onJavaMainClass(String arg) {
          super.onJavaOption("-Drmitest.junixsocket.socket-dir=" + socketDir.getPath());
          super.onJavaOption("-Drmitest.junixsocket.create-registry=true");
          super.onJavaOption("-Drmitest.junixsocket.export-hello=true");
          super.onJavaOption("-Drmitest.junixsocket.export-hello.delay="
              + exportHelloDelayMillis());
          super.onJavaOption("-Drmitest.junixsocket.remote-shutdown.allowed="
              + remoteShutdownAllowed());
          super.onJavaOption("-Drmitest.junixsocket.shutdown-after.secs=" + shutdownAfterSecs());
          super.onJavaMainClass(TestRegistryServer.class.getName());
        }

        @Override
        protected void onArguments(List<String> args) {
          super.onArguments(Collections.emptyList());
        }
      }.fork();
    }

    protected int shutdownAfterSecs() {
      return -1;
    }

    private void watchProcessAsync() {
      executors.submit(() -> {
        try {
          registryProcess.waitFor();
        } catch (InterruptedException e) {
          if (!markedShutdown.get()) {
            registryFuture.completeExceptionally(e);
          }
          return;
        }
        if (!markedShutdown.get() && !registryProcess.isAlive()) {
          registryFuture.completeExceptionally(new Exception(
              "The spawned VM has terminated with RC=" + registryProcess.exitValue()));
        }
      });
      executors.submit(bridgeOut);
      executors.submit(bridgeErr);
    }

    private void asyncGetRegistry() {
      executors.submit(() -> {
        try {
          AFNaming naming = getNamingInstance();
          AFRegistry registry = naming.getRegistry(30, TimeUnit.SECONDS);
          registryFuture.complete(registry);
        } catch (RuntimeException | IOException e) {
          if (!isShutdown()) {
            registryFuture.completeExceptionally(e);
          }
        }
      });
    }

    protected AFNaming getNamingInstance() throws IOException {
      return AFUNIXNaming.getInstance(socketDir);
    }

    protected int exportHelloDelayMillis() {
      return 0;
    }

    protected boolean remoteShutdownAllowed() {
      return true;
    }
  }

  private File tempSocketDir() throws IOException {
    File socketDir = File.createTempFile("jux", "");
    assumeTrue(countRMIFiles(socketDir) == 0,
        "Test cannot be performed: *.rmi files exist in directory: " + socketDir);
    assertTrue(socketDir.delete());
    assertTrue(socketDir.mkdir());
    return socketDir;
  }

  private int countRMIFiles(File dir) {
    File[] files = dir.listFiles((d, name) -> name.endsWith(".rmi"));
    return files == null ? 0 : files.length;
  }
}
