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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.platform.commons.JUnitException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.util.SystemPropertyUtil;

/**
 * This test measures throughput for sending and receiving messages over AF_UNIX, comparing
 * implementations of junixsocket and JEP 380 (Java 16).
 *
 * The test is enabled by default (only runs for a very short time), and is also included within the
 * self-test.
 *
 * The tests can be configured as follows (all system properties):
 * <ul>
 * <li><code>org.newsclub.net.unix.throughput-test.enabled</code> (0/1, default: 1)</li>
 * <li><code>org.newsclub.net.unix.throughput-test.payload-size</code> (bytes, e.g., 8192)</li>
 * <li><code>org.newsclub.net.unix.throughput-test.payload-size.datagram</code> (bytes, e.g., 2048;
 * defaults to value specified with "payload-size" above)</li>
 * <li><code>org.newsclub.net.unix.throughput-test.seconds</code> (default: 0)</li>
 * </ul>
 *
 * @author Christian Kohlschütter
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class ThroughputTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected static final int ENABLED = SystemPropertyUtil.getIntSystemProperty(
      "org.newsclub.net.unix.throughput-test.enabled", 1);
  protected static final int PAYLOAD_SIZE = SystemPropertyUtil.getIntSystemProperty(
      "org.newsclub.net.unix.throughput-test.payload-size", 2048); // 8192 is much faster
  protected static final int PAYLOAD_SIZE_DATAGRAM = SystemPropertyUtil.getIntSystemProperty(
      "org.newsclub.net.unix.throughput-test.payload-size.datagram", PAYLOAD_SIZE);
  protected static final int NUM_SECONDS = SystemPropertyUtil.getIntSystemProperty(
      "org.newsclub.net.unix.throughput-test.seconds", 0);
  protected static final int NUM_MILLISECONDS = Math.max(50, NUM_SECONDS * 1000);

  protected ThroughputTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  private static byte[] createTestData(int size) {
    byte[] buf = new byte[size];
    for (int i = 0; i < buf.length; i++) {
      buf[i] = (byte) (i % 256);
    }
    return buf;
  }

  private static void reportResults(String testType, String s) {
    if (NUM_SECONDS == 0) {
      // Tests are too short to be meaningful (other than for code coverage) -- do not report
      return;
    }
    System.out.println("ThroughputTest (" + testType + "): " + s);
  }

  @Test
  public void testSocket() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");

    assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
      try (ServerThread serverThread = new ServerThread() {
        @Override
        protected void handleConnection(final Socket sock) throws IOException {
          byte[] buf = new byte[PAYLOAD_SIZE];
          int read;

          try (InputStream inputStream = sock.getInputStream();
              OutputStream outputStream = sock.getOutputStream()) {
            while ((read = inputStream.read(buf)) >= 0) {
              outputStream.write(buf, 0, read);
            }
          }
        }
      }) {

        AtomicBoolean keepRunning = new AtomicBoolean(true);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
          keepRunning.set(false);
        }, NUM_MILLISECONDS, TimeUnit.MILLISECONDS);

        try (Socket sock = connectTo(serverThread.getServerAddress())) {
          byte[] buf = createTestData(PAYLOAD_SIZE);

          try (InputStream inputStream = sock.getInputStream();
              OutputStream outputStream = sock.getOutputStream()) {

            long readTotal = 0;
            long time = System.currentTimeMillis();
            while (keepRunning.get()) {
              outputStream.write(buf);

              int remaining = buf.length;
              int offset = 0;

              int read; // limited by net.local.stream.recvspace / sendspace etc.

              try {
                while (remaining > 0 && (read = inputStream.read(buf, offset, remaining)) >= 0) {
                  if (read > 0) {
                    remaining -= read;
                    offset += read;
                    readTotal += read;
                  }
                }
              } catch (SocketTimeoutException e) {
                if (keepRunning.get()) {
                  throw e;
                }
              }
              assertEquals(0, remaining);
            }
            time = System.currentTimeMillis() - time;

            reportResults(stbTestType() + " byte[]", ((1000f * readTotal / time) / 1000f / 1000f)
                + " MB/s for payload size " + PAYLOAD_SIZE);
          }
        }
      }
    });
  }

  @Test
  public void testSocketChannel() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
      runtestSocketChannel(false);
    });
  }

  @Test
  public void testSocketChannelDirectBuffer() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
      runtestSocketChannel(true);
    });
  }

  @FunctionalInterface
  protected interface SupplierWithException<T, E extends Exception> {
    T get() throws E;
  }

  private void runtestSocketChannel(boolean direct) throws Exception {
    SelectorProvider sp = selectorProvider();
    ServerSocketChannel ssc = sp.openServerSocketChannel();

    runTestSocketChannel(stbTestType() + " SocketChannel", getServerBindAddress(), ssc, () -> {
      SocketChannel sc = sp.openSocketChannel();
      connectSocket(sc, ssc.getLocalAddress());
      return sc;
    }, direct);
  }

  @Override
  protected boolean shouldDoConnectionCheckUponAccept() {
    return true;
  }

  protected void runTestSocketChannel(String implId, SocketAddress sba, ServerSocketChannel ssc,
      SupplierWithException<SocketChannel, IOException> sscSupp, boolean direct) throws Exception {
    final AtomicBoolean keepRunning = new AtomicBoolean(true);

    try (ServerThread unused = new ServerThread() {

      @Override
      protected ServerSocket startServer() throws IOException {
        bindServerSocket(ssc, sba);
        return null;
      }

      @Override
      public void shutdown() throws IOException {
        super.shutdown();
        ssc.close();
      }

      @Override
      protected void onServerSocketClose() {
        keepRunning.set(false);
        super.onServerSocketClose();
      }

      @Override
      protected void acceptAndHandleConnection() throws IOException {
        ByteBuffer bb = direct ? ByteBuffer.allocateDirect(PAYLOAD_SIZE) : ByteBuffer.allocate(
            PAYLOAD_SIZE);
        try (SocketChannel sc = ssc.accept()) {
          try {
            bb.clear();
            while (sc.read(bb) >= 0) {
              bb.flip();
              sc.write(bb);
              bb.clear();
            }
          } catch (SocketException | SocketTimeoutException e) {
            if (keepRunning.get()) {
              throw e;
            } else {
              // broken pipe (or connection reset by peer) is expected here
            }
          }
        }
      }

      @Override
      protected void handleConnection(Socket sock) throws IOException {
        throw new IllegalStateException();
      }
    }) {

      Executors.newSingleThreadScheduledExecutor().schedule(() -> {
        keepRunning.set(false);
      }, NUM_MILLISECONDS, TimeUnit.MILLISECONDS);

      try (SocketChannel sc = sscSupp.get()) {
        ByteBuffer bb = direct ? ByteBuffer.allocateDirect(PAYLOAD_SIZE) : ByteBuffer.allocate(
            PAYLOAD_SIZE);

        long readTotal = 0;
        long time = System.currentTimeMillis();
        while (keepRunning.get()) {
          bb.clear();
          bb.put(createTestData(PAYLOAD_SIZE));
          bb.flip();
          long remaining = sc.write(bb);
          bb.clear();

          long read; // limited by net.local.stream.recvspace / sendspace etc.
          while (remaining > 0 && (read = sc.read(bb)) >= 0) {
            remaining -= read;
            readTotal += read;
            bb.clear();
          }
          assertEquals(0, remaining);
        }

        time = System.currentTimeMillis() - time;
        reportResults(implId + " direct=" + direct, ((1000f * readTotal / time) / 1000f / 1000f)
            + " MB/s for payload size " + PAYLOAD_SIZE);
      }
    }
  }

  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
  @Test
  @SuppressWarnings("PMD.CognitiveComplexity")
  public void testDatagramPacket() throws Exception {
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
        SocketAddress dsAddr = newTempAddressForDatagram();
        SocketAddress dcAddr = newTempAddressForDatagram();

        try (DatagramSocket ds = newDatagramSocket(); DatagramSocket dc = newDatagramSocket()) {
          if (!ds.isBound()) {
            ds.bind(dsAddr);
          }
          if (!dc.isBound()) {
            dc.bind(dcAddr);
          }

          dsAddr = ds.getLocalSocketAddress();
          dcAddr = dc.getLocalSocketAddress();

          assertNotEquals(dsAddr, dcAddr);

          dc.connect(dsAddr);

          AtomicBoolean keepRunning = new AtomicBoolean(true);
          Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            keepRunning.set(false);
          }, NUM_MILLISECONDS, TimeUnit.MILLISECONDS);

          AtomicLong readTotal = new AtomicLong();
          long sentTotal = 0;

          new Thread() {
            final DatagramPacket dp = new DatagramPacket(new byte[PAYLOAD_SIZE_DATAGRAM],
                PAYLOAD_SIZE_DATAGRAM);

            @Override
            public void run() {
              try {
                while (!Thread.interrupted() && !ds.isClosed()) {
                  try {
                    ds.receive(dp);
                  } catch (SocketTimeoutException e) {
                    continue;
                  }
                  int read = dp.getLength();
                  if (read != PAYLOAD_SIZE_DATAGRAM && read != 0) {
                    throw new IOException("Unexpected response length: " + read);
                  }
                  readTotal.addAndGet(dp.getLength());
                }
              } catch (SocketException e) {
                if (keepRunning.get()) {
                  e.printStackTrace();
                }
              } catch (IOException e) { // NOPMD.ExceptionAsFlowControl
                e.printStackTrace();
              }
            }
          }.start();

          long time = System.currentTimeMillis();

          DatagramPacket dp = new DatagramPacket(new byte[PAYLOAD_SIZE_DATAGRAM],
              PAYLOAD_SIZE_DATAGRAM);
          byte[] data = dp.getData();
          for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
          }

          while (keepRunning.get()) {
            try {
              dc.send(dp);
            } catch (PortUnreachableException e) {
              e.addSuppressed(new Exception(dp.getSocketAddress().toString()));
              throw e;
            }
            sentTotal += PAYLOAD_SIZE_DATAGRAM;
          }
          time = System.currentTimeMillis() - time;
          keepRunning.set(false);
          ds.close(); // terminate server

          long readTotal0 = readTotal.get();

          reportResults(stbTestType() + " DatagramPacket", ((1000f * readTotal0 / time) / 1000f
              / 1000f) + " MB/s for datagram payload size " + PAYLOAD_SIZE_DATAGRAM + "; " + String
                  .format(Locale.ENGLISH, "%.1f%% packet loss", 100 * (1 - (readTotal0
                      / (float) sentTotal))));
        }
      });
    } catch (JUnitException e) {
      // Ignore timeout failure (this is a throughput test only)
      e.printStackTrace();
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
  public void testDatagramChannel() throws Exception {
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
        testDatagramChannel(false, true);
      });
    } catch (JUnitException e) {
      // Ignore timeout failure (this is a throughput test only)
      e.printStackTrace();
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
  public void testDatagramChannelDirect() throws Exception {
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
        testDatagramChannel(true, true);
      });
    } catch (JUnitException e) {
      // Ignore timeout failure (this is a throughput test only)
      e.printStackTrace();
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
  public void testDatagramChannelNonBlocking() throws Exception {
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
        testDatagramChannel(false, false);
      });
    } catch (JUnitException e) {
      // Ignore timeout failure (this is a throughput test only)
      e.printStackTrace();
    }
  }

  @Test
  @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS)
  public void testDatagramChannelNonBlockingDirect() throws Exception {
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(NUM_SECONDS + 5), () -> {
        testDatagramChannel(true, false);
      });
    } catch (JUnitException e) {
      // Ignore timeout failure (this is a throughput test only)
      e.printStackTrace();
    }
  }

  private void testDatagramChannel(boolean direct, boolean blocking) throws Exception {
    SocketAddress dsAddr = newTempAddressForDatagram();
    SocketAddress dcAddr = newTempAddressForDatagram();

    try (DatagramChannel ds = newDatagramChannel(); //
        DatagramChannel dc = newDatagramChannel();) {
      if (!ds.socket().isBound()) {
        ds.bind(dsAddr);
        dsAddr = ds.getLocalAddress();
      }
      if (!dc.socket().isBound()) {
        dc.bind(dcAddr);
        dcAddr = dc.getLocalAddress();
      }
      dc.connect(dsAddr);
      ds.connect(dcAddr);

      SelectorProvider sp;
      if (blocking) {
        sp = null;
      } else {
        sp = selectorProvider();
        dc.configureBlocking(false);
        ds.configureBlocking(false);
      }

      testSocketDatagramChannel(stbTestType() + " DatagramChannel", ds, dc, sp, direct, blocking);
    }
  }

  protected void testSocketDatagramChannel(String id, DatagramChannel ds, DatagramChannel dc, // NOPMD
      SelectorProvider sp, boolean direct, boolean blocking) throws IOException {
    // FIXME investigate why we need to add a few more bytes (82) than the payload
    // the receiver blocks otherwise (not exactly the struct socket_addr_un).
    // smells like some TCP/IP overhead ... (?)
    // ds.setOption(StandardSocketOptions.SO_RCVBUF, (PAYLOAD_SIZE + 82));

    AtomicBoolean keepRunning = new AtomicBoolean(true);
    Executors.newSingleThreadScheduledExecutor().schedule(() -> {
      keepRunning.set(false);
      try {
        ds.close();
      } catch (IOException e) {
        // ignore
      }
    }, NUM_MILLISECONDS, TimeUnit.MILLISECONDS);

    AtomicLong readTotal = new AtomicLong();
    long sentTotal = 0;

    long time;

    CompletableFuture<Long> bytesRead = new CompletableFuture<>();
    try (AbstractSelector readSelector = sp == null ? null : sp.openSelector()) {
      new Thread() {
        @Override
        public void run() {
          final ByteBuffer receiveBuffer = direct ? ByteBuffer.allocateDirect(PAYLOAD_SIZE_DATAGRAM)
              : ByteBuffer.allocate(PAYLOAD_SIZE_DATAGRAM);
          try {
            SelectionKey key;
            if (readSelector != null) {
              key = ds.register(readSelector, SelectionKey.OP_READ);
            } else {
              key = null;
            }
            while (!Thread.interrupted() && keepRunning.get() && !bytesRead.isCancelled()) {
              int read;
              if (readSelector != null) {
                int numReady = readSelector.select();
                if (numReady == 0) {
                  continue;
                }
                assertEquals(1, numReady);
                Objects.requireNonNull(key);

                // If we'd check for invalid keys, we could prevent the ClosedChannelException
                // if (!key.isValid()) {
                // break;
                // }
              }
              read = ds.read(receiveBuffer);
              receiveBuffer.rewind();
              if (read != PAYLOAD_SIZE_DATAGRAM && read != 0 && read != -1) {
                throw new IOException("Unexpected response length: " + read);
              }
              readTotal.addAndGet(read);
            }
            bytesRead.complete(readTotal.get());
          } catch (ClosedChannelException | SocketException e) {
            if (keepRunning.get()) {
              keepRunning.set(false);
              bytesRead.completeExceptionally(e);
            } else {
              bytesRead.complete(readTotal.get());
            }
          } catch (Exception e) { // NOPMD.ExceptionAsFlowControl
            keepRunning.set(false);
            bytesRead.completeExceptionally(e);
          }
        }
      }.start();

      time = System.currentTimeMillis();

      final ByteBuffer sendBuffer = direct ? ByteBuffer.allocateDirect(PAYLOAD_SIZE_DATAGRAM)
          : ByteBuffer.allocate(PAYLOAD_SIZE_DATAGRAM);

      try (AbstractSelector writeSelector = sp == null ? null : sp.openSelector()) { // NOPMD
        if (sp != null) {
          dc.register(writeSelector, SelectionKey.OP_WRITE);
        }
        while (keepRunning.get()) {
          if (writeSelector != null) {
            int numReady = writeSelector.select();
            if (numReady == 0) {
              continue;
            }
            assertEquals(1, numReady);
          }
          int written;
          try {
            written = dc.write(sendBuffer);
          } catch (SocketException e) {
            if (keepRunning.get()) {
              throw e;
            } else {
              written = 0;
            }
          }

          if (written != PAYLOAD_SIZE_DATAGRAM && written != 0) {
            throw new IOException("Unexpected written length: " + written);
          }

          sentTotal += PAYLOAD_SIZE_DATAGRAM;
          sendBuffer.rewind();
        }
      } finally {
        time = System.currentTimeMillis() - time;
        keepRunning.set(false);
        ds.close(); // terminate server
      }
    }

    try {
      bytesRead.get(NUM_MILLISECONDS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      if (NUM_SECONDS != 0) {
        e.printStackTrace();
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }

    long readTotal0 = readTotal.get();

    reportResults(id + " direct=" + direct + ";blocking=" + blocking, ((1000f * readTotal0 / time)
        / 1000f / 1000f) + " MB/s for datagram payload size " + PAYLOAD_SIZE_DATAGRAM + "; "
        + String.format(Locale.ENGLISH, "%.1f%% packet loss", 100 * (1 - (readTotal0
            / (float) sentTotal))));

  }

  protected String stbTestType() {
    return "junixsocket";
  }
}
