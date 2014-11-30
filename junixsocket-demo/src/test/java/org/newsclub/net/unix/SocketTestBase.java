/**
 * junixsocket
 *
 * Copyright (c) 2009,2014 Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Some base functionality for socket tests.
 * 
 * @author Christian Kohlschuetter
 */
abstract class SocketTestBase {
  private final AFUNIXSocketAddress serverAddress;

  public SocketTestBase() throws IOException {
    this.serverAddress = new AFUNIXSocketAddress(getSocketFile());
  }

  protected File getSocketFile() throws IOException {
    String explicitFile = System.getProperty("org.newsclub.net.unix.testsocket");
    if (explicitFile != null) {
      return new File(explicitFile);
    } else {
      return new File(new File(System.getProperty("java.io.tmpdir")), "junixsocket-test.sock");
    }
  }

  protected AFUNIXServerSocket startServer() throws IOException {
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

  protected abstract class ServerThread extends Thread {
    private final AFUNIXServerSocket serverSocket;
    private Exception exception = null;
    private volatile boolean loop = true;

    protected ServerThread() throws IOException {
      serverSocket = startServer();
      setDaemon(true);
      start();
    }

    /**
     * Callback used to handle a connection call.
     * 
     * Use {@link #stopAcceptingConnections()} to stop accepting new calls.
     * 
     * @param sock The socket to handle.
     */
    protected abstract void handleConnection(final Socket sock) throws IOException;

    /**
     * Called from within {@link #handleConnection(Socket)} to tell the server to no longer accept
     * new calls and to terminate the server thread.
     */
    protected void stopAcceptingConnections() {
      loop = false;
    }

    protected void onServerSocketClose() {
    }

    public ServerSocket getServerSocket() {
      return serverSocket;
    }

    @Override
    public final void run() {
      try {
        loop = true;
        try {
          while (loop) {
            try (Socket sock = serverSocket.accept()) {
              handleConnection(sock);
            }
          }
        } finally {
          onServerSocketClose();
          serverSocket.close();
        }
      } catch (IOException e) {
        exception = e;
      }
    }

    public void checkException() throws Exception {
      if (exception != null) {
        throw exception;
      }
    }
  }

  protected void sleepFor(final int ms) throws IOException {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }
}
