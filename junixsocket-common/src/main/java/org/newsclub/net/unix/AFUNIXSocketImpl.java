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
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Java-part of the {@link AFUNIXSocket} implementation.
 * 
 * @author Christian Kohlschütter
 */
@SuppressWarnings({"PMD.CyclomaticComplexity"})
class AFUNIXSocketImpl extends SocketImplShim {
  private static final int SHUT_RD = 0;
  private static final int SHUT_WR = 1;
  private static final int SHUT_RD_WR = 2;

  private final AFUNIXSocketStreamCore core;
  final AncillaryDataSupport ancillaryDataSupport = new AncillaryDataSupport();

  private final AtomicBoolean bound = new AtomicBoolean(false);
  private Boolean createType = null;
  private final AtomicBoolean connected = new AtomicBoolean(false);

  private volatile boolean closedInputStream = false;
  private volatile boolean closedOutputStream = false;

  private final AFUNIXInputStream in = newInputStream();
  private final AFUNIXOutputStream out = newOutputStream();

  private boolean reuseAddr = true;

  private final AtomicInteger socketTimeout = new AtomicInteger(0);

  /**
   * When the {@link AFUNIXSocketImpl} becomes unreachable (but not yet closed), we must ensure that
   * the underlying socket and all related file descriptors are closed.
   *
   * @author Christian Kohlschütter
   */
  private static class AFUNIXSocketStreamCore extends AFUNIXSocketCore {
    private final AtomicInteger pendingAccepts = new AtomicInteger(0);

    private AFUNIXSocketStreamCore(AFUNIXSocketImpl observed, FileDescriptor fd,
        AncillaryDataSupport ancillaryDataSupport) {
      super(observed, fd, ancillaryDataSupport);
    }

    private void incPendingAccepts() throws SocketException {
      if (pendingAccepts.incrementAndGet() >= Integer.MAX_VALUE) {
        throw new SocketException("Too many pending accepts");
      }
    }

    private void decPendingAccepts() throws SocketException {
      pendingAccepts.decrementAndGet();
    }

    /**
     * Unblock other threads that are currently waiting on accept, simply by connecting to the
     * socket.
     */
    @Override
    protected void unblockAccepts() {
      if (socketAddress == null || socketAddress.getBytes() == null || inode.get() < 0) {
        return;
      }

      while (pendingAccepts.get() > 0) {
        try {
          FileDescriptor tmpFd = new FileDescriptor();

          try {
            NativeUnixSocket.createSocket(tmpFd, NativeUnixSocket.SOCK_STREAM);
            NativeUnixSocket.connect(socketAddress.getBytes(), tmpFd, inode.get());
          } catch (IOException e) {
            // there's nothing more we can do to unlock these accepts
            // (e.g., SocketException: No such file or directory)
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
  }

  protected AFUNIXSocketImpl() throws SocketException {
    this((FileDescriptor) null);
  }

  protected AFUNIXSocketImpl(FileDescriptor fdObj) throws SocketException {
    super();
    this.address = InetAddress.getLoopbackAddress();
    this.core = new AFUNIXSocketStreamCore(this, fdObj, ancillaryDataSupport);
    this.fd = core.fd;
  }

  protected AFUNIXInputStream newInputStream() {
    return new AFUNIXInputStream();
  }

  protected AFUNIXOutputStream newOutputStream() {
    return new AFUNIXOutputStream();
  }

  FileDescriptor getFD() {
    return fd;
  }

  boolean isConnected() {
    if (connected.get()) {
      return true;
    }
    if (isClosed()) {
      return false;
    }
    if (core.isConnected(false)) {
      connected.set(true);
      return true;
    }
    return false;
  }

  boolean isBound() {
    if (bound.get()) {
      return true;
    }
    if (isClosed()) {
      return false;
    }
    if (core.isConnected(true)) {
      bound.set(true);
      return true;
    }
    return false;
  }

  AFUNIXSocketCore getCore() {
    return core;
  }

  private boolean isClosed() {
    return core.isClosed();
  }

  @Override
  protected void accept(SocketImpl socket) throws IOException {
    accept0(socket);
  }

  protected boolean accept0(SocketImpl socket) throws IOException {
    FileDescriptor fdesc = core.validFdOrException();
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    } else if (!isBound()) {
      throw new SocketException("Socket is not bound");
    }

    AFUNIXSocketAddress socketAddress = core.socketAddress;

    final AFUNIXSocketImpl si = (AFUNIXSocketImpl) socket;
    try {
      core.incPendingAccepts();
      if (!NativeUnixSocket.accept(socketAddress.getBytes(), fdesc, si.fd, core.inode.get(),
          socketTimeout.get())) {
        return false;
      }

      if (!isBound() || isClosed()) {
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
    } finally {
      core.decPendingAccepts();
    }
    si.setSocketAddress(socketAddress);
    si.connected.set(true);

    return true;
  }

  void setSocketAddress(AFUNIXSocketAddress socketAddress) {
    if (socketAddress == null) {
      this.core.socketAddress = null;
      this.address = null;
      this.localport = -1;
    } else {
      this.core.socketAddress = socketAddress;
      this.address = socketAddress.getAddress();
      if (this.localport <= 0) {
        this.localport = socketAddress.getPort();
      }
    }
  }

  @Override
  protected int available() throws IOException {
    FileDescriptor fdesc = core.validFdOrException();
    return NativeUnixSocket.available(fdesc);
  }

  protected void bind(SocketAddress addr, int options) throws IOException {
    if (addr == null) {
      throw new IllegalArgumentException("Cannot bind to null address");
    }
    if (!(addr instanceof AFUNIXSocketAddress)) {
      throw new SocketException("Cannot bind to this type of address: " + addr.getClass());
    }

    bound.set(true);

    if (addr == AFUNIXSocketAddress.INTERNAL_DUMMY_BIND) { // NOPMD
      core.inode.set(0);
      return;
    }

    AFUNIXSocketAddress socketAddress = (AFUNIXSocketAddress) addr;

    this.setSocketAddress(socketAddress);
    core.inode.set(NativeUnixSocket.bind(socketAddress.getBytes(), fd, options));
    core.validFdOrException();
  }

  @Override
  @SuppressWarnings("hiding")
  protected void bind(InetAddress host, int port) throws IOException {
    // ignored
  }

  private void checkClose() throws IOException {
    if (closedInputStream && closedOutputStream) {
      close();
    }
  }

  @Override
  protected final void close() throws IOException {
    core.runCleaner();
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
  protected void connect(SocketAddress addr, int connectTimeout) throws IOException {
    connect0(addr, connectTimeout);
  }

  boolean connect0(SocketAddress addr, int connectTimeout) throws IOException {
    if (!(addr instanceof AFUNIXSocketAddress)) {
      throw new SocketException("Cannot bind to this type of address: " + addr.getClass());
    }
    if (addr == AFUNIXSocketAddress.INTERNAL_DUMMY_CONNECT) { // NOPMD
      this.connected.set(true);
      return true;
    } else if (addr == AFUNIXSocketAddress.INTERNAL_DUMMY_CONNECT) { // NOPMD)
      return false;
    }
    AFUNIXSocketAddress socketAddress = (AFUNIXSocketAddress) addr;
    boolean success = NativeUnixSocket.connect(socketAddress.getBytes(), fd, -1);
    if (success) {
      setSocketAddress(socketAddress);
      this.connected.set(true);
    }
    core.validFdOrException();
    return success;
  }

  @Override
  protected void create(boolean stream) throws IOException {
    if (isClosed()) {
      throw new SocketException("Already closed");
    }
    if (fd.valid()) {
      if (createType != null) {
        if (createType.booleanValue() != stream) {
          throw new IllegalStateException("Already created with different mode");
        }
      } else {
        createType = stream;
      }
      return;
    }
    createType = stream;
    NativeUnixSocket.createSocket(fd, stream ? NativeUnixSocket.SOCK_STREAM
        : NativeUnixSocket.SOCK_DGRAM);
  }

  @Override
  protected InputStream getInputStream() throws IOException {
    if (!isConnected() && !isBound()) {
      throw new IOException("Not connected/not bound");
    }
    core.validFdOrException();
    return in;
  }

  @Override
  protected OutputStream getOutputStream() throws IOException {
    if (!isClosed() && !isBound()) {
      throw new IOException("Not connected/not bound");
    }
    core.validFdOrException();
    return out;
  }

  @Override
  protected void listen(int backlog) throws IOException {
    FileDescriptor fdesc = core.validFdOrException();
    if (backlog <= 0) {
      backlog = 50;
    }
    NativeUnixSocket.listen(fdesc, backlog);
  }

  @Override
  protected boolean supportsUrgentData() {
    // We don't really support it
    return false;
  }

  @Override
  protected void sendUrgentData(int data) throws IOException {
    throw new UnsupportedOperationException();
  }

  private final class AFUNIXInputStream extends InputStream {
    private volatile boolean streamClosed = false;
    private boolean eofReached = false;

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      if (streamClosed) {
        throw new IOException("This InputStream has already been closed.");
      }
      FileDescriptor fdesc = core.validFdOrException();
      if (len == 0) {
        return 0;
      } else if (off < 0 || len < 0 || (len > buf.length - off)) {
        throw new IndexOutOfBoundsException();
      }

      return NativeUnixSocket.read(fdesc, buf, off, len, ancillaryDataSupport, socketTimeout.get());
    }

    @Override
    public int read() throws IOException {
      FileDescriptor fdesc = core.validFdOrException();

      if (eofReached) {
        return -1;
      }
      int byteRead = NativeUnixSocket.read(fdesc, null, 0, 1, ancillaryDataSupport, socketTimeout
          .get());
      if (byteRead < 0) {
        eofReached = true;
        return -1;
      } else {
        return byteRead;
      }
    }

    @Override
    public synchronized void close() throws IOException {
      streamClosed = true;
      FileDescriptor fdesc = core.validFd();
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

      return AFUNIXSocketImpl.this.available();
    }
  }

  private static boolean checkWriteInterruptedException(int bytesTransferred)
      throws InterruptedIOException {
    if (Thread.interrupted()) {
      InterruptedIOException ex = new InterruptedIOException("Thread interrupted during write");
      ex.bytesTransferred = bytesTransferred;
      Thread.currentThread().interrupt();
      throw ex;
    }
    return true;
  }

  private final class AFUNIXOutputStream extends OutputStream {
    private volatile boolean streamClosed = false;

    @Override
    public void write(int oneByte) throws IOException {
      FileDescriptor fdesc = core.validFdOrException();

      int written;
      do {
        written = NativeUnixSocket.write(fdesc, null, oneByte, 1, ancillaryDataSupport);
        if (written != 0) {
          break;
        }
      } while (checkWriteInterruptedException(0));
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
      if (streamClosed) {
        throw new SocketException("This OutputStream has already been closed.");
      }
      if (len < 0 || off < 0 || len > buf.length - off) {
        throw new IndexOutOfBoundsException();
      }
      FileDescriptor fdesc = core.validFdOrException();
      if (len == 0) {
        return;
      }

      int writtenTotal = 0;

      do {
        final int written = NativeUnixSocket.write(fdesc, buf, off, len, ancillaryDataSupport);
        if (written < 0) {
          throw new IOException("Unspecific error while writing");
        }

        len -= written;
        off += written;
        writtenTotal += written;
      } while (len > 0 && checkWriteInterruptedException(writtenTotal));
    }

    @Override
    public synchronized void close() throws IOException {
      if (streamClosed) {
        return;
      }
      streamClosed = true;
      FileDescriptor fdesc = core.validFd();
      if (fdesc != null) {
        NativeUnixSocket.shutdown(fdesc, SHUT_WR);
      }
      closedOutputStream = true;
      checkClose();
    }
  }

  @Override
  public String toString() {
    return super.toString() + "[fd=" + fd + "; addr=" + this.core.socketAddress + "; connected="
        + connected + "; bound=" + bound + "]";
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
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (optID == SocketOptions.SO_REUSEADDR) {
      return reuseAddr;
    }

    FileDescriptor fdesc = core.validFdOrException();
    return getOptionDefault(fdesc, optID, socketTimeout);
  }

  static Object getOptionDefault(FileDescriptor fdesc, int optID, AtomicInteger acceptTimeout)
      throws SocketException {
    try {
      switch (optID) {
        case SocketOptions.SO_KEEPALIVE:
          try {
            return NativeUnixSocket.getSocketOptionInt(fdesc, optID) != 0 ? true : false;
          } catch (SocketException e) {
            // ignore
            return false;
          }
        case SocketOptions.TCP_NODELAY:
          return NativeUnixSocket.getSocketOptionInt(fdesc, optID) != 0 ? true : false;
        case SocketOptions.SO_TIMEOUT:
          return Math.max((acceptTimeout == null ? 0 : acceptTimeout.get()), Math.max(
              NativeUnixSocket.getSocketOptionInt(fdesc, 0x1005), NativeUnixSocket
                  .getSocketOptionInt(fdesc, 0x1006)));
        case SocketOptions.SO_LINGER:
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
          return NativeUnixSocket.getSocketOptionInt(fdesc, optID);
        case SocketOptions.IP_TOS:
          return 0;
        case SocketOptions.SO_BINDADDR:
          return AFUNIXSocketAddress.getInetAddress(fdesc, false);
        case SocketOptions.SO_REUSEADDR:
          return false;
        default:
          throw new SocketException("Unsupported option: " + optID);
      }
    } catch (final SocketException e) {
      throw e;
    } catch (final Exception e) {
      throw (SocketException) new SocketException("Could not get option").initCause(e);
    }
  }

  @Override
  public void setOption(int optID, Object value) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (optID == SocketOptions.SO_REUSEADDR) {
      reuseAddr = expectBoolean(value) == 0 ? false : true;
      return;
    }

    FileDescriptor fdesc = core.validFdOrException();
    setOptionDefault(fdesc, optID, value, socketTimeout);
  }

  static void setOptionDefault(FileDescriptor fdesc, int optID, Object value,
      AtomicInteger acceptTimeout) throws SocketException {
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
        case SocketOptions.SO_TIMEOUT: {
          int timeout = expectInteger(value);
          NativeUnixSocket.setSocketOptionInt(fdesc, 0x1005, timeout);
          NativeUnixSocket.setSocketOptionInt(fdesc, 0x1006, timeout);
          if (acceptTimeout != null) {
            acceptTimeout.set(timeout);
          }
          return;
        }
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
          NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectInteger(value));
          return;
        case SocketOptions.SO_KEEPALIVE:
          try {
            NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectBoolean(value));
          } catch (SocketException e) {
            // ignore
          }
          return;
        case SocketOptions.TCP_NODELAY:
          NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectBoolean(value));
          return;
        case SocketOptions.IP_TOS:
          // ignore
          return;
        case SocketOptions.SO_REUSEADDR:
          // ignore
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
    FileDescriptor fdesc = core.validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_RD);
    }
  }

  @Override
  protected void shutdownOutput() throws IOException {
    FileDescriptor fdesc = core.validFd();
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
    protected Lenient(FileDescriptor fdObj) throws SocketException {
      super(fdObj);
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

  AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return NativeUnixSocket.peerCredentials(fd, new AFUNIXSocketCredentials());
  }

  final FileDescriptor[] getReceivedFileDescriptors() {
    return ancillaryDataSupport.getReceivedFileDescriptors();
  }

  final void clearReceivedFileDescriptors() {
    ancillaryDataSupport.clearReceivedFileDescriptors();
  }

  final void receiveFileDescriptors(int[] fds) throws IOException {
    ancillaryDataSupport.receiveFileDescriptors(fds);
  }

  final void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    ancillaryDataSupport.setOutboundFileDescriptors(fdescs);
  }

  final boolean hasOutboundFileDescriptors() {
    return ancillaryDataSupport.hasOutboundFileDescriptors();
  }

  int getAncillaryReceiveBufferSize() {
    return ancillaryDataSupport.getAncillaryReceiveBufferSize();
  }

  void setAncillaryReceiveBufferSize(int size) {
    ancillaryDataSupport.setAncillaryReceiveBufferSize(size);
  }

  void ensureAncillaryReceiveBufferSize(int minSize) {
    ancillaryDataSupport.ensureAncillaryReceiveBufferSize(minSize);
  }

  SocketAddress receive(ByteBuffer dst) throws IOException {
    return core.receive(dst);
  }

  int send(ByteBuffer src, SocketAddress target) throws IOException {
    return core.write(src, target, 0);
  }

  int read(ByteBuffer dst, ByteBuffer socketAddressBuffer) throws IOException {
    return core.read(dst, socketAddressBuffer, 0);
  }

  int write(ByteBuffer src) throws IOException {
    return core.write(src);
  }

  @Override
  protected FileDescriptor getFileDescriptor() {
    return core.fd;
  }

  void updatePorts(int local, int remote) {
    this.localport = local;
    if (remote >= 0) {
      this.port = remote;
    }
  }

  AFUNIXSocketAddress getLocalSocketAddress() {
    return AFUNIXSocketAddress.getSocketAddress(getFileDescriptor(), false, localport);
  }

  AFUNIXSocketAddress getRemoteSocketAddress() {
    return AFUNIXSocketAddress.getSocketAddress(getFileDescriptor(), true, port);
  }

  int getLocalPort1() {
    return localport;
  }

  int getRemotePort() {
    return port;
  }

  @Override
  protected InetAddress getInetAddress() {
    AFUNIXSocketAddress rsa = getRemoteSocketAddress();
    if (rsa == null) {
      return InetAddress.getLoopbackAddress();
    } else {
      return rsa.getInetAddress();
    }
  }
}
