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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXServerSocketChannel;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.ThreadUtil;

/**
 * Test interrupt-related behavior, as discussed in
 * <a href="https://github.com/kohlschutter/junixsocket/issues/158">issue 158</a>.
 *
 * @author https://github.com/cenodis
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD")
public class InterruptIssue158Test {

  private static final Path SOCKET_PATH;
  private static final AFUNIXSocketAddress SOCKET_ADDR;

  static {
    try {
      SOCKET_ADDR = AFUNIXSocketAddress.ofNewTempFile();
      SOCKET_PATH = SOCKET_ADDR.getFile().toPath();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<Arguments> clientProvider() {
    return Arrays.asList( //
        // variants
        socket(false, AFUNIXSocket::newInstance, s -> s.connect(SOCKET_ADDR), SocketException.class,
            AFUNIXSocket::isClosed), //
        socket(true, () -> AFUNIXSocket.connectTo(SOCKET_ADDR), s -> s.getInputStream().read(),
            SocketException.class, AFUNIXSocket::isClosed), //
        socket(true, () -> AFUNIXSocket.connectTo(SOCKET_ADDR), s -> s.getOutputStream().write(10),
            SocketException.class, AFUNIXSocket::isClosed), socket(false, AFUNIXSocketChannel::open,
                s -> s.connect(SOCKET_ADDR), ClosedChannelException.class, s -> !s.isOpen()), //
        socket(true, InterruptIssue158Test::connectSocketChannel, s -> s.read(ByteBuffer.allocate(
            1)), ClosedChannelException.class, s -> !s.isOpen()), //
        socket(true, InterruptIssue158Test::connectSocketChannel, s -> s.write(ByteBuffer.allocate(
            1)), ClosedChannelException.class, s -> !s.isOpen()) //
    );
  }

  private static List<Arguments> serverProvider() {
    return Arrays.asList( //
        serverSocket(() -> AFUNIXServerSocket.bindOn(SOCKET_ADDR), AFUNIXServerSocket::accept,
            SocketException.class, AFUNIXServerSocket::isClosed), //
        serverSocket(InterruptIssue158Test::bindServerSocketChannel,
            AFUNIXServerSocketChannel::accept, ClosedChannelException.class, s -> !s.isOpen())//
    );
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("clientProvider")
  <T extends AutoCloseable> void testClientInterruption(boolean acceptConnections,
      IOSupplier<T> socket, IOConsumer<T> blockingOp, Class<?> expectedException,
      Predicate<T> closeCheck) throws Throwable {
    withServer(acceptConnections, () -> testSocketInterruption(false, socket, blockingOp,
        expectedException, closeCheck));
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("clientProvider")
  <T extends AutoCloseable> void testClientInterruptionWithDelay(boolean acceptConnections,
      IOSupplier<T> socket, IOConsumer<T> blockingOp, Class<?> expectedException,
      Predicate<T> closeCheck) throws Throwable {
    withServer(acceptConnections, () -> testSocketInterruption(true, socket, blockingOp,
        expectedException, closeCheck));
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("serverProvider")
  <T extends AutoCloseable> void testServerInterruption(IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck)
      throws Throwable {
    try {
      testSocketInterruption(false, socket, blockingOp, expectedException, closeCheck);
    } finally {
      Files.deleteIfExists(SOCKET_PATH);
    }
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("serverProvider")
  <T extends AutoCloseable> void testServerInterruptionWithDelay(IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck)
      throws Throwable {
    try {
      testSocketInterruption(true, socket, blockingOp, expectedException, closeCheck);
    } finally {
      Files.deleteIfExists(SOCKET_PATH);
    }
  }

  <T extends AutoCloseable> void testSocketInterruption(boolean delay, IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck)
      throws Throwable {
    AtomicReference<Throwable> exceptionHolder = new AtomicReference<>();
    CountDownLatch ready = new CountDownLatch(1);
    Thread t = ThreadUtil.startNewDaemonThread(true, () -> exceptionHolder.set(runOperation(ready,
        socket, blockingOp, expectedException, closeCheck)));

    ready.await();
    if (delay) {
      Thread.sleep(500);
    }
    t.interrupt();
    t.join(Duration.of(1, ChronoUnit.SECONDS).toMillis());
    if (t.isAlive()) {
      throw new RuntimeException("Thread failed to terminate after interrupt");
    }
    Throwable thrownException = exceptionHolder.get();
    if (thrownException != null) {
      throw thrownException;
    }
  }

  private static void withServer(boolean acceptConnections, ThrowingRunnable func)
      throws Throwable {
    try (ServerSocketChannel serverSocket = AFUNIXServerSocketChannel.open()) {
      serverSocket.bind(SOCKET_ADDR);
      Thread serverThread = null;
      if (acceptConnections) {
        serverThread = ThreadUtil.startNewDaemonThread(false, () -> {
          List<SocketChannel> clients = new ArrayList<>();
          while (serverSocket.isOpen()) {
            try {
              SocketChannel socket = serverSocket.accept();
              clients.add(socket);
            } catch (ClosedChannelException e) {
              return;
            } catch (IOException e) {
              throw new RuntimeException("Unable to accept socket ", e);
            } finally {
              for (SocketChannel client : clients) {
                try {
                  client.close();
                } catch (IOException ignored) {
                  // ignored
                }
              }
            }
          }
        });
      }
      try {
        func.run();
      } finally {
        serverSocket.close();
        if (serverThread != null) {
          serverThread.join();
        }
      }
    } finally {
      Files.deleteIfExists(SOCKET_PATH);
    }
  }

  <T extends AutoCloseable> Throwable runOperation(CountDownLatch ready, IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck) {
    try {
      @SuppressWarnings("resource")
      T sock = socket.get();
      ready.countDown();
      try {
        blockingOp.accept(sock);
      } catch (Exception e) {
        // These tests usually expect the "Thread interrupted" state to be set.
        // However, when we accept any SocketException to be thrown, that state is not
        // deterministic.
        // Also, when we expect any kind of ClosedChannelException, it is only expected to be
        // set when the actual exception thrown is from the ClosedByInterruptException subclass.
        boolean ignoreInterruptState = SocketException.class.equals(expectedException);
        boolean interruptStateOK = Thread.interrupted() || (ClosedChannelException.class.equals(
            expectedException) && !(e instanceof ClosedByInterruptException));

        assertAll(() -> assertInstanceOf(expectedException, e, "Socket exception"),
            () -> assertTrue(ignoreInterruptState || interruptStateOK, "Thread interrupted"),
            () -> assertTrue(closeCheck.test(sock), "Socket closed"));
      } finally {
        ready.countDown();
        if (sock != null) {
          try {
            sock.close();
          } catch (Exception e) {
            throw new RuntimeException("Unable to clean up socket", e);
          }
        }
      }
    } catch (Throwable e) {
      return e;
    }
    return null;
  }

  private static <T> Arguments socket(boolean acceptConnections, IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck) {
    return Arguments.of(acceptConnections, socket, blockingOp, expectedException, closeCheck);
  }

  private static <T> Arguments serverSocket(IOSupplier<T> socket, IOConsumer<T> blockingOp,
      Class<?> expectedException, Predicate<T> closeCheck) {
    return Arguments.of(socket, blockingOp, expectedException, closeCheck);
  }

  private static AFUNIXSocketChannel connectSocketChannel() throws IOException {
    AFUNIXSocketChannel socket = AFUNIXSocketChannel.open();
    socket.connect(SOCKET_ADDR);
    return socket;
  }

  private static AFUNIXServerSocketChannel bindServerSocketChannel() throws IOException {
    AFUNIXServerSocketChannel socket = AFUNIXServerSocketChannel.open();
    socket.bind(SOCKET_ADDR);
    return socket;
  }

  private interface IOSupplier<T> {
    T get() throws IOException;
  }

  private interface IOConsumer<T> {
    void accept(T t) throws IOException;
  }

  private interface ThrowingRunnable {
    void run() throws Throwable;
  }
}