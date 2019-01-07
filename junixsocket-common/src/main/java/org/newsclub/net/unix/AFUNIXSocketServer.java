/**
 * junixsocket
 *
 * Copyright 2009-2018 Christian Kohlschütter
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AFUNIXSocketServer {
  private final AFUNIXSocketAddress listenAddress;

  private int maxConcurrentConnections = 3;
  private int serverTimeout = (int) TimeUnit.MINUTES.toMillis(5);
  private int socketTimeout = (int) TimeUnit.SECONDS.toMillis(60);
  private int serverFullTimeout = (int) TimeUnit.SECONDS.toMillis(1);

  private AFUNIXServerSocket serverSocket;

  private boolean stopRequested = false;

  private final Object connectionsMonitor = new Object();

  public AFUNIXSocketServer(AFUNIXSocketAddress listenAddress) {
    if (listenAddress == null) {
      throw new NullPointerException("listenAddress");
    }
    this.listenAddress = listenAddress;
  }

  public int getMaxConcurrentConnections() {
    return maxConcurrentConnections;
  }

  public void setMaxConcurrentConnections(int maxConcurrentConnections) {
    this.maxConcurrentConnections = maxConcurrentConnections;
  }

  public int getServerTimeout() {
    return serverTimeout;
  }

  public void setServerTimeout(int serverTimeout) {
    this.serverTimeout = serverTimeout;
  }

  public int getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(int socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public int getServerFullTimeout() {
    return serverFullTimeout;
  }

  public void setServerFullTimeout(int serverFullTimeout) {
    this.serverFullTimeout = serverFullTimeout;
  }

  protected void onBind(AFUNIXSocketAddress address) {
    System.out.println("Created server —— try to connect: nc -U " + address.getSocketFile());
    System.out.println("Set stty to send data immediately: stty -icanon && nc -U " + address
        .getSocketFile());
  }

  protected void onBusy() {
    System.out.println("Server is busy");
  }

  protected void onReady(int activeCount) {
    System.out.println("Active connections: " + activeCount
        + "; waiting for the next connection...");
  }

  protected void onSubmitted(Socket socket, Future<?> submit) {
    System.out.println("Accepted: " + socket);
  }

  protected void onBeginServingSocket(Socket socket) {
    System.out.println("Serving socket: " + socket);
  }

  protected void onShutdown() {
    System.out.println("Nothing going on for a long time, I better stop listening");
  }

  protected void onSocketExceptionDuringAccept(SocketException e) {
    e.printStackTrace();
  }

  protected void onSocketExceptionAfterAccept(Socket socket, SocketException e) {
    System.out.println("Closed (not executed): " + socket);
  }

  protected void onServingException(Socket socket, Exception e) {
    System.err.println("Exception thrown in " + socket);
    e.printStackTrace();
  }

  protected void onEndServingSocket(Socket socket) {
    System.out.println("Closed: " + socket);
  }

  protected void onListenException(IOException e) {
    e.printStackTrace();
  }

  protected void doServeSocket(Socket socket) throws IOException {
    int bufferSize = 8192;
    try {
      bufferSize = socket.getReceiveBufferSize();
    } catch (SocketException e1) {
      // ignore
    }

    byte[] buffer = new byte[bufferSize];

    try (InputStream is = socket.getInputStream(); //
        OutputStream os = socket.getOutputStream()) {
      int read;
      while ((read = is.read(buffer)) != -1) {
        os.write(buffer, 0, read);
      }
    }
  }

  public synchronized void stop() throws IOException {
    stopRequested = true;

    AFUNIXServerSocket theServerSocket = serverSocket;
    System.out.println("Close server " + theServerSocket);
    if (theServerSocket == null) {
      return;
    }

    theServerSocket.close();
    notifyAll();
  }

  public synchronized void start() throws IOException {

    Thread t = new Thread(AFUNIXSocketServer.this.toString() + " listening thread") {
      @Override
      public void run() {
        try {
          listen();
        } catch (IOException e) {
          onListenException(e);
        }
      }
    };
    t.start();
  }

  @SuppressWarnings("resource")
  public void listen() throws IOException {
    AFUNIXServerSocket server;
    synchronized (this) {
      if (serverSocket != null) {
        throw new IllegalStateException("The server is already listening");
      }
      serverSocket = AFUNIXServerSocket.newInstance();
      server = serverSocket;
    }

    try {
      ForkJoinPool connectionPool = new ForkJoinPool(maxConcurrentConnections,
          ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);

      server.bind(listenAddress);
      onBind(listenAddress);
      server.setSoTimeout(serverTimeout);

      acceptLoop : while (!stopRequested && !Thread.interrupted()) {
        try {
          while (!stopRequested && connectionPool
              .getActiveThreadCount() >= maxConcurrentConnections) {
            onBusy();

            synchronized (connectionsMonitor) {
              try {
                connectionsMonitor.wait(serverFullTimeout);
              } catch (InterruptedException e) {
                throw new InterruptedIOException("Interrupted while waiting on server resources");
              }
            }
          }
          if (stopRequested || server == null) {
            break;
          }

          onReady(connectionPool.getActiveThreadCount());

          AFUNIXSocket socket;
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

            continue acceptLoop;
          }

          onSubmitted(socket, submit(socket, server, connectionPool));
        } catch (SocketTimeoutException e) {
          if (!connectionPool.isQuiescent()) {
            continue acceptLoop;
          } else {
            onShutdown();
            connectionPool.shutdown();
            break acceptLoop;
          }
        }
      }
    } finally {
      synchronized (this) {
        AFUNIXServerSocket theServerSocket = server;
        if (theServerSocket != null) {
          theServerSocket.close();
        }
        server = null;
      }
    }
  }

  private Future<?> submit(Socket socket, ServerSocket server, ExecutorService connectionPool) {
    return connectionPool.submit(new Runnable() {
      @Override
      public void run() {
        onBeginServingSocket(socket);

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
          onEndServingSocket(socket);
        }
      }

    });
  }

  public AFUNIXServerSocket serverSocket() {
    return serverSocket;
  }
}
