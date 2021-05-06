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

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Some base functionality for socket tests.
 * 
 * @author Christian Kohlschuetter
 */
public class SocketTestBase { // NOTE: needs to be public for junit
  private static final File SOCKET_FILE = initSocketFile();
  private final AFUNIXSocketAddress serverAddress;
  private Exception caller = new Exception();

  protected SocketTestBase() throws IOException {
    this.serverAddress = new AFUNIXSocketAddress(SOCKET_FILE);
  }

  @BeforeEach
  public void ensureSocketFileIsDeleted() throws IOException {
    Files.deleteIfExists(SOCKET_FILE.toPath());
  }

  @AfterAll
  public static void tearDownClass() throws IOException {
    Files.deleteIfExists(SOCKET_FILE.toPath());
  }

  protected AFUNIXSocketAddress getServerAddress() {
    return serverAddress;
  }

  static File initSocketFile() {
    File f;
    String explicitFile = System.getProperty("org.newsclub.net.unix.testsocket");
    if (explicitFile != null) {
      f = new File(explicitFile);
    } else {
      try {
        f = File.createTempFile("jutest", ".sock");
        f.deleteOnExit(); // always delete on exit to clean-up sockets created under that name
      } catch (IOException e) {
        throw new IllegalStateException("Can't create temporary file", e);
      }
    }
    if (!f.delete()) {
      throw new IllegalStateException("Could not delete temporary file that we just created: " + f);
    }
    return f;
  }

  protected File getSocketFile() {
    return SOCKET_FILE;
  }

  protected AFUNIXServerSocket startServer() throws IOException {
    caller = new Exception();
    final AFUNIXServerSocket server = AFUNIXServerSocket.newInstance();
    server.bind(serverAddress);
    return server;
  }

  protected AFUNIXSocket connectToServer() throws IOException {
    return AFUNIXSocket.connectTo(serverAddress);
  }

  protected AFUNIXSocket connectToServer(AFUNIXSocket socket) throws IOException {
    socket.connect(serverAddress);
    return socket;
  }

  protected enum ExceptionHandlingDecision {
    RAISE, IGNORE
  }

  protected abstract class ServerThread extends Thread implements AutoCloseable {
    private final AFUNIXServerSocket serverSocket;
    private volatile Exception exception = null;
    private final AtomicBoolean loop = new AtomicBoolean(true);
    private final Semaphore sema = new Semaphore(0);

    @SuppressFBWarnings("SC_START_IN_CTOR")
    protected ServerThread() throws IOException {
      super();
      serverSocket = startServer(); // NOPMD
      setDaemon(true);

      start();
    }

    protected AFUNIXServerSocket startServer() throws IOException {
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
    protected abstract void handleConnection(final AFUNIXSocket sock) throws IOException;

    /**
     * Called from within {@link #handleConnection(AFUNIXSocket)} to tell the server to no longer
     * accept new calls and to terminate the server thread.
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
    }

    public ServerSocket getServerSocket() {
      return serverSocket;
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
      try (AFUNIXSocket sock = serverSocket.accept()) {
        handleConnection(sock);
      }
    }

    @Override
    public final void run() {
      try {
        loop.set(true);
        while (loop.get()) {
          acceptAndHandleConnection();
        }
      } catch (IOException e) {
        if (!loop.get()) {
          // ignore
        } else if (handleException(e) != ExceptionHandlingDecision.IGNORE) {
          e.addSuppressed(caller);
          exception = e;
        }
      } finally {
        sema.release();
      }
    }

    /**
     * Checks if there were any exceptions thrown during the lifetime of this ServerThread.
     * 
     * NOTE: This call blocks until the Thread actually terminates.
     * 
     * @throws Exception upon error.
     */
    public void checkException() throws Exception {
      sema.acquire();
      if (exception != null) {
        throw exception;
      }
    }
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
}
