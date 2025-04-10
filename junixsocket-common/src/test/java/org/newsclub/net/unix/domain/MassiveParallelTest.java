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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.ConnectionResetSocketException;
import org.newsclub.net.unix.SocketClosedException;
import org.newsclub.net.unix.TestUtil;
import org.newsclub.net.unix.ThreadUtil;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedNotAnIssueException;
import com.kohlschutter.util.SystemPropertyUtil;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressWarnings({
    "PMD.NcssCount", "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public class MassiveParallelTest extends
    org.newsclub.net.unix.MassiveParallelTest<AFUNIXSocketAddress> {
  private static final int MAX_SERVER_THREADS = 32;

  protected MassiveParallelTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.AvoidCatchingThrowable"})
  public void testAcceptConnect() throws Exception {
    if (!ThreadUtil.isVirtualThreadSupported()) {
      throw new TestAbortedNotAnIssueException("Virtual Threads are not supported by this JVM");
    }
    // number of connections to perform
    // final int numConnections = 1_000;
    // final int numConnections = 10_000;
    // final int numConnections = 100_000;
    // final int numConnections = 1_000_000;
    final int numConnections = SystemPropertyUtil.getIntSystemProperty(
        "selftest.MassiveParallelTest.numConnections", 1000);
    if (numConnections <= 0) {
      throw new TestAbortedNotAnIssueException("Skipping test due to numConnections="
          + numConnections);
    }

    // limit the number of concurrently active servers/clients
    // so we don't run out of file descriptors (the limit could be as low as 256)
    final int nProc = Math.min(MAX_SERVER_THREADS, Runtime.getRuntime().availableProcessors());
    final Semaphore concurrentClientPermits = new Semaphore(Math.min(100, nProc));

    AFUNIXSocketAddress listenAddr = AFUNIXSocketAddress.ofNewTempFile();

    long startTime;

    try (Server server = new Server(listenAddr, nProc, numConnections)) {
      ExecutorService esClients = ThreadUtil.newVirtualThreadPerTaskExecutor();
      // ExecutorService esClients = Executors.newWorkStealingPool();
      final AtomicInteger connectAttempts = new AtomicInteger(0);
      final AtomicInteger connected = new AtomicInteger(0);

      Runnable clientJob = new Runnable() {

        @Override
        public void run() {
          try (AFUNIXSocket socket = AFUNIXSocket.newInstance()) {
            concurrentClientPermits.acquire();
            try {
              connectAttempts.incrementAndGet();
              socket.connect(listenAddr);
            } catch (SocketTimeoutException | SocketException e) {
              // try again
              return;
            }

            connected.incrementAndGet();
            try (InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream()) {
              int rcv = in.read();
              if (rcv != 0xAA) {
                if (server.isRunning()) {
                  System.err.println("Wrong data: " + rcv);
                }
              }

              byte[] otherBytes = new byte[5];
              int pos = 0;
              int remaining = otherBytes.length;
              while (remaining > 0) {
                int read = in.read(otherBytes, pos, remaining);
                if (read == -1) {
                  break;
                }
                remaining -= read;
                pos += read;
              }
              if (remaining != 0) {
                if (server.isRunning()) {
                  System.err.println("Incomplete data; bytes missing " + remaining);
                }
              }
              if (server.isRunning()) {
                if (!Arrays.equals(new byte[] {(byte) 0xAB, (byte) 0xAC, 'X', 'Y', 'Z'},
                    otherBytes)) {
                  System.err.println("Wrong data received: " + Arrays.toString(otherBytes) + " vs "
                      + Arrays.toString(new byte[] {(byte) 0xAB, (byte) 0xAC, 'X', 'Y', 'Z'}));
                }
              }

              out.write(0xBB);
              out.flush();
            }

          } catch (Throwable e) {
            if (server.isRunning()) {
              e.printStackTrace();
            }
          } finally {
            concurrentClientPermits.release();
            if (server.isRunning()) {
              // keep trying
              TestUtil.trackFuture(esClients.submit(this));
            }
          }
        }
      };

      startTime = System.currentTimeMillis();

      for (int i = 0; i < numConnections; i++) {
        TestUtil.trackFuture(esClients.submit(clientJob));
      }

      boolean stopped = server.cl.await(10, TimeUnit.SECONDS);
      if (!stopped) {
        List<Runnable> remainingClients = esClients.shutdownNow();
        List<Runnable> remainingServers = server.esServers.shutdownNow();
        System.err.println("Not all connections were made; remaining: " + server.cl.getCount());
        if (!remainingClients.isEmpty() || !remainingServers.isEmpty()) {
          System.err.println("Remaining threads: servers=" + remainingServers.size() + "; clients="
              + remainingClients.size());
        }
      }
      server.stop();

      long elapsed = (System.currentTimeMillis() - startTime);
      int completed = server.completed.intValue();
      System.out.println("millis: " + elapsed);
      System.out.println("completed: " + completed);
      float timePerItem = elapsed / (float) completed;
      System.out.println("time per completed connection: " + timePerItem + " ms");

      if (completed <= nProc && completed < numConnections / 10.0) {
        fail("Not enough jobs were completed: " + completed + "; expected:" + numConnections);
      }
    }
  }

  private static final class Server implements Closeable {
    final AtomicBoolean running = new AtomicBoolean(true);
    final AtomicInteger accepted = new AtomicInteger(0);
    final AtomicInteger serverThreads = new AtomicInteger(0);
    final AtomicInteger completed = new AtomicInteger(0);

    final AtomicInteger acceptsInFlight = new AtomicInteger(0);
    final AFUNIXServerSocket serverSocket;
    final ExecutorService esServers;

    final CountDownLatch cl;

    public Server(AFUNIXSocketAddress listenAddr, int numServerThreads, int stopAfterNumConnections)
        throws IOException {
      cl = new CountDownLatch(stopAfterNumConnections);
      serverSocket = AFUNIXServerSocket.newInstance();
      serverSocket.bind(listenAddr); // allow for some default backlog

      // esServers = Executors.newCachedThreadPool();
      // esServers = Executors.newWorkStealingPool();
      esServers = ThreadUtil.newVirtualThreadPerTaskExecutor();

      for (int i = 0; i < numServerThreads; i++) {
        TestUtil.trackFuture(esServers.submit(this::acceptJob));
      }
    }

    public boolean isRunning() {
      return running.get() && !serverSocket.isClosed();
    }

    @Override
    public String toString() {
      return super.toString() + "[closed=" + serverSocket.isClosed() + ";running=" + running.get()
          + ";inAccept=" + acceptsInFlight + ";completed=" + completed + "]";
    }

    private void acceptJob() {
      serverThreads.incrementAndGet();
      acceptsInFlight.incrementAndGet();
      try {
        ByteBuffer bbNonDirect = ByteBuffer.allocate(64);
        ByteBuffer bbDirect = ByteBuffer.allocateDirect(64);

        while (isRunning()) {
          try (AFUNIXSocket socket = serverSocket.accept()) {
            accepted.incrementAndGet();
            if (socket == null) {
              continue;
            }
            cl.countDown();
            if (cl.getCount() == 0) {
              stop();
            }

            try (AFUNIXSocketChannel channel = socket.getChannel();
                OutputStream out = socket.getOutputStream();) {
              channel.configureBlocking(true);
              ByteBuffer bb = bbDirect;
              bb.clear();
              bb.put((byte) 0xAA);
              bb.put((byte) 0xAB);
              bb.put((byte) 0xAC);
              bb.flip();

              while (bb.hasRemaining()) {
                channel.write(bb);
              }

              out.write(new byte[] {'X', 'Y', 'Z'});
              out.flush();

              bb = bbNonDirect;

              bb.clear();
              int read = channel.read(bb);
              if (read != 1) {
                if (isRunning()) {
                  System.err.println("Wrong response: " + read + " bytes");
                }
              } else {
                int rcv = bb.get(0) & 0xFF;
                if (rcv != 0xbb) {
                  if (isRunning()) {
                    System.err.println("Wrong response: 0x" + Integer.toHexString(rcv));
                  }
                } else {
                  completed.incrementAndGet();
                }
              }
            }
          } catch (InterruptedIOException | ConnectionResetSocketException | SocketClosedException
              | ClosedChannelException e) {
            // ignore
          } catch (SocketException e) {
            if (isRunning()) {
              throw e;
            } else {
              // ignore
            }
          }
        }
      } catch (Throwable e) { // NOPMD.ExceptionAsFlowControl,AvoidCatchingThrowable
        if (isRunning()) {
          e.printStackTrace();
        }
      } finally {
        acceptsInFlight.decrementAndGet();
        if (isRunning()) {
          System.err.println("Restarting failed server job");
          try {
            TestUtil.trackFuture(esServers.submit(this::acceptJob));
          } catch (RejectedExecutionException e) {
            if (isRunning()) {
              TestUtil.printStackTrace(e);
            }
          } catch (Throwable t) { // NOPMD.AvoidCatchingThrowable
            TestUtil.printStackTrace(t);
          }
        }
      }
    }

    public void stop() {
      running.set(false);
      esServers.shutdown();
    }

    @Override
    public void close() throws IOException {
      stop();
      serverSocket.close();
      try {
        if (!esServers.awaitTermination(5, TimeUnit.SECONDS)) {
          esServers.shutdownNow();
          if (!esServers.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new InterruptedIOException("did not terminate");
          }
        }
      } catch (InterruptedException e) {
        throw (InterruptedIOException) new InterruptedIOException("did not terminate").initCause(e);
      }
    }
  }

}
