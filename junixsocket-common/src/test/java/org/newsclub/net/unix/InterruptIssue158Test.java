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
package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.kohlschutter.testutil.TestAbortedNotAnIssueException;
import com.kohlschutter.util.SystemPropertyUtil;

/**
 * Test interrupt-related behavior, as discussed in
 * <a href="https://github.com/kohlschutter/junixsocket/issues/158">issue 158</a>.
 *
 * @author https://github.com/cenodis
 * @author Christian Kohlschütter
 */
@SuppressWarnings({"PMD", "exports"})
@TestInstance(Lifecycle.PER_CLASS)
public abstract class InterruptIssue158Test<A extends SocketAddress> extends SocketTestBase<A> {
  // enable for additional debugging to System.out
  private static boolean DEBUG = SystemPropertyUtil.getBooleanSystemProperty(
      "selftest.issue.158.debug", false);
  private static boolean DEBUG_VERBOSE = (System.getProperty("com.kohlschutter.selftest") == null)
      && SystemPropertyUtil.getBooleanSystemProperty("selftest.issue.158.debug.verbose", true);

  private static boolean DELAY_CLOSE = SystemPropertyUtil.getBooleanSystemProperty(
      "selftest.issue.158.delay-close", true);

  private A address = newAddress();
  private TestInfo testInfo;
  private List<AutoCloseable> closeables = new ArrayList<>();

  protected InterruptIssue158Test(AddressSpecifics<A> asp) {
    super(asp);
  }

  @BeforeEach
  public void beforeEach(TestInfo info) {
    this.testInfo = info;
    this.address = newAddress();
  }

  @SuppressWarnings("unchecked")
  private A newAddress() {
    try {
      return (A) newTempAddress();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void closeAfterTest() {
    deleteSocketFile(address);

    for (AutoCloseable cl : closeables) {
      try {
        cl.close();
      } catch (Exception e) {
        // ignore
      }
    }
    closeables.clear();
  }

  @AfterEach
  public void afterEach() {
    closeAfterTest();
  }

  protected abstract void deleteSocketFile(A sa);

  public List<Arguments> clientProvider() {
    return Arrays.asList( //
        // variants
        socket(false, this::newSocket, s -> s.connect(address), SocketException.class,
            Socket::isClosed), //
        socket(true, () -> newConnectedSocket(address), s -> s.getInputStream().read(),
            SocketException.class, Socket::isClosed), //
        socket(true, () -> newConnectedSocket(address), s -> s.getOutputStream().write(10),
            SocketException.class, Socket::isClosed), //
        socket(false, this::newSocketChannel, s -> s.connect(address),
            ClosedByInterruptException.class, s -> !s.isOpen()), //
        socket(true, this::connectSocketChannel, s -> s.read(ByteBuffer.allocate(1)),
            ClosedByInterruptException.class, s -> !s.isOpen()), //
        socket(true, this::connectSocketChannel, s -> s.write(ByteBuffer.allocate(1)),
            ClosedByInterruptException.class, s -> !s.isOpen()) //
    );
  }

  public List<Arguments> serverProvider() {
    return Arrays.asList( //
        serverSocket(() -> registerCloseable(newServerSocketBindOn(address)), ServerSocket::accept,
            SocketException.class, ServerSocket::isClosed), //
        serverSocket(this::bindServerSocketChannel, ServerSocketChannel::accept,
            ClosedByInterruptException.class, s -> !s.isOpen())//
    );
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("clientProvider")
  public <T extends AutoCloseable> void testClientInterruption(boolean acceptConnections,
      IOSupplier<T> socket, IOConsumer<T> blockingOp, Class<?> expectedException,
      Predicate<T> closeCheck) throws Throwable {
    withServer(acceptConnections, () -> testSocketInterruption(false, socket, blockingOp,
        expectedException, closeCheck));
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("clientProvider")
  public <T extends AutoCloseable> void testClientInterruptionWithDelay(boolean acceptConnections,
      IOSupplier<T> socket, IOConsumer<T> blockingOp, Class<?> expectedException,
      Predicate<T> closeCheck) throws Throwable {
    withServer(acceptConnections, () -> testSocketInterruption(true, socket, blockingOp,
        expectedException, closeCheck));
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("serverProvider")
  public <T extends AutoCloseable> void testServerInterruption(IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck)
      throws Throwable {
    testSocketInterruption(false, socket, blockingOp, expectedException, closeCheck);
  }

  @ParameterizedTest(name = "variant {index}")
  @MethodSource("serverProvider")
  public <T extends AutoCloseable> void testServerInterruptionWithDelay(IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck)
      throws Throwable {
    testSocketInterruption(true, socket, blockingOp, expectedException, closeCheck);
  }

  public <T extends AutoCloseable> void testSocketInterruption(boolean delay, IOSupplier<T> socket,
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
      // Thread.interrupt is not guaranteed to succeed
      // observed with graalvm-jdk-17.0.9+11.1 when building with agent for
      // junixsocket-native-graalvm
      // What we need to do here is to close all socket-related resources and try again
      closeAfterTest();
      t.interrupt();
      t.join(Duration.of(1, ChronoUnit.SECONDS).toMillis());
      if (t.isAlive()) {
        throw new RuntimeException("Thread failed to terminate after interrupt");
      }
    }
    Throwable thrownException = exceptionHolder.get();
    if (thrownException != null) {
      throw thrownException;
    }
  }

  private <C extends AutoCloseable> C registerCloseable(C closeable) {
    closeables.add(closeable);
    return closeable;
  }

  private void withServer(boolean acceptConnections, ThrowingRunnable func) throws Throwable {
    Semaphore done = new Semaphore(0);
    try (ServerSocketChannel serverSocket = registerCloseable(newServerSocketChannel())) {
      serverSocket.bind(address);
      Thread serverThread = null;
      if (acceptConnections) {
        serverThread = ThreadUtil.startNewDaemonThread(false, () -> {
          while (serverSocket.isOpen()) {
            SocketChannel socket = null;
            try {
              socket = serverSocket.accept();
            } catch (ClosedChannelException e) {
              return;
            } catch (IOException e) {
              throw new RuntimeException("Unable to accept socket ", e);
            } finally {
              if (socket != null) {
                final SocketChannel socketToClose = socket;
                if (DELAY_CLOSE) {
                  TestUtil.trackFuture(CompletableFuture.runAsync(() -> {
                    try {
                      if (!done.tryAcquire(1, TimeUnit.SECONDS)) {
                        // ignore
                      }
                    } catch (InterruptedException e) {
                      // ignore
                    }
                    try {
                      socketToClose.close();
                    } catch (IOException e) {
                      TestUtil.printStackTrace(e);
                    }
                  }));
                } else {
                  try {
                    socketToClose.close();
                  } catch (IOException e) {
                    TestUtil.printStackTrace(e);
                  }
                }
              }
            }
          }
        });
      }
      try {
        func.run();
      } finally {
        done.release();
        serverSocket.close();
        if (serverThread != null) {
          serverThread.join();
        }
      }
    }
  }

  @SuppressWarnings("Finally") // ErrorProne
  <T extends AutoCloseable> Throwable runOperation(CountDownLatch ready, IOSupplier<T> socket,
      IOConsumer<T> blockingOp, Class<?> expectedException, Predicate<T> closeCheck) {

    boolean supported = false;
    Exception exc = null;
    try {
      @SuppressWarnings({"resource"})
      T sock = registerCloseable(socket.get());
      ready.countDown();

      supported = true;
      try {
        blockingOp.accept(sock);
      } catch (Exception e) {
        exc = e;
        // These tests usually expect the "Thread interrupted" state to be set.
        // However, when we accept any SocketException to be thrown, that state is not
        // deterministic.
        // Also, when we expect any kind of ClosedChannelException, it is only expected to be
        // set when the actual exception thrown is from the ClosedByInterruptException subclass.
        boolean ignoreInterruptState = SocketException.class.equals(expectedException);
        boolean interruptStateOK = Thread.interrupted() || (ClosedChannelException.class
            .isAssignableFrom(expectedException) && !(e instanceof ClosedByInterruptException));

        if (!expectedException.isAssignableFrom(e.getClass())) {
          // log unexpected exception stack trace
          e.printStackTrace();
        }

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
    } catch (TestAbortedNotAnIssueException e) {
      return e;
    } catch (Throwable e) {
      TestUtil.printStackTrace(e);
      return e;
    } finally {
      ready.countDown();
      if (DEBUG) {
        // print concise results for debugging:
        if (DEBUG_VERBOSE) {
          System.out.print(testInfo.getTestClass().get().getName() + "." + testInfo.getTestMethod()
              .get().getName() + " " + testInfo.getDisplayName() + ": ");
        }
        System.out.println((supported ? (exc == null ? "no exception" : exc) : "unsupported"));
      }
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

  private SocketChannel connectSocketChannel() throws IOException {
    SocketChannel socket = registerCloseable(newSocketChannel());
    socket.connect(address);
    return socket;
  }

  private ServerSocketChannel bindServerSocketChannel() throws IOException {
    ServerSocketChannel socket = registerCloseable(newServerSocketChannel());
    try {
      try {
        socket.bind(address);
      } catch (BindException e) {
        // With Inet sockets, our reserved address may just have been taken by another process
        // so let's try again
        address = newAddress();
        socket.bind(address);
      }
    } catch (BindException e) {
      throw (BindException) new BindException(e.getMessage() + ": " + address).initCause(e);
    }
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
