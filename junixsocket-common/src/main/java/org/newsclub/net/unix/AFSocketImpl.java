/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOption;
import java.net.SocketOptions;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * junixsocket-based {@link SocketImpl}.
 * 
 * @author Christian Kohlschütter
 */
@SuppressWarnings({"PMD.CyclomaticComplexity"})
public abstract class AFSocketImpl<A extends AFSocketAddress> extends SocketImplShim {
  private static final int SHUT_RD = 0;
  private static final int SHUT_WR = 1;
  private static final int SHUT_RD_WR = 2;
  private static final int SHUTDOWN_RD_WR = (1 << SHUT_RD) | (1 << SHUT_WR);

  private final AFSocketStreamCore core;
  final AncillaryDataSupport ancillaryDataSupport = new AncillaryDataSupport();

  private final AtomicBoolean bound = new AtomicBoolean(false);
  private Boolean createType = null;
  private final AtomicBoolean connected = new AtomicBoolean(false);

  private volatile boolean closedInputStream = false;
  private volatile boolean closedOutputStream = false;

  private final AFInputStream in;
  private final AFOutputStream out;

  private boolean reuseAddr = true;

  private final AtomicInteger socketTimeout = new AtomicInteger(0);
  private final AFAddressFamily<A> addressFamily;

  private int shutdownState = 0;

  private AFSocketImplExtensions<A> implExtensions = null;

  /**
   * When the {@link AFSocketImpl} becomes unreachable (but not yet closed), we must ensure that the
   * underlying socket and all related file descriptors are closed.
   *
   * @author Christian Kohlschütter
   */
  static final class AFSocketStreamCore extends AFSocketCore {
    private final AtomicInteger pendingAccepts = new AtomicInteger(0);

    AFSocketStreamCore(AFSocketImpl<?> observed, FileDescriptor fd,
        AncillaryDataSupport ancillaryDataSupport, AFAddressFamily<?> af) {
      super(observed, fd, ancillaryDataSupport, af, false);
    }

    private void incPendingAccepts() throws SocketException {
      if (pendingAccepts.incrementAndGet() >= Integer.MAX_VALUE) {
        throw new SocketException("Too many pending accepts");
      }
    }

    private void decPendingAccepts() throws SocketException {
      pendingAccepts.decrementAndGet();
    }

    protected void createSocket(FileDescriptor fdTarget, AFSocketType type) throws IOException {
      NativeUnixSocket.createSocket(fdTarget, addressFamily().getDomain(), type.getId());
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
            createSocket(tmpFd, AFSocketType.SOCK_STREAM);
            ByteBuffer ab = socketAddress.getNativeAddressDirectBuffer();
            NativeUnixSocket.connect(ab, ab.limit(), tmpFd, inode.get());
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

        // sleep a little to give the cleaners some CPU time to actually clean up
        try {
          Thread.sleep(5);
        } catch (InterruptedException e) {
          // ignore
        }
      }
    }
  }

  /**
   * Creates a new {@link AFSocketImpl} instance.
   * 
   * @param addressFamily The address family.
   * @param fdObj The socket's {@link FileDescriptor}.
   * @throws SocketException on error.
   */
  protected AFSocketImpl(AFAddressFamily<@NonNull A> addressFamily, FileDescriptor fdObj)
      throws SocketException {
    super();
    this.addressFamily = addressFamily;
    this.address = InetAddress.getLoopbackAddress();
    this.core = new AFSocketStreamCore(this, fdObj, ancillaryDataSupport, addressFamily);
    this.fd = core.fd;
    this.in = newInputStream();
    this.out = newOutputStream();
  }

  /**
   * Creates a new {@link InputStream} for this socket.
   * 
   * @return The new stream.
   */
  protected final AFInputStream newInputStream() {
    return new AFInputStreamImpl();
  }

  /**
   * Creates a new {@link OutputStream} for this socket.
   * 
   * @return The new stream.
   */
  protected final AFOutputStream newOutputStream() {
    return new AFOutputStreamImpl();
  }

  final FileDescriptor getFD() {
    return fd;
  }

  // CPD-OFF
  final boolean isConnected() {
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

  final boolean isBound() {
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

  final AFSocketCore getCore() {
    return core;
  }

  private boolean isClosed() {
    return core.isClosed();
  }
  // CPD-ON

  @Override
  protected final void accept(SocketImpl socket) throws IOException {
    accept0(socket);
  }

  final boolean accept0(SocketImpl socket) throws IOException {
    FileDescriptor fdesc = core.validFdOrException();
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    } else if (!isBound()) {
      throw new SocketException("Socket is not bound");
    }

    AFSocketAddress socketAddress = core.socketAddress;
    if (socketAddress == null) {
      core.socketAddress = socketAddress = getLocalSocketAddress();
    }
    if (socketAddress == null) {
      throw new SocketException("Socket is not bound");
    }

    @SuppressWarnings("unchecked")
    final AFSocketImpl<A> si = (AFSocketImpl<A>) socket;
    try {
      core.incPendingAccepts();
      ByteBuffer ab = socketAddress.getNativeAddressDirectBuffer();
      if (!NativeUnixSocket.accept(ab, ab.limit(), fdesc, si.fd, core.inode.get(), socketTimeout
          .get())) {
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

  final void setSocketAddress(AFSocketAddress socketAddress) {
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
  protected final int available() throws IOException {
    FileDescriptor fdesc = core.validFdOrException();
    return NativeUnixSocket.available(fdesc, core.getThreadLocalDirectByteBuffer(0));
  }

  final void bind(SocketAddress addr, int options) throws IOException {
    if (addr == null) {
      throw new IllegalArgumentException("Cannot bind to null address");
    }
    if (!(addr instanceof AFSocketAddress)) {
      throw new SocketException("Cannot bind to this type of address: " + addr.getClass());
    }

    bound.set(true);

    if (addr == AFSocketAddress.INTERNAL_DUMMY_BIND) { // NOPMD
      core.inode.set(0);
      return;
    }

    AFSocketAddress socketAddress = (AFSocketAddress) addr;

    this.setSocketAddress(socketAddress);
    ByteBuffer ab = socketAddress.getNativeAddressDirectBuffer();
    core.inode.set(NativeUnixSocket.bind(ab, ab.limit(), fd, options));
    core.validFdOrException();
  }

  @Override
  @SuppressWarnings("hiding")
  protected final void bind(InetAddress host, int port) throws IOException {
    // ignored
  }

  private void checkClose() throws IOException {
    if (closedInputStream && closedOutputStream) {
      close();
    }
  }

  @Override
  protected final void close() throws IOException {
    shutdown();
    core.runCleaner();
  }

  @Override
  @SuppressWarnings("hiding")
  protected final void connect(String host, int port) throws IOException {
    throw new SocketException("Cannot bind to this type of address: " + InetAddress.class);
  }

  @Override
  @SuppressWarnings("hiding")
  protected final void connect(InetAddress address, int port) throws IOException {
    throw new SocketException("Cannot bind to this type of address: " + InetAddress.class);
  }

  @Override
  protected final void connect(SocketAddress addr, int connectTimeout) throws IOException {
    connect0(addr, connectTimeout);
  }

  final boolean connect0(SocketAddress addr, int connectTimeout) throws IOException {
    if (addr == AFSocketAddress.INTERNAL_DUMMY_CONNECT) { // NOPMD
      this.connected.set(true);
      return true;
    } else if (addr == AFSocketAddress.INTERNAL_DUMMY_CONNECT) { // NOPMD)
      return false;
    }

    if (!(addr instanceof AFSocketAddress)) {
      throw new SocketException("Cannot connect to this type of address: " + addr.getClass());
    }

    AFSocketAddress socketAddress = (AFSocketAddress) addr;
    ByteBuffer ab = socketAddress.getNativeAddressDirectBuffer();
    boolean success = NativeUnixSocket.connect(ab, ab.limit(), fd, -1);
    if (success) {
      setSocketAddress(socketAddress);
      this.connected.set(true);
    }
    core.validFdOrException();
    return success;
  }

  @Override
  protected final void create(boolean stream) throws IOException {
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
    createSocket(fd, stream ? AFSocketType.SOCK_STREAM : AFSocketType.SOCK_DGRAM);
  }

  @Override
  protected final AFInputStream getInputStream() throws IOException {
    if (!isConnected() && !isBound()) {
      throw new IOException("Not connected/not bound");
    }
    core.validFdOrException();
    return in;
  }

  @Override
  protected final AFOutputStream getOutputStream() throws IOException {
    if (!isClosed() && !isBound()) {
      throw new IOException("Not connected/not bound");
    }
    core.validFdOrException();
    return out;
  }

  @Override
  protected final void listen(int backlog) throws IOException {
    FileDescriptor fdesc = core.validFdOrException();
    if (backlog <= 0) {
      backlog = 50;
    }
    NativeUnixSocket.listen(fdesc, backlog);
  }

  @Override
  protected final boolean supportsUrgentData() {
    return false;
  }

  @Override
  protected final void sendUrgentData(int data) throws IOException {
    throw new UnsupportedOperationException();
  }

  private final class AFInputStreamImpl extends AFInputStream {
    private volatile boolean streamClosed = false;
    private final AtomicBoolean eofReached = new AtomicBoolean(false);

    private final int opt = (core.isBlocking() ? 0 : NativeUnixSocket.OPT_NON_BLOCKING);

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      if (streamClosed) {
        throw new IOException("This InputStream has already been closed.");
      }
      if (eofReached.get()) {
        return -1;
      }

      FileDescriptor fdesc = core.validFdOrException();
      if (len == 0) {
        return 0;
      } else if (off < 0 || len < 0 || (len > buf.length - off)) {
        throw new IndexOutOfBoundsException();
      }

      try {
        return NativeUnixSocket.read(fdesc, buf, off, len, opt, ancillaryDataSupport, socketTimeout
            .get());
      } catch (EOFException e) {
        eofReached.set(true);
        throw e;
      }
    }

    @Override
    public int read() throws IOException {
      FileDescriptor fdesc = core.validFdOrException();

      if (eofReached.get()) {
        return -1;
      }

      int byteRead = NativeUnixSocket.read(fdesc, null, 0, 1, opt, ancillaryDataSupport,
          socketTimeout.get());
      if (byteRead < 0) {
        eofReached.set(true);
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

      return AFSocketImpl.this.available();
    }

    @Override
    public FileDescriptor getFileDescriptor() throws IOException {
      return getFD();
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

  private final class AFOutputStreamImpl extends AFOutputStream {
    private volatile boolean streamClosed = false;

    private final int opt = (core.isBlocking() ? 0 : NativeUnixSocket.OPT_NON_BLOCKING);

    @Override
    public void write(int oneByte) throws IOException {
      FileDescriptor fdesc = core.validFdOrException();

      int written;
      do {
        written = NativeUnixSocket.write(fdesc, null, oneByte, 1, opt, ancillaryDataSupport);
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
        final int written = NativeUnixSocket.write(fdesc, buf, off, len, opt, ancillaryDataSupport);
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

    @Override
    public FileDescriptor getFileDescriptor() throws IOException {
      return getFD();
    }
  }

  @Override
  public final String toString() {
    return super.toString() + "[fd=" + fd + "; addr=" + this.core.socketAddress + "; connected="
        + connected + "; bound=" + bound + "]";
  }

  private static int expectInteger(Object value) throws SocketException {
    if (value == null) {
      throw (SocketException) new SocketException("Value must not be null").initCause(
          new NullPointerException());
    }
    try {
      return (Integer) value;
    } catch (final ClassCastException e) {
      throw (SocketException) new SocketException("Unsupported value: " + value).initCause(e);
    }
  }

  private static int expectBoolean(Object value) throws SocketException {
    if (value == null) {
      throw (SocketException) new SocketException("Value must not be null").initCause(
          new NullPointerException());
    }
    try {
      return ((Boolean) value).booleanValue() ? 1 : 0;
    } catch (final ClassCastException e) {
      throw (SocketException) new SocketException("Unsupported value: " + value).initCause(e);
    }
  }

  @Override
  public Object getOption(int optID) throws SocketException {
    return getOption0(optID);
  }

  private Object getOption0(int optID) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (optID == SocketOptions.SO_REUSEADDR) {
      return reuseAddr;
    }

    FileDescriptor fdesc = core.validFdOrException();
    return getOptionDefault(fdesc, optID, socketTimeout, addressFamily);
  }

  static final Object getOptionDefault(FileDescriptor fdesc, int optID, AtomicInteger acceptTimeout,
      AFAddressFamily<?> af) throws SocketException {
    try {
      switch (optID) {
        case SocketOptions.SO_KEEPALIVE:
          try {
            return (NativeUnixSocket.getSocketOptionInt(fdesc, optID) != 0);
          } catch (SocketException e) {
            // ignore
            return false;
          }
        case SocketOptions.TCP_NODELAY:
          return (NativeUnixSocket.getSocketOptionInt(fdesc, optID) != 0);
        case SocketOptions.SO_TIMEOUT:
          int v = Math.max(NativeUnixSocket.getSocketOptionInt(fdesc, 0x1005), NativeUnixSocket
              .getSocketOptionInt(fdesc, 0x1006));
          if (v == -1) {
            // special value, meaning: do not override infinite timeout from native code
            return 0;
          }
          return Math.max((acceptTimeout == null ? 0 : acceptTimeout.get()), v);
        case SocketOptions.SO_LINGER:
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
          return NativeUnixSocket.getSocketOptionInt(fdesc, optID);
        case SocketOptions.IP_TOS:
          return 0;
        case SocketOptions.SO_BINDADDR:
          return AFSocketAddress.getInetAddress(fdesc, false, af);
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
    setOption0(optID, value);
  }

  private void setOption0(int optID, Object value) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (optID == SocketOptions.SO_REUSEADDR) {
      reuseAddr = (expectBoolean(value) != 0);
      return;
    }

    FileDescriptor fdesc = core.validFdOrException();
    setOptionDefault(fdesc, optID, value, socketTimeout);
  }

  /**
   * Like {@link #getOption(int)}, but ignores exceptions for certain option IDs.
   * 
   * @param optID The option ID.
   * @return The value.
   * @throws SocketException on error.
   */
  protected final Object getOptionLenient(int optID) throws SocketException {
    try {
      return getOption0(optID);
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

  /**
   * Like {@link #setOption(int, Object)}, but ignores exceptions for certain option IDs.
   * 
   * @param optID The option ID.
   * @param value The value.
   * @throws SocketException on error.
   */
  protected final void setOptionLenient(int optID, Object value) throws SocketException {
    try {
      setOption0(optID, value);
    } catch (SocketException e) {
      switch (optID) {
        case SocketOptions.TCP_NODELAY:
          return;
        default:
          throw e;
      }
    }
  }

  static final void setOptionDefault(FileDescriptor fdesc, int optID, Object value,
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

  /**
   * Shuts down both input and output at once. Equivalent to calling {@link #shutdownInput()} and
   * {@link #shutdownOutput()}.
   * 
   * @throws IOException on error.
   */
  protected final void shutdown() throws IOException {
    FileDescriptor fdesc = core.validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_RD_WR);
      shutdownState = 0;
    }
  }

  @Override
  protected final void shutdownInput() throws IOException {
    FileDescriptor fdesc = core.validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_RD);
      shutdownState |= 1 << (SHUT_RD);
      if (shutdownState == SHUTDOWN_RD_WR) {
        NativeUnixSocket.shutdown(fdesc, SHUT_RD_WR);
        shutdownState = 0;
      }
    }
  }

  @Override
  protected final void shutdownOutput() throws IOException {
    FileDescriptor fdesc = core.validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_WR);
      shutdownState |= 1 << (SHUT_RD_WR);
      if (shutdownState == SHUTDOWN_RD_WR) {
        NativeUnixSocket.shutdown(fdesc, SHUT_RD_WR);
        shutdownState = 0;
      }
    }
  }

  final int getAncillaryReceiveBufferSize() {
    return ancillaryDataSupport.getAncillaryReceiveBufferSize();
  }

  final void setAncillaryReceiveBufferSize(int size) {
    ancillaryDataSupport.setAncillaryReceiveBufferSize(size);
  }

  final void ensureAncillaryReceiveBufferSize(int minSize) {
    ancillaryDataSupport.ensureAncillaryReceiveBufferSize(minSize);
  }

  AncillaryDataSupport getAncillaryDataSupport() {
    return ancillaryDataSupport;
  }

  final SocketAddress receive(ByteBuffer dst) throws IOException {
    return core.receive(dst);
  }

  final int send(ByteBuffer src, SocketAddress target) throws IOException {
    return core.write(src, target, 0);
  }

  final int read(ByteBuffer dst, ByteBuffer socketAddressBuffer) throws IOException {
    return core.read(dst, socketAddressBuffer, 0);
  }

  final int write(ByteBuffer src) throws IOException {
    return core.write(src);
  }

  @Override
  protected final FileDescriptor getFileDescriptor() {
    return core.fd;
  }

  final void updatePorts(int local, int remote) {
    this.localport = local;
    if (remote >= 0) {
      this.port = remote;
    }
  }

  final @Nullable A getLocalSocketAddress() {
    return AFSocketAddress.getSocketAddress(getFileDescriptor(), false, localport, addressFamily);
  }

  final @Nullable A getRemoteSocketAddress() {
    return AFSocketAddress.getSocketAddress(getFileDescriptor(), true, port, addressFamily);
  }

  final int getLocalPort1() {
    return localport;
  }

  final int getRemotePort() {
    return port;
  }

  @Override
  protected final InetAddress getInetAddress() {
    @Nullable
    A rsa = getRemoteSocketAddress();
    if (rsa == null) {
      return InetAddress.getLoopbackAddress();
    } else {
      return rsa.getInetAddress();
    }
  }

  final void createSocket(FileDescriptor fdTarget, AFSocketType type) throws IOException {
    NativeUnixSocket.createSocket(fdTarget, addressFamily.getDomain(), type.getId());
  }

  final AFAddressFamily<A> getAddressFamily() {
    return addressFamily;
  }

  @Override
  protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      ((AFSocketImpl<?>) this).getCore().setOption((AFSocketOption<T>) name, value);
      return;
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      super.setOption(name, value);
    } else {
      setOption(optionId, value);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T getOption(SocketOption<T> name) throws IOException {
    if (name instanceof AFSocketOption<?>) {
      return ((AFSocketImpl<?>) this).getCore().getOption((AFSocketOption<T>) name);
    }
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      return super.getOption(name);
    } else {
      return (T) getOption(optionId);
    }
  }

  @Override
  protected Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }

  /**
   * Returns the internal helper instance for address-specific extensions.
   * 
   * @return The helper instance.
   * @throws UnsupportedOperationException if such extensions are not supported for this address
   *           type.
   */
  protected final synchronized AFSocketImplExtensions<A> getImplExtensions() {
    if (implExtensions == null) {
      implExtensions = addressFamily.initImplExtensions(ancillaryDataSupport);
    }
    return implExtensions;
  }
}
