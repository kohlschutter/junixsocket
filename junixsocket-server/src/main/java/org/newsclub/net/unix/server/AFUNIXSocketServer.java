/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * A base implementation for a simple, multi-threaded socket server.
 * 
 * This class supports both unix and "regular" sockets.
 * 
 * @author Christian Kohlschütter
 */
public abstract class AFUNIXSocketServer {
  private final SocketAddress listenAddress;

  private int maxConcurrentConnections = Runtime.getRuntime().availableProcessors();
  private int serverTimeout = 0; // by default, the server doesn't timeout.
  private int socketTimeout = (int) TimeUnit.SECONDS.toMillis(60);
  private int serverBusyTimeout = (int) TimeUnit.SECONDS.toMillis(1);

  private Thread listenThread = null;
  private ServerSocket serverSocket;
  private boolean stopRequested = false;

  private final Object connectionsMonitor = new Object();
  private ForkJoinPool connectionPool;

  public AFUNIXSocketServer(SocketAddress listenAddress) {
    Objects.requireNonNull(listenAddress, "listenAddress");

    this.listenAddress = listenAddress;
  }

  public int getMaxConcurrentConnections() {
    return maxConcurrentConnections;
  }

  public void setMaxConcurrentConnections(int maxConcurrentConnections) {
    if (connectionPool != null) {
      throw new IllegalStateException("Already configured");
    }
    this.maxConcurrentConnections = maxConcurrentConnections;
  }

  public int getServerTimeout() {
    return serverTimeout;
  }

  public void setServerTimeout(int serverTimeout) {
    synchronized (this) {
      if (serverSocket != null) {
        throw new IllegalStateException("Already configured");
      }
      this.serverTimeout = serverTimeout;
    }
  }

  public int getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(int socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public int getServerBusyTimeout() {
    return serverBusyTimeout;
  }

  public void setServerBusyTimeout(int serverFullTimeout) {
    this.serverBusyTimeout = serverFullTimeout;
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
   * Starts the server.
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

      Thread t = new Thread(AFUNIXSocketServer.this.toString() + " listening thread") {
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

  protected ServerSocket newServerSocket() throws IOException {
    if (listenAddress instanceof AFUNIXSocketAddress) {
      return AFUNIXServerSocket.newInstance();
    } else {
      return new ServerSocket();
    }
  }

  @SuppressWarnings("resource")
  private void listen() throws IOException {
    ServerSocket server;
    synchronized (this) {
      if (serverSocket != null) {
        throw new IllegalStateException("The server is already listening");
      }
      serverSocket = newServerSocket();
      server = serverSocket;
    }
    onServerStarting();

    try {
      server.bind(listenAddress);
      onServerBound(listenAddress);
      server.setSoTimeout(serverTimeout);

      long busyStartTime = 0;
      acceptLoop : while (!stopRequested && !Thread.interrupted()) {
        try {
          while (!stopRequested && connectionPool
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

          if (stopRequested || server == null) {
            break;
          }

          onServerReady(connectionPool.getActiveThreadCount());

          Socket socket;
          try {
            socket = server.accept();
          } catch (SocketException e) {
            if (server.isClosed()) {
              // already closed, ignore
              break acceptLoop;
            } else {
              onSocketExceptionDuringAccept(e);
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
    } finally {
      stop();
      onServerStopped(server);
    }
  }

  /**
   * Stops the server.
   * 
   * @throws IOException If there was an error.
   */
  public void stop() throws IOException {
    synchronized (this) {
      stopRequested = true;

      ServerSocket theServerSocket = serverSocket;
      serverSocket = null;
      if (theServerSocket == null) {
        return;
      }
      theServerSocket.close();
    }
  }

  private Future<?> submit(Socket socket, ExecutorService executor) {
    return executor.submit(new Runnable() {
      @Override
      public void run() {
        onBeforeServingSocket(socket);

        try {
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
   * Called when a socket is ready to be served.
   * 
   * @param socket The socket to serve.
   * @throws IOException If there was an error.
   */
  protected abstract void doServeSocket(Socket socket) throws IOException;

  /**
   * Called when the server is starting up.
   */
  protected void onServerStarting() {
  }

  /**
   * Called when the server has been bound to a socket.
   * 
   * @param address The bound address.
   */
  protected void onServerBound(SocketAddress address) {
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
  protected void onServerStopped(ServerSocket socket) {
  }

  /**
   * Called when a socket gets submitted into the process queue.
   * 
   * @param socket The socket.
   * @param submission The {@link Future} referencing the submission; it's "done" after the socket
   *          has been served.
   */
  protected void onSubmitted(Socket socket, Future<?> submission) {
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
  protected void onSocketExceptionAfterAccept(Socket socket, SocketException e) {
  }

  /**
   * Called before serving the socket.
   * 
   * @param socket The socket.
   */
  protected void onBeforeServingSocket(Socket socket) {
  }

  /**
   * Called when an exception was thrown while serving a socket.
   * 
   * @param socket The socket.
   * @param e The exception.
   */
  protected void onServingException(Socket socket, Exception e) {
  }

  /**
   * Called after the socket has been served.
   * 
   * @param socket The socket.
   */
  protected void onAfterServingSocket(Socket socket) {
  }

  /**
   * Called when an exception was thrown while listening on the server socket.
   * 
   * @param e The exception.
   */
  protected void onListenException(Exception e) {
  }
}
