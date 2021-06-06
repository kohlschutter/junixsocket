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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.kohlschutter.testutil.AvailabilityRequirement;
import com.kohlschutter.util.SystemPropertyUtil;

/**
 * This test measures throughput for sending and receiving messages over AF_UNIX, comparing
 * implementations of junixsocket and JEP 380 (Java 16).
 *
 * The test is enabled by default, and also included with the self-test.
 *
 * The tests can be configured as follows (all system properties):
 * <ul>
 * <li><code>org.newsclub.net.unix.throughput-test.enabled</code> (0/1, default: 1)</li>
 * <li><code>org.newsclub.net.unix.throughput-test.payload-size</code> (bytes, e.g., 65536)</li>
 * <li><code>org.newsclub.net.unix.throughput-test.seconds</code> (default: 0)</li>
 * </ul>
 *
 * @author Christian Kohlschütter
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ThroughputTest extends SocketTestBase {
  private static final int ENABLED = SystemPropertyUtil.getIntSystemProperty(
      "org.newsclub.net.unix.throughput-test.enabled", 1);
  private static final int PAYLOAD_SIZE = SystemPropertyUtil.getIntSystemProperty(
      "org.newsclub.net.unix.throughput-test.payload-size", 2048);
  private static final int NUM_SECONDS = SystemPropertyUtil.getIntSystemProperty(
      "org.newsclub.net.unix.throughput-test.seconds", 0);
  private static final int NUM_MILLISECONDS = Math.max(50, NUM_SECONDS * 1000);
  private static final Random RANDOM = new SecureRandom();

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
  public void testJUnixSocket() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    try (ServerThread serverThread = new ServerThread() {
      @Override
      protected void handleConnection(final AFUNIXSocket sock) throws IOException {
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

      try (AFUNIXSocket sock = connectToServer()) {
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
            while (remaining > 0 && (read = inputStream.read(buf, offset, remaining)) >= 0) {
              int pos = RANDOM.nextInt(read) + offset;
              if ((buf[pos] & 0xFF) != (pos % 256)) {
                throw new IllegalStateException("Unexpected response from read: value@pos " + pos
                    + "=" + (buf[pos] & 0xFF) + " != " + (pos % 256));
              }
              remaining -= read;
              offset += read;
              readTotal += read;
            }
          }
          time = System.currentTimeMillis() - time;

          reportResults("junixsocket byte[]", ((1000f * readTotal / time) / 1000f / 1000f)
              + " MB/s for payload size " + PAYLOAD_SIZE);
        }
      }
    }
  }

  @Test
  public void testJUnixSocketChannel() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    runTestJUnixSocketChannel(false);
  }

  @Test
  public void testJUnixSocketChannelDirectBuffer() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    runTestJUnixSocketChannel(true);
  }

  @Test
  @AvailabilityRequirement(classes = {"java.net.UnixDomainSocketAddress"}, //
      message = "This test requires Java 16 or later")
  public void testJEP380() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    runTestJEP380(false);
  }

  @Test
  @AvailabilityRequirement(classes = {"java.net.UnixDomainSocketAddress"}, //
      message = "This test requires Java 16 or later")
  public void testJEP380directBuffer() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    runTestJEP380(true);
  }

  private static SocketAddress jep380SocketAddress(String path) throws IllegalAccessException,
      IllegalArgumentException, InvocationTargetException, SecurityException {
    try {
      // We use reflection so we can compile on older Java versions
      Class<?> klazz = Class.forName("java.net.UnixDomainSocketAddress");
      return (SocketAddress) klazz.getMethod("of", String.class).invoke(null, path);
    } catch (NoSuchMethodException | ClassNotFoundException e) {
      assumeTrue(false, "java.net.UnixDomainSocketAddress (JEP 380) not supported by JVM");
      return null;
    }
  }

  private void runTestJEP380(boolean direct) throws Exception {
    SocketAddress sa = jep380SocketAddress(getServerAddress().getPath());

    ServerSocketChannel ssc;
    // We use reflection so we can compile on older Java versions
    try {
      ssc = (ServerSocketChannel) ServerSocketChannel.class.getMethod("open", ProtocolFamily.class)
          .invoke(null, StandardProtocolFamily.valueOf("UNIX"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    runTestSocketChannel("JEP380 SocketChannel", sa, ssc, () -> SocketChannel.open(sa), direct);
  }

  @Test
  @SystemPropertyRequirement(property = "org.newsclub.net.unix.throughput-test.ip.enabled", value = "1")
  public void testTCPLoopback() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    runTestTCPLoopback(false);
  }

  @Test
  @SystemPropertyRequirement(property = "org.newsclub.net.unix.throughput-test.ip.enabled", value = "1")
  public void testTCPLoopbackDirectBuffer() throws Exception {
    assumeTrue(ENABLED > 0, "Throughput tests are disabled");
    assumeTrue(PAYLOAD_SIZE > 0, "Payload must be positive");
    runTestTCPLoopback(true);
  }

  private void runTestTCPLoopback(boolean direct) throws Exception {
    final SocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    ServerSocketChannel ssc = ServerSocketChannel.open();

    runTestSocketChannel("TCP-Loopback", sa, ssc, () -> SocketChannel.open(ssc.getLocalAddress()),
        direct);
  }

  @FunctionalInterface
  interface SupplierWithException<T, E extends Exception> {
    T get() throws E;
  }

  private void runTestJUnixSocketChannel(boolean direct) throws Exception {
    final SocketAddress sa = getServerAddress();

    AFUNIXSelectorProvider sp = AFUNIXSelectorProvider.getInstance();
    AFUNIXServerSocketChannel ssc = sp.openServerSocketChannel();

    runTestSocketChannel("junixsocket SocketChannel", sa, ssc, () -> sp.openSocketChannel(sa),
        direct);
  }

  private void runTestSocketChannel(String implId, SocketAddress sa, ServerSocketChannel ssc,
      SupplierWithException<SocketChannel, IOException> sscSupp, boolean direct) throws Exception {
    final AtomicBoolean keepRunning = new AtomicBoolean(true);

    try (ServerThread serverThread = new ServerThread() {

      @Override
      protected AFUNIXServerSocket startServer() throws IOException {
        ssc.bind(sa);

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
            while (sc.read(bb) >= 0) {
              bb.flip();
              sc.write(bb);
              bb.flip();
            }
          } catch (SocketException e) {
            if (keepRunning.get()) {
              throw e;
            } else {
              // broken pipe is expected here
            }
          }
        }
      }

      @Override
      protected void handleConnection(AFUNIXSocket sock) throws IOException {
        throw new IllegalStateException();
      }
    }) {

      Executors.newSingleThreadScheduledExecutor().schedule(() -> {
        keepRunning.set(false);
      }, NUM_MILLISECONDS, TimeUnit.MILLISECONDS);

      try (SocketChannel sc = sscSupp.get()) {
        ByteBuffer bb = direct ? ByteBuffer.allocateDirect(PAYLOAD_SIZE) : ByteBuffer.allocate(
            PAYLOAD_SIZE);
        bb.put(createTestData(PAYLOAD_SIZE));
        bb.flip();

        long readTotal = 0;
        long time = System.currentTimeMillis();
        while (keepRunning.get()) {
          int remaining = sc.write(bb);
          bb.flip();

          long read; // limited by net.local.stream.recvspace / sendspace etc.
          while (remaining > 0 && (read = sc.read(bb)) >= 0) {
            remaining -= read;
            readTotal += read;
          }

          int pos = RANDOM.nextInt(bb.limit());
          if ((bb.get(pos) & 0xFF) != (pos % 256)) {
            throw new IllegalStateException("Unexpected response from read");
          }

        }
        time = System.currentTimeMillis() - time;
        reportResults(implId + " direct=" + direct, ((1000f * readTotal / time) / 1000f / 1000f)
            + " MB/s for payload size " + PAYLOAD_SIZE);
      }
    }
  }

  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_DATAGRAMS)
  @Test
  public void testJUnixSocketDatagramPacket() throws Exception {
    AFUNIXSocketAddress dsAddr = AFUNIXSocketAddress.of(SocketTestBase.newTempFile());
    AFUNIXSocketAddress dcAddr = AFUNIXSocketAddress.of(SocketTestBase.newTempFile());
    assertNotEquals(dsAddr, dcAddr);

    try (AFUNIXDatagramSocket ds = AFUNIXDatagramSocket.newInstance();
        AFUNIXDatagramSocket dc = AFUNIXDatagramSocket.newInstance()) {
      ds.bind(dsAddr);
      dc.bind(dcAddr);
      dc.connect(dsAddr);

      // FIXME investigate why we need to add a few more bytes (82) than the payload
      // the receiver blocks otherwise (not exactly the struct socket_addr_un).
      // smells like some TCP/IP overhead ... (?)
      ds.setReceiveBufferSize(PAYLOAD_SIZE + 82);

      AtomicBoolean keepRunning = new AtomicBoolean(true);
      Executors.newSingleThreadScheduledExecutor().schedule(() -> {
        keepRunning.set(false);
      }, NUM_MILLISECONDS, TimeUnit.MILLISECONDS);

      AtomicLong readTotal = new AtomicLong();
      long sentTotal = 0;

      new Thread() {
        final DatagramPacket dp = new DatagramPacket(new byte[PAYLOAD_SIZE], PAYLOAD_SIZE);

        @Override
        public void run() {
          try {
            while (!Thread.interrupted()) {
              ds.receive(dp);
              int read = dp.getLength();
              if (read != PAYLOAD_SIZE && read != 0) {
                throw new IOException("Unexpected response length: " + read);
              }
              readTotal.addAndGet(dp.getLength());
            }
          } catch (SocketException e) {
            if (keepRunning.get()) {
              e.printStackTrace();
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }.start();

      long time = System.currentTimeMillis();

      DatagramPacket dp = new DatagramPacket(new byte[PAYLOAD_SIZE], PAYLOAD_SIZE);
      byte[] data = dp.getData();
      for (int i = 0; i < data.length; i++) {
        data[i] = (byte) i;
      }

      while (keepRunning.get()) {
        dc.send(dp);
        sentTotal += PAYLOAD_SIZE;
      }
      time = System.currentTimeMillis() - time;
      ds.close(); // terminate server

      long readTotal0 = readTotal.get();

      reportResults("junixsocket DatagramPacket", ((1000f * readTotal0 / time) / 1000f / 1000f)
          + " MB/s for payload size " + PAYLOAD_SIZE + "; " + String.format(Locale.ENGLISH,
              "%.1f%% packet loss", 100 * (1 - (readTotal0 / (float) sentTotal))));
    }
  }

  @Test
  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_DATAGRAMS)
  public void testJUnixSocketDatagramChannel() throws Exception {
    testJUnixSocketDatagramChannel(false);
  }

  @Test
  @AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_DATAGRAMS)
  public void testJUnixSocketDatagramChannelDirect() throws Exception {
    testJUnixSocketDatagramChannel(true);
  }

  private void testJUnixSocketDatagramChannel(boolean direct) throws Exception {
    AFUNIXSocketAddress dsAddr = AFUNIXSocketAddress.of(SocketTestBase.newTempFile());
    AFUNIXSocketAddress dcAddr = AFUNIXSocketAddress.of(SocketTestBase.newTempFile());
    assertNotEquals(dsAddr, dcAddr);

    try (DatagramChannel ds = AFUNIXDatagramSocket.newInstance().getChannel();
        DatagramChannel dc = AFUNIXDatagramSocket.newInstance().getChannel()) {
      ds.bind(dsAddr);
      dc.bind(dcAddr).connect(dsAddr);

      testSocketDatagramChannel("junixsocket DatagramChannel", ds, dc, direct);
    }
  }

  @Test
  @SystemPropertyRequirement(property = "org.newsclub.net.unix.throughput-test.ip.enabled", value = "1")
  public void testUDPLoopback() throws Exception {
    testUDPLoopbackDatagramChannel(false);
  }

  @Test
  @SystemPropertyRequirement(property = "org.newsclub.net.unix.throughput-test.ip.enabled", value = "1")
  public void testUDPLoopbackDirectBuffer() throws Exception {
    testUDPLoopbackDatagramChannel(true);
  }

  private void testUDPLoopbackDatagramChannel(boolean direct) throws Exception {
    SocketAddress dsAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    SocketAddress dcAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    try (DatagramChannel ds = SelectorProvider.provider().openDatagramChannel();
        DatagramChannel dc = SelectorProvider.provider().openDatagramChannel()) {
      ds.bind(dsAddr);
      dc.bind(dcAddr).connect(ds.getLocalAddress());
      ds.connect(dc.getLocalAddress());

      assertNotEquals(ds.getLocalAddress(), dc.getLocalAddress());

      testSocketDatagramChannel("UDP-Loopback DatagramChannel", ds, dc, direct);
    }
  }

  private void testSocketDatagramChannel(String id, DatagramChannel ds, DatagramChannel dc,
      boolean direct) throws IOException {
    // FIXME investigate why we need to add a few more bytes (82) than the payload
    // the receiver blocks otherwise (not exactly the struct socket_addr_un).
    // smells like some TCP/IP overhead ... (?)
    ds.setOption(StandardSocketOptions.SO_RCVBUF, (PAYLOAD_SIZE + 82));

    AtomicBoolean keepRunning = new AtomicBoolean(true);
    Executors.newSingleThreadScheduledExecutor().schedule(() -> {
      keepRunning.set(false);
    }, NUM_MILLISECONDS, TimeUnit.MILLISECONDS);

    AtomicLong readTotal = new AtomicLong();
    long sentTotal = 0;

    new Thread() {
      @Override
      public void run() {
        final ByteBuffer receiveBuffer = direct ? ByteBuffer.allocateDirect(PAYLOAD_SIZE)
            : ByteBuffer.allocate(PAYLOAD_SIZE);
        try {
          while (!Thread.interrupted()) {
            int read = ds.read(receiveBuffer);
            receiveBuffer.rewind();
            if (read != PAYLOAD_SIZE && read != 0) {
              throw new IOException("Unexpected response length: " + read);
            }
            readTotal.addAndGet(read);
          }
        } catch (ClosedChannelException | SocketException e) {
          if (keepRunning.get()) {
            e.printStackTrace();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }.start();

    long time = System.currentTimeMillis();

    final ByteBuffer sendBuffer = direct ? ByteBuffer.allocateDirect(PAYLOAD_SIZE) : ByteBuffer
        .allocate(PAYLOAD_SIZE);

    while (keepRunning.get()) {
      int written = dc.write(sendBuffer);
      if (written != PAYLOAD_SIZE && written != 0) {
        throw new IOException("Unexpected written length: " + written);
      }

      sentTotal += PAYLOAD_SIZE;
      sendBuffer.rewind();
    }
    time = System.currentTimeMillis() - time;
    ds.close(); // terminate server

    long readTotal0 = readTotal.get();

    reportResults(id + " direct=" + direct, ((1000f * readTotal0 / time) / 1000f / 1000f)
        + " MB/s for payload size " + PAYLOAD_SIZE + "; " + String.format(Locale.ENGLISH,
            "%.1f%% packet loss", 100 * (1 - (readTotal0 / (float) sentTotal))));

  }
}
