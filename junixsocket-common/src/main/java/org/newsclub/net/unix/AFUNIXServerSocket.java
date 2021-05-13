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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The server part of an AF_UNIX domain socket.
 * 
 * @author Christian Kohlschütter
 */
public class AFUNIXServerSocket extends ServerSocket {
  private final AFUNIXSocketImpl implementation;
  private AFUNIXSocketAddress boundEndpoint;
  private final Closeables closeables = new Closeables();
  private final AtomicBoolean created = new AtomicBoolean(false);
  private final AtomicBoolean deleteOnClose = new AtomicBoolean(true);

  /**
   * Constructs a new, unconnected instance.
   * 
   * @throws IOException if the operation fails.
   */
  protected AFUNIXServerSocket() throws IOException {
    super();
    this.implementation = new AFUNIXSocketImpl();
    NativeUnixSocket.initServerImpl(this, implementation);

    setReuseAddress(true);
  }

  /**
   * Returns a new, unbound AF_UNIX {@link ServerSocket}.
   * 
   * @return The new, unbound {@link AFUNIXServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket newInstance() throws IOException {
    return new AFUNIXServerSocket();
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given
   * {@link AFUNIXSocketAddress}.
   * 
   * @param addr The socket file to bind to.
   * @return The new, bound {@link AFUNIXServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final AFUNIXSocketAddress addr) throws IOException {
    AFUNIXServerSocket socket = newInstance();
    socket.bind(addr);
    return socket;
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given path.
   * 
   * @param path The path to bind to.
   * @param deleteOnClose If {@code true}, the socket file will be deleted upon {@link #close}.
   * @return The new, bound {@link AFUNIXServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final File path, boolean deleteOnClose)
      throws IOException {
    return bindOn(path.toPath(), deleteOnClose);
  }

  /**
   * Returns a new AF_UNIX {@link ServerSocket} that is bound to the given path.
   * 
   * @param path The path to bind to.
   * @param deleteOnClose If {@code true}, the socket file will be deleted upon {@link #close}.
   * @return The new, bound {@link AFUNIXServerSocket}.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXServerSocket bindOn(final Path path, boolean deleteOnClose)
      throws IOException {
    AFUNIXServerSocket socket = newInstance();
    socket.setDeleteOnClose(true);
    socket.bind(new AFUNIXSocketAddress(path));
    return socket;
  }

  /**
   * Returns a new, <em>unbound</em> AF_UNIX {@link ServerSocket} that will always bind to the given
   * address, regardless of any socket address used in a call to <code>bind</code>.
   * 
   * @param forceAddr The address to use.
   * @return The new, yet unbound {@link AFUNIXServerSocket}.
   * @throws IOException if an exception occurs.
   */
  public static AFUNIXServerSocket forceBindOn(final AFUNIXSocketAddress forceAddr)
      throws IOException {
    return new AFUNIXServerSocket() {

      @Override
      public void bind(SocketAddress ignored, int backlog) throws IOException {
        super.bind(forceAddr, backlog);
      }
    };
  }

  @Override
  public void bind(SocketAddress endpoint, int backlog) throws IOException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (isBound()) {
      throw new SocketException("Already bound");
    }

    if (!(endpoint instanceof AFUNIXSocketAddress)) {
      throw new IllegalArgumentException("Can only bind to endpoints of type "
          + AFUNIXSocketAddress.class.getName());
    }

    getAFImpl().bind(endpoint, getReuseAddress() ? -1 : 0);
    boundEndpoint = (AFUNIXSocketAddress) endpoint;

    implementation.listen(backlog);
  }

  @Override
  public boolean isBound() {
    return boundEndpoint != null;
  }

  @Override
  public boolean isClosed() {
    return super.isClosed() || (isBound() && !implementation.getFD().valid());
  }

  @Override
  public AFUNIXSocket accept() throws IOException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    AFUNIXSocket as = newSocketInstance();
    implementation.accept(as.getAFImpl());
    as.addr = boundEndpoint;
    NativeUnixSocket.setConnected(as);
    return as;
  }

  protected AFUNIXSocket newSocketInstance() throws IOException {
    return AFUNIXSocket.newInstance();
  }

  @Override
  public String toString() {
    return "AFUNIXServerSocket[" + (isBound() ? boundEndpoint.toString() : "unbound") + "]";
  }

  @Override
  public synchronized void close() throws IOException {
    if (isClosed()) {
      return;
    }

    AFUNIXSocketAddress endpoint = boundEndpoint;

    IOException superException = null;
    try {
      super.close();
    } catch (IOException e) {
      superException = e;
    }
    if (implementation != null) {
      try {
        implementation.close();
      } catch (IOException e) {
        if (superException == null) {
          superException = e;
        } else {
          superException.addSuppressed(e);
        }
      }
    }

    IOException ex = null;
    try {
      closeables.close(superException);
    } finally {
      if (endpoint != null && endpoint.hasFilename() && isDeleteOnClose()) {
        File f = endpoint.getFile();
        if (!f.delete() && f.exists()) {
          ex = new IOException("Could not delete socket file after close: " + f);
        }
      }
    }
    if (ex != null) {
      throw ex;
    }
  }

  /**
   * Registers a {@link Closeable} that should be closed when this socket is closed.
   * 
   * @param closeable The closeable.
   */
  public void addCloseable(Closeable closeable) {
    closeables.add(closeable);
  }

  /**
   * Unregisters a previously registered {@link Closeable}.
   * 
   * @param closeable The closeable.
   */
  public void removeCloseable(Closeable closeable) {
    closeables.remove(closeable);
  }

  /**
   * Checks whether everything is setup to support AF_UNIX sockets.
   * 
   * @return {@code true} if supported.
   */
  public static boolean isSupported() {
    return NativeUnixSocket.isLoaded();
  }

  @Override
  public AFUNIXSocketAddress getLocalSocketAddress() {
    return boundEndpoint;
  }

  @Override
  public int getLocalPort() {
    if (boundEndpoint == null) {
      return -1;
    }
    return boundEndpoint.getPort();
  }

  /**
   * Checks if this {@link AFUNIXServerSocket}'s file should be removed upon {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @return {@code true} if an attempt is made to delete the socket file upon {@link #close()}.
   */
  public boolean isDeleteOnClose() {
    return deleteOnClose.get();
  }

  /**
   * Enables/disables deleting this {@link AFUNIXServerSocket}'s file upon {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @param b Enabled if {@code true}.
   */
  public void setDeleteOnClose(boolean b) {
    deleteOnClose.set(b);
  }

  AFUNIXSocketImpl getAFImpl() {
    if (created.compareAndSet(false, true)) {
      try {
        getSoTimeout(); // trigger create via java.net.Socket
      } catch (IOException e) {
        // ignore
      }
    }
    return implementation;
  }
}
