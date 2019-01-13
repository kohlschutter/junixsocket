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

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Java-part of the {@link AFUNIXSocket} implementation.
 * 
 * @author Christian Kohlschütter
 */
class AFUNIXSocketImpl extends SocketImpl {
  private static final int SHUT_RD = 0;
  private static final int SHUT_WR = 1;
  private static final int SHUT_RD_WR = 2;

  private String socketFile;

  /**
   * We keep track of the server's inode to detect when another server connects to our address.
   */
  private long inode = -1;
  private volatile boolean closed = false;
  private volatile boolean bound = false;
  private boolean connected = false;

  private volatile boolean closedInputStream = false;
  private volatile boolean closedOutputStream = false;

  private final AFUNIXInputStream in = new AFUNIXInputStream();
  private final AFUNIXOutputStream out = new AFUNIXOutputStream();

  private final AtomicInteger pendingAccepts = new AtomicInteger(0);

  private boolean reuseAddr = true;

  protected AFUNIXSocketImpl() {
    super();
    this.fd = new FileDescriptor();
  }

  FileDescriptor getFD() {
    return fd;
  }

  // NOTE: This prevents a file descriptor leak
  // see conversation in https://github.com/kohlschutter/junixsocket/pull/29
  @SuppressWarnings("all")
  @Override
  protected final void finalize() {
    try {
      // prevent file descriptor leakage
      close();
    } catch (Throwable t) {
      // nothing that can be done here
    }
  }

  @Override
  protected void accept(SocketImpl socket) throws IOException {
    FileDescriptor fdesc = validFdOrException();

    final AFUNIXSocketImpl si = (AFUNIXSocketImpl) socket;
    try {
      if (pendingAccepts.incrementAndGet() >= Integer.MAX_VALUE) {
        throw new SocketException("Too many pending accepts");
      } else {
        if (!bound || closed) {
          throw new SocketException("Socket is closed");
        }

        NativeUnixSocket.accept(socketFile, fdesc, si.fd, inode);
        if (!bound || closed) {
          try {
            NativeUnixSocket.shutdown(si.fd, SHUT_RD_WR);
          } catch (Exception e) {
            // ignore
          }
          try {
            NativeUnixSocket.close(si.fd);
          } catch (Exception e) {
            // ignore
          }
          throw new SocketException("Socket is closed");
        }
      }
    } finally {
      pendingAccepts.decrementAndGet();
    }
    si.socketFile = socketFile;
    si.connected = true;
  }

  @Override
  protected int available() throws IOException {
    FileDescriptor fdesc = validFdOrException();
    return NativeUnixSocket.available(fdesc);
  }

  protected void bind(SocketAddress addr) throws IOException {
    bind(addr, -1);
  }

  protected void bind(SocketAddress addr, int options) throws IOException {
    if (!(addr instanceof AFUNIXSocketAddress)) {
      throw new SocketException("Cannot bind to this type of address: " + addr.getClass());
    }

    final AFUNIXSocketAddress socketAddress = (AFUNIXSocketAddress) addr;
    socketFile = socketAddress.getSocketFile();

    this.inode = NativeUnixSocket.bind(socketFile, fd, options);
    validFdOrException();
    bound = true;
    this.localport = socketAddress.getPort();
  }

  @Override
  @SuppressWarnings("hiding")
  protected void bind(InetAddress host, int port) throws IOException {
    throw new SocketException("Cannot bind to this type of address: " + InetAddress.class);
  }

  private void checkClose() throws IOException {
    if (closedInputStream && closedOutputStream) {
      // close();
    }
  }

  /**
   * Unblock other threads that are currently waiting on accept, simply by connecting to the socket.
   */
  private void unblockAccepts() {
    while (pendingAccepts.get() > 0) {
      try {
        FileDescriptor tmpFd = new FileDescriptor();

        try {
          NativeUnixSocket.connect(socketFile, tmpFd, inode);
        } catch (IOException e) {
          // there's nothing we can do to unlock these accepts
          return;
        }
        try {
          NativeUnixSocket.shutdown(tmpFd, SHUT_RD_WR);
        } catch (Exception e) {
          // ignore
        }
        try {
          NativeUnixSocket.close(tmpFd);
        } catch (Exception e) {
          // ignore
        }
      } catch (Exception e) {
        // ignore
      }
    }
  }

  @Override
  protected final synchronized void close() throws IOException {
    boolean wasBound = bound;
    bound = false;

    FileDescriptor fdesc = validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_RD_WR);

      closed = true;
      if (wasBound && socketFile != null && inode >= 0) {
        unblockAccepts();
      }

      NativeUnixSocket.close(fdesc);
    }
    closed = true;
  }

  @Override
  @SuppressWarnings("hiding")
  protected void connect(String host, int port) throws IOException {
    throw new SocketException("Cannot bind to this type of address: " + InetAddress.class);
  }

  @Override
  @SuppressWarnings("hiding")
  protected void connect(InetAddress address, int port) throws IOException {
    throw new SocketException("Cannot bind to this type of address: " + InetAddress.class);
  }

  @Override
  protected void connect(SocketAddress addr, int timeout) throws IOException {
    if (!(addr instanceof AFUNIXSocketAddress)) {
      throw new SocketException("Cannot bind to this type of address: " + addr.getClass());
    }
    final AFUNIXSocketAddress socketAddress = (AFUNIXSocketAddress) addr;
    socketFile = socketAddress.getSocketFile();
    NativeUnixSocket.connect(validateSocketFile(socketFile), fd, -1);
    validFdOrException();
    this.address = socketAddress.getAddress();
    this.port = socketAddress.getPort();
    this.localport = 0;
    this.connected = true;
  }

  private String validateSocketFile(String file) throws SocketException {
    if (file.isEmpty() || !new File(file).exists()) {
      throw new SocketException("Socket file not found: " + socketFile);
    }
    return file;
  }

  @Override
  protected void create(boolean stream) throws IOException {
  }

  @Override
  protected InputStream getInputStream() throws IOException {
    if (!connected && !bound) {
      throw new IOException("Not connected/not bound");
    }
    validFdOrException();
    return in;
  }

  @Override
  protected OutputStream getOutputStream() throws IOException {
    if (!connected && !bound) {
      throw new IOException("Not connected/not bound");
    }
    validFdOrException();
    return out;
  }

  @Override
  protected void listen(int backlog) throws IOException {
    FileDescriptor fdesc = validFdOrException();
    if (backlog < 1) {
      backlog = 50;
    }
    NativeUnixSocket.listen(fdesc, backlog);
  }

  @Override
  protected void sendUrgentData(int data) throws IOException {
    FileDescriptor fdesc = validFdOrException();
    NativeUnixSocket.write(fdesc, new byte[] {(byte) (data & 0xFF)}, 0, 1);
  }

  private final class AFUNIXInputStream extends InputStream {
    private volatile boolean streamClosed = false;

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      if (streamClosed) {
        throw new IOException("This InputStream has already been closed.");
      }
      FileDescriptor fdesc = validFdOrException();
      if (len == 0) {
        return 0;
      } else if (off < 0 || len < 0 || (len > buf.length - off)) {
        throw new IndexOutOfBoundsException();
      }

      return NativeUnixSocket.read(fdesc, buf, off, len);
    }

    @Override
    public int read() throws IOException {
      final byte[] buf1 = new byte[1];
      final int numRead = read(buf1, 0, 1);
      if (numRead <= 0) {
        return -1;
      } else {
        return buf1[0] & 0xFF;
      }
    }

    @Override
    public synchronized void close() throws IOException {
      streamClosed = true;
      FileDescriptor fdesc = validFd();
      if (fdesc != null) {
        NativeUnixSocket.shutdown(fdesc, SHUT_RD);
      }

      closedInputStream = true;
      checkClose();
    }

    @Override
    public int available() throws IOException {
      if (streamClosed) {
        throw new IOException("This InputStream has already been closed.");
      }
      FileDescriptor fdesc = validFdOrException();

      return NativeUnixSocket.available(fdesc);
    }
  }

  private final class AFUNIXOutputStream extends OutputStream {
    private volatile boolean streamClosed = false;

    @Override
    public void write(int oneByte) throws IOException {
      final byte[] buf1 = new byte[] {(byte) oneByte};
      write(buf1, 0, 1);
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
      if (streamClosed) {
        throw new SocketException("This OutputStream has already been closed.");
      }
      if (len < 0 || off < 0 || len > buf.length - off) {
        throw new IndexOutOfBoundsException();
      }
      FileDescriptor fdesc = validFdOrException();
      int writtenTotal = 0;

      while (len > 0) {
        if (Thread.interrupted()) {
          InterruptedIOException ex = new InterruptedIOException("Thread interrupted during write");
          ex.bytesTransferred = writtenTotal;
          Thread.currentThread().interrupt();
          throw ex;
        }
        final int written = NativeUnixSocket.write(fdesc, buf, off, len);
        if (written < 0) {
          throw new IOException("Unspecific error while writing");
        }
        len -= written;
        off += written;
        writtenTotal += written;
      }
    }

    @Override
    public synchronized void close() throws IOException {
      if (streamClosed) {
        return;
      }
      streamClosed = true;
      FileDescriptor fdesc = validFd();
      if (fdesc != null) {
        NativeUnixSocket.shutdown(fdesc, SHUT_WR);
      }
      closedOutputStream = true;
      checkClose();
    }
  }

  private FileDescriptor validFdOrException() throws SocketException {
    FileDescriptor fdesc = validFd();
    if (fdesc == null) {
      throw new SocketException("Not open");
    }
    return fdesc;
  }

  private synchronized FileDescriptor validFd() {
    if (closed) {
      return null;
    }
    FileDescriptor descriptor = this.fd;
    if (descriptor != null) {
      if (descriptor.valid()) {
        return descriptor;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return super.toString() + "[fd=" + fd + "; file=" + this.socketFile + "; connected=" + connected
        + "; bound=" + bound + "]";
  }

  private static int expectInteger(Object value) throws SocketException {
    try {
      return (Integer) value;
    } catch (final ClassCastException e) {
      throw (SocketException) new SocketException("Unsupported value: " + value).initCause(e);
    } catch (final NullPointerException e) {
      throw (SocketException) new SocketException("Value must not be null").initCause(e);
    }
  }

  private static int expectBoolean(Object value) throws SocketException {
    try {
      return ((Boolean) value).booleanValue() ? 1 : 0;
    } catch (final ClassCastException e) {
      throw (SocketException) new SocketException("Unsupported value: " + value).initCause(e);
    } catch (final NullPointerException e) {
      throw (SocketException) new SocketException("Value must not be null").initCause(e);
    }
  }

  @Override
  public Object getOption(int optID) throws SocketException {
    if (optID == SocketOptions.SO_REUSEADDR) {
      return reuseAddr;
    }

    FileDescriptor fdesc = validFdOrException();
    try {
      switch (optID) {
        case SocketOptions.SO_KEEPALIVE:
        case SocketOptions.TCP_NODELAY:
          return NativeUnixSocket.getSocketOptionInt(fdesc, optID) != 0 ? true : false;
        case SocketOptions.SO_LINGER:
        case SocketOptions.SO_TIMEOUT:
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
          return NativeUnixSocket.getSocketOptionInt(fdesc, optID);
        default:
          throw new SocketException("Unsupported option: " + optID);
      }
    } catch (final SocketException e) {
      throw e;
    } catch (final Exception e) {
      throw (SocketException) new SocketException("Error while getting option").initCause(e);
    }
  }

  @Override
  public void setOption(int optID, Object value) throws SocketException {
    if (optID == SocketOptions.SO_REUSEADDR) {
      reuseAddr = expectBoolean(value) == 0 ? false : true;
      return;
    }

    FileDescriptor fdesc = validFdOrException();
    try {
      switch (optID) {
        case SocketOptions.SO_LINGER:

          if (value instanceof Boolean) {
            final boolean b = (Boolean) value;
            if (b) {
              throw new SocketException("Only accepting Boolean.FALSE here");
            }
            NativeUnixSocket.setSocketOptionInt(fdesc, optID, -1);
            return;
          }
          NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectInteger(value));
          return;
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
        case SocketOptions.SO_TIMEOUT:
          NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectInteger(value));
          return;
        case SocketOptions.SO_KEEPALIVE:
        case SocketOptions.TCP_NODELAY:
          NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectBoolean(value));
          return;
        default:
          throw new SocketException("Unsupported option: " + optID);
      }
    } catch (final SocketException e) {
      throw e;
    } catch (final Exception e) {
      throw (SocketException) new SocketException("Error while setting option").initCause(e);
    }
  }

  @Override
  protected void shutdownInput() throws IOException {
    FileDescriptor fdesc = validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_RD);
    }
  }

  @Override
  protected void shutdownOutput() throws IOException {
    FileDescriptor fdesc = validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_WR);
    }
  }

  /**
   * Changes the behavior to be somewhat lenient with respect to the specification.
   * 
   * In particular, we ignore calls to {@link Socket#getTcpNoDelay()} and
   * {@link Socket#setTcpNoDelay(boolean)}.
   */
  static final class Lenient extends AFUNIXSocketImpl {
    Lenient() {
      super();
    }

    @Override
    public void setOption(int optID, Object value) throws SocketException {
      try {
        super.setOption(optID, value);
      } catch (SocketException e) {
        switch (optID) {
          case SocketOptions.TCP_NODELAY:
            return;
          default:
            throw e;
        }
      }
    }

    @Override
    public Object getOption(int optID) throws SocketException {
      try {
        return super.getOption(optID);
      } catch (SocketException e) {
        switch (optID) {
          case SocketOptions.TCP_NODELAY:
          case SocketOptions.SO_KEEPALIVE:
            return false;
          default:
            throw e;
        }
      }
    }
  }
}
