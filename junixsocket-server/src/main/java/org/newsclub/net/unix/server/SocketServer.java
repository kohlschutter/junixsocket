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
package org.newsclub.net.unix.server;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A base implementation for a simple, multi-threaded socket server.
 *
 * @author Christian Kohlschütter
 * @see AFSocketServer
 * @param <A> The supported address type.
 * @param <S> The supported {@link Socket} type.
 * @param <V> The supported {@link ServerSocket} type.
 */
public abstract class SocketServer<A extends SocketAddress, S extends Socket, V extends ServerSocket> {
  private static final ScheduledExecutorService TIMEOUTS = Executors.newScheduledThreadPool(1);

  private final @NonNull A listenAddress;

  private int maxConcurrentConnections = Runtime.getRuntime().availableProcessors();
  private int serverTimeout = 0; // by default, the server doesn't timeout.
  private int socketTimeout = (int) TimeUnit.SECONDS.toMillis(60);
  private int serverBusyTimeout = (int) TimeUnit.SECONDS.toMillis(1);

  private Thread listenThread = null;
  private V serverSocket;
  private final AtomicBoolean stopRequested = new AtomicBoolean(false);
  private final AtomicBoolean ready = new AtomicBoolean(false);

  private final Object connectionsMonitor = new Object();
  private ForkJoinPool connectionPool;

  private ScheduledFuture<IOException> timeoutFuture;
  private final V reuseSocket;

  /**
   * Creates a server using the given, bound {@link ServerSocket}.
   *
   * @param serverSocket The server socket to use (must be bound).
   */
  @SuppressWarnings({"null", "unchecked"})
  public SocketServer(V serverSocket) {
    this((A) Objects.requireNonNull(serverSocket).getLocalSocketAddress(), serverSocket);
  }

  /**
   * Creates a server using the given {@link SocketAddress}.
   *
   * @param listenAddress The address to bind the socket on.
   */
  @SuppressWarnings("null")
  public SocketServer(A listenAddress) {
    this(listenAddress, null);
  }

  @SuppressWarnings("null")
  private SocketServer(A listenAddress, V preboundSocket) {
    Objects.requireNonNull(listenAddress, "listenAddress");
    this.reuseSocket = preboundSocket;

    this.listenAddress = listenAddress;
  }

  /**
   * Returns the maximum number of concurrent connections.
   *
   * @return The maximum number of concurrent connections.
   */
  public int getMaxConcurrentConnections() {
    return maxConcurrentConnections;
  }

  /**
   * Sets the maximum number of concurrent connections.
   *
   * @param maxConcurrentConnections The new maximum.
   */
  public void setMaxConcurrentConnections(int maxConcurrentConnections) {
    if (connectionPool != null) {
      throw new IllegalStateException("Already configured");
    }
    this.maxConcurrentConnections = maxConcurrentConnections;
  }

  /**
   * Returns the server timeout (in milliseconds).
   *
   * @return The server timeout in milliseconds (0 = no timeout).
   */
  public int getServerTimeout() {
    return serverTimeout;
  }

  /**
   * Sets the server timeout (in milliseconds).
   *
   * @param timeout The new timeout in milliseconds (0 = no timeout).
   */
  public void setServerTimeout(int timeout) {
    synchronized (this) {
      if (serverSocket != null) {
        throw new IllegalStateException("Already configured");
      }
      this.serverTimeout = timeout;
    }
  }

  /**
   * Returns the socket timeout (in milliseconds).
   *
   * @return The socket timeout in milliseconds (0 = no timeout).
   */
  public int getSocketTimeout() {
    return socketTimeout;
  }

  /**
   * Sets the socket timeout (in milliseconds).
   *
   * @param timeout The new timeout in milliseconds (0 = no timeout).
   */
  public void setSocketTimeout(int timeout) {
    this.socketTimeout = timeout;
  }

  /**
   * Returns the server-busy timeout (in milliseconds).
   *
   * @return The server-busy timeout in milliseconds (0 = no timeout).
   */
  public int getServerBusyTimeout() {
    return serverBusyTimeout;
  }

  /**
   * Sets the server-busy timeout (in milliseconds).
   *
   * @param timeout The new timeout in milliseconds (0 = no timeout).
   */
  public void setServerBusyTimeout(int timeout) {
    this.serverBusyTimeout = timeout;
  }

  /**
   * Checks if the server is running.
   *
   * @return {@code true} if the server is alive.
   */
  public boolean isRunning() {
    synchronized (this) {
      return (listenThread != null && listenThread.isAlive());
    }
  }

  /**
   * Checks if the server is running and accepting new connections.
   *
   * @return {@code true} if the server is alive and ready to accept new connections.
   */
  public boolean isReady() {
    return ready.get() && !stopRequested.get() && isRunning();
  }

  /**
   * Starts the server, and returns immediately.
   *
   * @see #startAndWaitToBecomeReady(long, TimeUnit)
   */
  public void start() {
    synchronized (this) {
      if (isRunning()) {
        return;
      }
      if (connectionPool == null) {
        connectionPool = new ForkJoinPool(maxConcurrentConnections,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
      }

      Thread t = new Thread(SocketServer.this.toString() + " listening thread") {
        @Override
        public void run() {
          try {
            listen();
          } catch (Exception e) {
            onListenException(e);
          }
        }
      };
      t.start();

      listenThread = t;
    }
  }

  /**
   * Starts the server and waits until it is ready or had to shop due to an error.
   *
   * @param duration The duration wait.
   * @param unit The duration's time unit.
   * @return {@code true} if the server is ready to serve requests.
   * @throws InterruptedException If the wait was interrupted.
   */
  public boolean startAndWaitToBecomeReady(long duration, TimeUnit unit)
      throws InterruptedException {
    synchronized (this) {
      start();
      long timeStart = System.currentTimeMillis();
      while (duration > 0) {
        if (isReady()) {
          return true;
        }
        this.wait(unit.toMillis(duration));
        duration -= (System.currentTimeMillis() - timeStart);
      }
      return isReady();
    }
  }

  /**
   * Returns a new server socket.
   *
   * @return The new socket (an {@link AFServerSocket} if the listen address is an
   *         {@link AFSocketAddress}).
   * @throws IOException on error.
   */
  protected abstract V newServerSocket() throws IOException;

  @SuppressWarnings("null")
  private void listen() throws IOException {
    V server;

    synchronized (this) {
      if (reuseSocket != null) {
        serverSocket = reuseSocket;
      } else {
        if (serverSocket != null) {
          throw new IllegalStateException("The server is already listening");
        }
        serverSocket = newServerSocket();
      }
      server = serverSocket;
    }
    onServerStarting();

    try {
      if (!server.isBound()) {
        server.bind(listenAddress);
        onServerBound(listenAddress);
      }
      server.setSoTimeout(serverTimeout);

      acceptLoop(server);
    } catch (SocketException e) {
      onSocketExceptionDuringAccept(e);
    } finally {
      stop();
      onServerStopped(server);
    }
  }

  @SuppressWarnings("PMD.CognitiveComplexity")
  @SuppressFBWarnings("NN_NAKED_NOTIFY")
  private void acceptLoop(V server) throws IOException {
    long busyStartTime = 0;
    acceptLoop : while (!stopRequested.get() && !Thread.interrupted()) {
      try {
        while (!stopRequested.get() && connectionPool
            .getActiveThreadCount() >= maxConcurrentConnections) {
          if (busyStartTime == 0) {
            busyStartTime = System.currentTimeMillis();
          }
          onServerBusy(busyStartTime);

          synchronized (connectionsMonitor) {
            try {
              connectionsMonitor.wait(serverBusyTimeout);
            } catch (InterruptedException e) {
              throw (InterruptedIOException) new InterruptedIOException(
                  "Interrupted while waiting on server resources").initCause(e);
            }
          }
        }
        busyStartTime = 0;

        if (stopRequested.get() || server == null) {
          break;
        }

        synchronized (SocketServer.this) {
          SocketServer.this.notifyAll();
        }
        ready.set(true);
        onServerReady(connectionPool.getActiveThreadCount());

        final S socket;
        try {
          @SuppressWarnings("unchecked")
          S theSocket = (S) server.accept();
          socket = theSocket;
        } catch (SocketException e) {
          if (server.isClosed()) {
            // already closed, ignore
            break acceptLoop;
          } else {
            throw e;
          }
        }
        try {
          socket.setSoTimeout(socketTimeout);
        } catch (SocketException e) {
          // Connection closed before we could do anything
          onSocketExceptionAfterAccept(socket, e);
          socket.close();

          continue acceptLoop;
        }

        onSubmitted(socket, submit(socket, connectionPool));
      } catch (SocketTimeoutException e) {
        if (!connectionPool.isQuiescent()) {
          continue acceptLoop;
        } else {
          onServerShuttingDown();
          connectionPool.shutdown();
          break acceptLoop;
        }
      }
    }
  }

  /**
   * Stops the server.
   *
   * @throws IOException If there was an error.
   */
  @SuppressWarnings("null")
  @SuppressFBWarnings("NN_NAKED_NOTIFY")
  public void stop() throws IOException {
    stopRequested.set(true);
    ready.set(false);

    synchronized (this) {
      V theServerSocket = serverSocket;
      serverSocket = null;
      try {
        if (theServerSocket == null) {
          return;
        }
        ScheduledFuture<IOException> future = this.timeoutFuture;
        if (future != null) {
          future.cancel(false);
          this.timeoutFuture = null;
        }

        theServerSocket.close();
      } finally {
        SocketServer.this.notifyAll();
      }
    }
  }

  private Future<?> submit(final S socket, ExecutorService executor) {
    Objects.requireNonNull(socket);
    return executor.submit(new Runnable() {
      @Override
      public void run() {
        onBeforeServingSocket(socket);

        try { // NOPMD
          doServeSocket(socket);
        } catch (Exception e) {
          onServingException(socket, e);
        } finally {
          // Notify the server's accept thread that we handled the connection
          synchronized (connectionsMonitor) {
            connectionsMonitor.notifyAll();
          }
          try {
            socket.close();
          } catch (IOException e) {
            // ignore
          }
          onAfterServingSocket(socket);
        }
      }
    });
  }

  /**
   * Requests that the server will be stopped after the given time delay. If the server is not
   * started yet (and {@link #stop()} was not called yet, it will be started first.
   *
   * @param delay The delay.
   * @param unit The time unit for the delay.
   * @return A scheduled future that can be used to monitor progress / cancel the request. If there
   *         was a problem with stopping, an IOException is returned as the value (not thrown). If
   *         stop was already requested, {@code null} is returned.
   */
  public ScheduledFuture<IOException> startThenStopAfter(long delay, TimeUnit unit) {
    if (stopRequested.get()) {
      return null;
    }
    synchronized (this) {
      start();
      ScheduledFuture<?> existingFuture = this.timeoutFuture;
      if (existingFuture != null) {
        existingFuture.cancel(false);
      }

      return (this.timeoutFuture = TIMEOUTS.schedule(new Callable<IOException>() {
        @SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
        @Override
        public IOException call() throws Exception {
          try {
            stop();
            return null;
          } catch (IOException e) {
            return e;
          }
        }
      }, delay, unit));
    }
  }

  /**
   * Called when a socket is ready to be served.
   *
   * @param socket The socket to serve.
   * @throws IOException If there was an error.
   */
  protected abstract void doServeSocket(S socket) throws IOException;

  /**
   * Called when the server is starting up.
   */
  protected void onServerStarting() {
  }

  /**
   * Called when the server has been bound to a socket.
   *
   * This is not called when you instantiated the server with a pre-bound socket.
   *
   * @param address The bound address.
   */
  protected void onServerBound(A address) {
  }

  /**
   * Called when the server is ready to accept a new connection.
   *
   * @param activeCount The current number of active tasks (= serving sockets).
   */
  protected void onServerReady(int activeCount) {
  }

  /**
   * Called when the server is busy / not ready to accept a new connection.
   *
   * The frequency on how often this method is called when the server is busy is determined by
   * {@link #getServerBusyTimeout()}.
   *
   * @param busyStartTime The time stamp since the server became busy.
   */
  protected void onServerBusy(long busyStartTime) {
  }

  /**
   * Called when the server has been stopped.
   *
   * @param socket The server's socket that stopped.
   */
  protected void onServerStopped(V socket) {
  }

  /**
   * Called when a socket gets submitted into the process queue.
   *
   * @param socket The socket.
   * @param submission The {@link Future} referencing the submission; it's "done" after the socket
   *          has been served.
   */
  protected void onSubmitted(S socket, Future<?> submission) {
  }

  /**
   * Called when the server is shutting down.
   */
  protected void onServerShuttingDown() {
  }

  /**
   * Called when a {@link SocketException} was thrown during "accept".
   *
   * @param e The exception.
   */
  protected void onSocketExceptionDuringAccept(SocketException e) {
  }

  /**
   * Called when a {@link SocketException} was thrown during "accept".
   *
   * @param socket The socket.
   * @param e The exception.
   */
  protected void onSocketExceptionAfterAccept(S socket, SocketException e) {
  }

  /**
   * Called before serving the socket.
   *
   * @param socket The socket.
   */
  protected void onBeforeServingSocket(S socket) {
  }

  /**
   * Called when an exception was thrown while serving a socket.
   *
   * @param socket The socket.
   * @param e The exception.
   */
  protected void onServingException(S socket, Exception e) {
  }

  /**
   * Called after the socket has been served.
   *
   * @param socket The socket.
   */
  protected void onAfterServingSocket(S socket) {
  }

  /**
   * Called when an exception was thrown while listening on the server socket.
   *
   * @param e The exception.
   */
  protected void onListenException(Exception e) {
  }

  /**
   * Returns the address the server listens to.
   *
   * @return The listen address.
   */
  protected @NonNull A getListenAddress() {
    return listenAddress;
  }
}
