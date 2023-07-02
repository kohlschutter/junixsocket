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

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.newsclub.net.unix.java.JavaAddressSpecifics;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Some base functionality for socket tests.
 *
 * This class provides access to the {@link AddressSpecifics} methods for the socket implementation
 * under test. It is essential to use these wrapper methods in tests instead of directly calling the
 * {@link AFSocket} etc. methods: Some socket implementations (and sometimes only in certain
 * kernel/environment configurations) may expose unexpected behavior that is otherwise hard to
 * catch.
 *
 * This is especially relevant when connecting/binding sockets (see
 * {@link #connectSocket(Socket, SocketAddress)}, #bindServerSocket(ServerSocket, SocketAddress)},
 * etc.)
 *
 * @author Christian Kohlschuetter
 */
@SuppressWarnings({"PMD.AbstractClassWithoutAbstractMethod", "PMD.CouplingBetweenObjects"})
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public abstract class SocketTestBase<A extends SocketAddress> { // NOTE: needs to be public for
                                                                // junit

  private static final Pattern PAT_LINUX_VERSION = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.");

  private static final File SOCKET_FILE = initSocketFile();
  private static final Random RANDOM = new Random();

  private final SocketAddress serverAddress;
  private Exception caller = new Exception();
  private final AddressSpecifics<A> asp;

  @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
  protected SocketTestBase(AddressSpecifics<A> asp) {
    this.asp = asp;
    try {
      this.serverAddress = newTempAddress();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static File initSocketFile() {
    return SocketTestBase.newTempFile(System.getProperty("org.newsclub.net.unix.testsocket"));
  }

  public static File socketFile() {
    return SOCKET_FILE;
  }

  protected final SocketAddress newTempAddress() throws IOException {
    return asp.newTempAddress();
  }

  @BeforeEach
  public void ensureSocketFileIsDeleted() throws IOException {
    Files.deleteIfExists(SOCKET_FILE.toPath());
  }

  @AfterAll
  public static void tearDownClass() throws IOException {
    Files.deleteIfExists(SOCKET_FILE.toPath());
  }

  public static File newTempFile() {
    return newTempFile(null);
  }

  public static File newTempFile(String name) {
    File f;
    try {
      f = (name == null) ? File.createTempFile("jutest", ".sock") : new File(name);
      f.deleteOnExit(); // always delete on exit to clean-up sockets created under that name
    } catch (IOException e) {
      throw new IllegalStateException("Can't create temporary file", e);
    }
    if (!f.delete()) {
      throw new IllegalStateException("Could not delete temporary file that we just created: " + f);
    }
    return f;
  }

  protected ServerSocket startServer() throws IOException {
    caller = new Exception("Test server stacktrace");
    final ServerSocket server = newServerSocket();
    SocketAddress bindAddr = getServerBindAddress();
    try {
      asp.bindServerSocket(server, getServerBindAddress());
    } catch (BindException e) {
      if (asp instanceof JavaAddressSpecifics && ((InetSocketAddress) bindAddr).getPort() == 0) {
        asp.bindServerSocket(server, null);
      } else {
        throw e;
      }
    }
    return server;
  }

  /**
   * Checks if an optional connection check via {@link AFSocket#checkConnectionClosed()}, is to be
   * run upon {@link AFServerSocket#accept()}.
   *
   * Override to enable.
   *
   * @return {@code true} if enabled; default is {@code false} = disabled.
   */
  protected boolean shouldDoConnectionCheckUponAccept() {
    return false;
  }

  protected enum ExceptionHandlingDecision {
    RAISE, IGNORE
  }

  protected abstract class ServerThread extends Thread implements AutoCloseable {
    private final ServerSocket serverSocket;
    private volatile Exception exception = null;
    private volatile Error error = null;
    private final AtomicBoolean loop = new AtomicBoolean(true);
    private final Semaphore sema = new Semaphore(1);
    private final Semaphore readySema = new Semaphore(0);

    @SuppressFBWarnings("SC_START_IN_CTOR")
    protected ServerThread() throws IOException, InterruptedException {
      super();
      serverSocket = startServer(); // NOPMD
      setDaemon(true);

      start();
      readySema.acquire();
    }

    @Override
    public final void start() {
      super.start();
    }

    protected ServerSocket startServer() throws IOException {
      return SocketTestBase.this.startServer();
    }

    @Override
    public void close() throws Exception {
      shutdown();
      checkException();
    }

    /**
     * Stops the server.
     *
     * @throws IOException on error.
     */
    public void shutdown() throws IOException {
      stopAcceptingConnections();
      if (serverSocket != null) {
        onServerSocketClose();
        serverSocket.close();
      }
    }

    /**
     * Callback used to handle a connection call.
     *
     * After returning from this call, the socket is closed.
     *
     * Use {@link #stopAcceptingConnections()} to stop accepting new calls.
     *
     * @param sock The socket to handle.
     * @throws IOException upon error.
     */
    protected abstract void handleConnection(Socket sock) throws IOException;

    /**
     * Called from within {@link #handleConnection(Socket)} to tell the server to no longer accept
     * new calls and to terminate the server thread.
     *
     * Note that this will lead to existing client connections to be closed.
     *
     * If you want to deny new connections but finish your work on the client side (in another
     * thread), then please use semaphores etc. to ensure reaching a safe state before calling this
     * method.
     */
    protected void stopAcceptingConnections() {
      loop.set(false);
    }

    protected void onServerSocketClose() {
      stopAcceptingConnections();
    }

    /**
     * Returns the server socket.
     *
     * @return the server socket.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public ServerSocket getServerSocket() {
      return serverSocket;
    }

    /**
     * Returns the server's address to connect to.
     *
     * @return the address.
     */
    public SocketAddress getServerAddress() {
      return getServerSocket().getLocalSocketAddress();
    }

    /**
     * Called upon receiving an exception that may be handled specifically.
     *
     * @param e The exception
     * @return {@link ExceptionHandlingDecision#RAISE} if we should handle the exception somehow,
     *         {@link ExceptionHandlingDecision#IGNORE} if we should pretend the exception never
     *         occurred.
     */
    protected ExceptionHandlingDecision handleException(Exception e) {
      return ExceptionHandlingDecision.RAISE;
    }

    protected void acceptAndHandleConnection() throws IOException {
      boolean acceptSuccess = false;
      sema.release();
      try (Socket sock = serverSocket.accept()) {
        try {
          sema.acquire();
        } catch (InterruptedException e) {
          throw (InterruptedIOException) new InterruptedIOException(e.getMessage()).initCause(e);
        }
        acceptSuccess = true;

        if (sock instanceof AFSocket<?>) {
          AFSocket<?> afs = (AFSocket<?>) sock;
          if (shouldDoConnectionCheckUponAccept() && afs.checkConnectionClosed()) {
            acceptSuccess = false;
          }
        }

        if (acceptSuccess) {
          handleConnection(sock);
        }
      } catch (IOException e) { // NOPMD.ExceptionAsFlowControl
        if (!acceptSuccess) {
          // ignore: connection closed before accept could complete
          if (serverSocket.isClosed()) {
            stopAcceptingConnections();
          }
        } else {
          throw e;
        }
      } finally {
        if (acceptSuccess) {
          sema.release();
        }
      }
    }

    @Override
    public final void run() {
      try {
        loop.set(true);
        readySema.release();
        onServerReady();
        while (loop.get()) {
          acceptAndHandleConnection();
        }
      } catch (Exception e) {
        if (!loop.get()) {
          // ignore
        } else if (handleException(e) != ExceptionHandlingDecision.IGNORE) {
          e.addSuppressed(caller);
          exception = e;
        }
      } catch (Error e) {
        error = e;
      }
    }

    /**
     * Called right before starting the accept loop.
     */
    protected void onServerReady() {
    }

    /**
     * Checks if there were any exceptions thrown during the lifetime of this ServerThread.
     *
     * NOTE: This call blocks until the Thread actually terminates.
     *
     * @throws Exception upon error.
     */
    public void checkException() throws Exception {
      boolean serverStillRunning = !sema.tryAcquire(30, TimeUnit.SECONDS);
      if (error != null) {
        throw error;
      }
      if (exception != null) {
        throw exception;
      }
      if (serverStillRunning) {
        throw new IllegalStateException("SocketTestBase server still running after 30 seconds");
      }
    }
  }

  protected abstract class AFUNIXServerThread extends ServerThread {
    protected AFUNIXServerThread() throws IOException, InterruptedException {
      super();
    }

    @Override
    protected final void handleConnection(Socket sock) throws IOException {
      handleConnection((AFUNIXSocket) sock);
    }

    protected abstract void handleConnection(AFUNIXSocket sock) throws IOException;
  }

  /**
   * Sleeps for the given amount of milliseconds.
   *
   * @param ms The duration in milliseconds.
   * @throws InterruptedIOException when interrupted.
   */
  protected void sleepFor(final int ms) throws InterruptedIOException {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw (InterruptedIOException) new InterruptedIOException("sleep interrupted").initCause(e);
    }
  }

  protected final SocketAddress getServerBindAddress() {
    return serverAddress;
  }

  protected final Socket newSocket() throws IOException {
    return asp.newSocket();
  }

  protected final Socket newStrictSocket() throws IOException {
    return asp.newStrictSocket();
  }

  protected final DatagramSocket newDatagramSocket() throws IOException {
    return asp.newDatagramSocket();
  }

  protected final DatagramChannel newDatagramChannel() throws IOException {
    return asp.newDatagramChannel();
  }

  protected final ServerSocket newServerSocket() throws IOException {
    return asp.newServerSocket();
  }

  protected final ServerSocket newServerSocketBindOn(SocketAddress addr) throws IOException {
    return asp.newServerSocketBindOn(addr);
  }

  protected final ServerSocket newServerSocketBindOn(SocketAddress addr, boolean deleteOnClose)
      throws IOException {
    return asp.newServerSocketBindOn(addr, deleteOnClose);
  }

  protected final SocketAddress newTempAddressForDatagram() throws IOException {
    return asp.newTempAddressForDatagram();
  }

  protected final SocketAddress unwrap(InetAddress addr, int port) throws SocketException {
    return asp.unwrap(addr, port);
  }

  protected final SelectorProvider selectorProvider() throws IOException {
    return asp.selectorProvider();
  }

  protected CloseablePair<? extends SocketChannel> newSocketPair() throws IOException {
    return asp.newSocketPair();
  }

  protected CloseablePair<? extends DatagramChannel> newDatagramSocketPair() throws IOException {
    return asp.newDatagramSocketPair();
  }

  protected Socket connectTo(SocketAddress endpoint) throws IOException {
    return asp.connectTo(endpoint);
  }

  protected final void bindServerSocket(ServerSocket serverSocket, SocketAddress bindpoint)
      throws IOException {
    asp.bindServerSocket(serverSocket, bindpoint);
  }

  protected final void bindServerSocket(ServerSocketChannel serverSocketChannel,
      SocketAddress bindpoint) throws IOException {
    asp.bindServerSocket(serverSocketChannel, bindpoint);
  }

  protected final void bindServerSocket(ServerSocketChannel serverSocketChannel,
      SocketAddress bindpoint, int backlog) throws IOException {
    asp.bindServerSocket(serverSocketChannel, bindpoint, backlog);
  }

  protected final void connectSocket(Socket socket, SocketAddress endpoint) throws IOException {
    asp.connectSocket(socket, endpoint);
  }

  protected final boolean connectSocket(SocketChannel socketChannel, SocketAddress endpoint)
      throws IOException {
    return asp.connectSocket(socketChannel, endpoint);
  }

  protected CloseablePair<? extends Socket> newInterconnectedSockets() throws IOException {
    return asp.newInterconnectedSockets();
  }

  protected Random getRandom() {
    return RANDOM;
  }

  /**
   * Returns the Linux kernel's major and minor version as an integer array (i.e., {@code 5.10.2 ->
   * int[]{5,10}}), or {@code null} if the running system isn't Linux or the version could not be
   * determined.
   *
   * @return The running Linux kernels' major and minor version as an integer array, or
   *         {@code null}.
   */
  protected int[] getLinuxMajorMinorVersion() {
    if (!"Linux".equals(System.getProperty("os.name"))) {
      return null; // NOPMD.PMD.ReturnEmptyCollectionRatherThanNull
    }
    Matcher m = PAT_LINUX_VERSION.matcher(System.getProperty("os.version", ""));
    if (m.find()) {
      return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    } else {
      return null; // NOPMD.PMD.ReturnEmptyCollectionRatherThanNull
    }
  }
}
