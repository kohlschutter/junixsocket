/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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

import static org.newsclub.net.unix.NativeUnixSocket.SHUT_RD;
import static org.newsclub.net.unix.NativeUnixSocket.SHUT_RD_WR;
import static org.newsclub.net.unix.NativeUnixSocket.SHUT_WR;

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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.newsclub.net.unix.pool.MutableHolder;
import org.newsclub.net.unix.pool.ObjectPool.Lease;

/**
 * junixsocket-based {@link SocketImpl}.
 *
 * @author Christian Kohlschütter
 * @param <A> The supported address type.
 */
@SuppressWarnings({
    "PMD.CyclomaticComplexity", "PMD.CouplingBetweenObjects",
    "UnsafeFinalization" /* errorprone */})
public abstract class AFSocketImpl<A extends AFSocketAddress> extends SocketImplShim {
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

  private final AtomicBoolean reuseAddr = new AtomicBoolean(true);

  private final AtomicInteger socketTimeout = new AtomicInteger(0);
  private final AFAddressFamily<A> addressFamily;

  private int shutdownState = 0;

  private AFSocketImplExtensions<A> implExtensions = null;

  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * When the {@link AFSocketImpl} becomes unreachable (but not yet closed), we must ensure that the
   * underlying socket and all related file descriptors are closed.
   *
   * @author Christian Kohlschütter
   */
  static final class AFSocketStreamCore extends AFSocketCore {
    AFSocketStreamCore(AFSocketImpl<?> observed, FileDescriptor fd,
        AncillaryDataSupport ancillaryDataSupport, AFAddressFamily<?> af) {
      super(observed, fd, ancillaryDataSupport, af, false);
    }

    void createSocket(FileDescriptor fdTarget, AFSocketType type) throws IOException {
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
      if (!hasPendingAccepts()) {
        return;
      }
      try {
        ThreadUtil.runOnSystemThread(this::unblockAccepts0);
      } catch (InterruptedException e) {
        // ignore
      }
    }

    private void unblockAccepts0() {
      while (hasPendingAccepts()) {
        try {
          FileDescriptor tmpFd = new FileDescriptor();

          try (Lease<ByteBuffer> abLease = socketAddress.getNativeAddressDirectBuffer()) {
            createSocket(tmpFd, AFSocketType.SOCK_STREAM);
            ByteBuffer ab = abLease.get();
            NativeUnixSocket.connect(ab, ab.limit(), tmpFd, inode.get());
          } catch (IOException e) {
            // there's nothing more we can do to unlock these accepts
            // (e.g., SocketException: No such file or directory)
            return;
          }
          if (isShutdownOnClose()) {
            try {
              NativeUnixSocket.shutdown(tmpFd, SHUT_RD_WR);
            } catch (Exception e) {
              // ignore
            }
          }
          try {
            NativeUnixSocket.close(tmpFd);
          } catch (Exception e) {
            // ignore
          }
        } catch (RuntimeException e) {
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
   */
  protected AFSocketImpl(AFAddressFamily<@NonNull A> addressFamily, FileDescriptor fdObj) {
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

  boolean isClosed() {
    return closed.get() || core.isClosed();
  }
  // CPD-ON

  @Override
  protected final void accept(SocketImpl socket) throws IOException {
    accept0(socket);
  }

  @SuppressWarnings({
      "Finally" /* errorprone */, //
      "PMD.CognitiveComplexity", "PMD.NPathComplexity", "PMD.NcssCount"})
  final boolean accept0(SocketImpl socket) throws IOException {
    FileDescriptor fdesc = core.validFdOrException();
    if (isClosed()) {
      throw new SocketClosedException();
    } else if (!isBound()) {
      throw new NotBoundSocketException();
    }

    AFSocketAddress socketAddress = core.socketAddress;
    AFSocketAddress boundSocketAddress = getLocalSocketAddress();
    if (boundSocketAddress != null) {
      // Always resolve bound address from wildcard address, etc.
      core.socketAddress = socketAddress = boundSocketAddress;
    }

    if (socketAddress == null) {
      throw new NotBoundSocketException();
    }

    @SuppressWarnings("unchecked")
    final AFSocketImpl<A> si = (AFSocketImpl<A>) socket;
    core.incPendingAccepts();

    final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && core.isBlocking()) || core
        .isVirtualBlocking();

    long now = virtualBlocking ? System.currentTimeMillis() : 0;
    boolean park = false;
    virtualThreadLoop : do {
      if (virtualBlocking) {
        if (park) {
          VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_ACCEPT, now,
              socketTimeout::get, this::close);
        }
      }

      try (Lease<ByteBuffer> abLease = socketAddress.getNativeAddressDirectBuffer()) {
        ByteBuffer ab = abLease.get();

        SocketException caught = null;
        try {
          boolean success;
          if (virtualBlocking) {
            core.configureVirtualBlocking(true);
          }
          try {
            success = NativeUnixSocket.accept(ab, ab.limit(), fdesc, si.fd, core.inode.get(),
                socketTimeout.get());
          } catch (SocketTimeoutException e) {
            if (virtualBlocking) {
              // try again
              park = true;
              continue virtualThreadLoop;
            } else {
              throw e;
            }
          } finally {
            if (virtualBlocking) {
              core.configureVirtualBlocking(false);
            }
          }

          if (virtualBlocking) {
            if (success) {
              // mark the accepted socket as blocking if necessary
              NativeUnixSocket.configureBlocking(si.fd, core.isBlocking());
            } else {
              // try again
              park = true;
              continue virtualThreadLoop;
            }
          }
        } catch (NotConnectedSocketException | SocketClosedException // NOPMD.ExceptionAsFlowControl
            | BrokenPipeSocketException e) {
          try {
            close();
          } catch (Exception e2) {
            e.addSuppressed(e2);
          }
          throw e;
        } catch (SocketException e) { // NOPMD.ExceptionAsFlowControl
          caught = e;
        } finally { // NOPMD.DoNotThrowExceptionInFinally
          if (!isBound() || isClosed()) {
            if (getCore().isShutdownOnClose()) {
              try {
                NativeUnixSocket.shutdown(si.fd, SHUT_RD_WR);
              } catch (Exception e) {
                // ignore
              }
            }
            try {
              NativeUnixSocket.close(si.fd);
            } catch (Exception e) {
              // ignore
            }
            if (caught != null) {
              throw caught;
            } else {
              throw new BrokenPipeSocketException("Socket is closed");
            }
          } else if (caught != null) {
            throw caught;
          }
        }
      } finally {
        core.decPendingAccepts();
      }
      break; // NOPMD.AvoidBranchingStatementAsLastInLoop virtualThreadLoop
    } while (true); // NOPMD.WhileLoopWithLiteralBoolean

    if (!si.fd.valid()) {
      return false;
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
    try (Lease<MutableHolder<ByteBuffer>> lease = core.getPrivateDirectByteBuffer(0)) {
      return NativeUnixSocket.available(fdesc, lease.get().get());
    }
  }

  final void bind(SocketAddress addr, int options) throws IOException {
    if (addr == null) {
      addr = addressFamily.nullBindAddress();
      if (addr == null) {
        throw new UnsupportedOperationException("Cannot bind to null address");
      }
    }

    if (addr == AFSocketAddress.INTERNAL_DUMMY_BIND) { // NOPMD
      bound.set(true);
      core.inode.set(0);
      return;
    }

    addr = AFSocketAddress.mapOrFail(addr, addressFamily.getSocketAddressClass());
    bound.set(true);

    AFSocketAddress socketAddress = Objects.requireNonNull((AFSocketAddress) addr);

    this.setSocketAddress(socketAddress);
    try (Lease<ByteBuffer> abLease = socketAddress.getNativeAddressDirectBuffer()) {
      ByteBuffer ab = abLease.get();
      long inode = NativeUnixSocket.bind(ab, ab.limit(), fd, options);
      core.inode.set(inode);
    }
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
    this.closed.set(true);
    try {
      shutdown();
    } catch (NotConnectedSocketException | SocketClosedException e) {
      // ignore
    }

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

  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NPathComplexity", "PMD.NcssCount"})
  final boolean connect0(SocketAddress addr, int connectTimeout) throws IOException {
    if (addr == AFSocketAddress.INTERNAL_DUMMY_CONNECT) { // NOPMD
      this.connected.set(true);
      return true;
    } else if (addr == AFSocketAddress.INTERNAL_DUMMY_DONT_CONNECT) { // NOPMD)
      return false;
    }

    addr = AFSocketAddress.mapOrFail(addr, addressFamily.getSocketAddressClass());
    AFSocketAddress socketAddress = Objects.requireNonNull((AFSocketAddress) addr);

    final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && core.isBlocking()) || core
        .isVirtualBlocking();
    long now = virtualBlocking ? System.currentTimeMillis() : 0;

    /**
     * If set, a two-phase connect is in flight, and the value holds the connect timeout.
     */
    AFSupplier<Integer> virtualConnectTimeout = null;

    if (virtualBlocking) {
      core.configureVirtualBlocking(true);
    }
    boolean park = false;
    try {
      virtualThreadLoop : do {
        try (Lease<ByteBuffer> abLease = socketAddress.getNativeAddressDirectBuffer()) {
          ByteBuffer ab = abLease.get();
          boolean success = false;
          boolean ignoreSpuriousTimeout = true;
          do {
            if (virtualBlocking) {
              if (virtualConnectTimeout != null) {
                if (park) {
                  VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fd, SelectionKey.OP_CONNECT,
                      now, virtualConnectTimeout, this::close);
                }
              } else {
                Thread.yield();
              }
            }

            if (virtualBlocking) {
              core.configureVirtualBlocking(true);
            }
            try {
              success = NativeUnixSocket.connect(ab, ab.limit(), fd, -2);
              if (!success && virtualBlocking) {
                // try again (non-blocking timeout)
                if (virtualConnectTimeout == null) {
                  virtualConnectTimeout = () -> connectTimeout;
                }
                park = true;
                continue virtualThreadLoop;
              }
              break;
            } catch (SocketTimeoutException e) {
              // Ignore spurious timeout once when SO_TIMEOUT==0
              // seen on older Linux kernels with AF_VSOCK running in qemu
              if (ignoreSpuriousTimeout) {
                Object o = getOption(SocketOptions.SO_TIMEOUT);
                if (o instanceof Integer) {
                  if (((Integer) o) == 0) {
                    ignoreSpuriousTimeout = false;
                    continue;
                  }
                } else if (o == null) {
                  ignoreSpuriousTimeout = false;
                  continue;
                }
              }
              throw e;
            } catch (NotConnectedSocketException | SocketClosedException
                | BrokenPipeSocketException e) {
              try {
                close();
              } catch (Exception e2) {
                e.addSuppressed(e2);
              }
              throw e;
            } catch (SocketException e) {
              if (virtualBlocking) {
                Thread.yield();
              }
              throw e;
            } finally {
              if (virtualBlocking) {
                core.configureVirtualBlocking(false);
              }
            }
          } while (ThreadUtil.checkNotInterruptedOrThrow());
          if (success) {
            setSocketAddress(socketAddress);
            this.connected.set(true);
          }
          core.validFdOrException();
          return success;
        }
      } while (true); // NOPMD.WhileLoopWithLiteralBoolean
    } finally {
      if (virtualBlocking) {
        core.configureVirtualBlocking(true);
      }
    }
  }

  @Override
  protected final void create(boolean stream) throws IOException {
    if (isClosed()) {
      throw new SocketException("Already closed");
    }
    if (fd.valid()) {
      if (createType != null) {
        if (createType.booleanValue() != stream) { // NOPMD.UnnecessaryBoxing
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
      close();
      throw new SocketClosedException("Not connected/not bound");
    }
    core.validFdOrException();
    return in;
  }

  @Override
  protected final AFOutputStream getOutputStream() throws IOException {
    if (!isClosed() && !isBound()) {
      close();
      throw new SocketClosedException("Not connected/not bound");
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

    private final int defaultOpt = (core.isBlocking() ? 0 : NativeUnixSocket.OPT_NON_BLOCKING);

    @SuppressWarnings("PMD.CognitiveComplexity")
    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      if (streamClosed) {
        throw new SocketClosedException("This InputStream has already been closed.");
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

      final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && core.isBlocking()) || core
          .isVirtualBlocking();
      final long now;
      final int opt;
      if (virtualBlocking) {
        now = System.currentTimeMillis();
        opt = defaultOpt | NativeUnixSocket.OPT_NON_BLOCKING;
      } else {
        now = 0;
        opt = defaultOpt;
      }

      int read;

      boolean park = false;
      virtualThreadLoop : do {
        if (virtualBlocking) {
          if (park) {
            VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_READ, now,
                socketTimeout::get, this::forceCloseSocket);
          }
          core.configureVirtualBlocking(true);
        }

        try {
          read = NativeUnixSocket.read(fdesc, buf, off, len, opt, ancillaryDataSupport,
              socketTimeout.get());
          if (read == -2) {
            if (virtualBlocking) {
              // sleep again
              park = true;
              continue virtualThreadLoop;
            } else {
              read = 0;
            }
          }
        } catch (SocketTimeoutException e) {
          if (virtualBlocking) {
            // sleep again
            park = true;
            continue virtualThreadLoop;
          } else {
            throw e;
          }
        } catch (EOFException e) {
          eofReached.set(true);
          throw e;
        } finally {
          if (virtualBlocking) {
            core.configureVirtualBlocking(false);
          }
        }
        break; // NOPMD.AvoidBranchingStatementAsLastInLoop virtualThreadLoop
      } while (true); // NOPMD.WhileLoopWithLiteralBoolean

      return read;
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    @Override
    public int read() throws IOException {
      FileDescriptor fdesc = core.validFdOrException();

      if (eofReached.get()) {
        return -1;
      }

      // CPD-OFF
      final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && core.isBlocking()) || core
          .isVirtualBlocking();
      final long now;
      final int opt;
      if (virtualBlocking) {
        now = System.currentTimeMillis();
        opt = defaultOpt | NativeUnixSocket.OPT_NON_BLOCKING;
      } else {
        now = 0;
        opt = defaultOpt;
      }

      boolean park = false;
      virtualThreadLoop : do {
        if (virtualBlocking) {
          if (park) {
            VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_READ, now,
                socketTimeout::get, this::forceCloseSocket);
          }
          core.configureVirtualBlocking(true);
        }

        try {
          int byteRead = NativeUnixSocket.read(fdesc, null, 0, 1, opt, ancillaryDataSupport,
              socketTimeout.get());
          if (byteRead < 0) {
            if (byteRead == -2) {
              if (virtualBlocking) {
                // sleep again
                park = true;
                continue virtualThreadLoop;
              } else {
                byteRead = -1;
              }
            }
            eofReached.set(true);
            return -1;
          } else {
            return byteRead;
          }
        } catch (SocketTimeoutException e) {
          if (virtualBlocking) {
            // sleep again
            park = true;
            continue virtualThreadLoop;
          } else {
            throw e;
          }
        } finally {
          if (virtualBlocking) {
            core.configureVirtualBlocking(false);
          }
        }
      } while (true); // NOPMD.WhileLoopWithLiteralBoolean

      // CPD-ON
    }

    private void forceCloseSocket() throws IOException {
      closedOutputStream = true;
      close();
    }

    @Override
    public synchronized void close() throws IOException {
      if (streamClosed || isClosed()) {
        return;
      }
      streamClosed = true;
      FileDescriptor fdesc = core.validFd();
      if (fdesc != null && getCore().isShutdownOnClose()) {
        NativeUnixSocket.shutdown(fdesc, SHUT_RD);
      }

      closedInputStream = true;
      checkClose();
    }

    @Override
    public int available() throws IOException {
      if (streamClosed) {
        throw new SocketClosedException("This InputStream has already been closed.");
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
    if (Thread.currentThread().isInterrupted()) {
      InterruptedIOException ex = new InterruptedIOException("write");
      ex.bytesTransferred = bytesTransferred;
      throw ex;
    }
    return true;
  }

  private final class AFOutputStreamImpl extends AFOutputStream {
    private volatile boolean streamClosed = false;

    private final int defaultOpt = (core.isBlocking() ? 0 : NativeUnixSocket.OPT_NON_BLOCKING);

    @SuppressWarnings("PMD.CognitiveComplexity")
    @Override
    public void write(int oneByte) throws IOException {
      FileDescriptor fdesc = core.validFdOrException();

      final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && core.isBlocking()) || core
          .isVirtualBlocking();
      final long now;
      final int opt;
      if (virtualBlocking) {
        now = System.currentTimeMillis();
        opt = defaultOpt | NativeUnixSocket.OPT_NON_BLOCKING;
      } else {
        now = 0;
        opt = defaultOpt;
      }

      boolean park = false;
      virtualThreadLoop : do {
        if (virtualBlocking) {
          if (park) {
            VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_WRITE, now,
                socketTimeout::get, this::forceCloseSocket);
          }
          core.configureVirtualBlocking(true);
        }

        try {
          int written;
          do {
            written = NativeUnixSocket.write(fdesc, null, oneByte, 1, opt, ancillaryDataSupport);
            if (written != 0) {
              break;
            }
            if (virtualBlocking) {
              park = true;
              continue virtualThreadLoop;
            }
          } while (checkWriteInterruptedException(0));
        } catch (NotConnectedSocketException | SocketClosedException
            | BrokenPipeSocketException e) {
          try {
            forceCloseSocket();
          } catch (Exception e2) {
            e.addSuppressed(e2);
          }
          throw e;
        } catch (SocketTimeoutException e) {
          if (virtualBlocking) {
            // try again
            park = true;
            continue virtualThreadLoop;
          } else {
            throw e;
          }
        } finally {
          if (virtualBlocking) {
            core.configureVirtualBlocking(false);
          }
        }
        break; // NOPMD.AvoidBranchingStatementAsLastInLoop virtualThreadLoop
      } while (true); // NOPMD.WhileLoopWithLiteralBoolean
    }

    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NPathComplexity"})
    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
      if (streamClosed) {
        throw new SocketException("This OutputStream has already been closed.");
      }
      if (len < 0 || off < 0 || len > buf.length - off) {
        throw new IndexOutOfBoundsException();
      }
      FileDescriptor fdesc = core.validFdOrException();

      // NOTE: writing messages with len == 0 should be permissible (unless ignored in native code)
      // For certain sockets, empty messages can be used to probe if the remote connection is alive
      if (len == 0 && !AFSocket.supports(AFSocketCapability.CAPABILITY_ZERO_LENGTH_SEND)) {
        return;
      }

      final boolean virtualBlocking = (ThreadUtil.isVirtualThread() && core.isBlocking()) || core
          .isVirtualBlocking();
      final long now;
      final int opt;
      if (virtualBlocking) {
        now = System.currentTimeMillis();
        opt = defaultOpt | NativeUnixSocket.OPT_NON_BLOCKING;
      } else {
        now = 0;
        opt = defaultOpt;
      }

      int writtenTotal = 0;
      do {
        boolean park = false;
        virtualThreadLoop : do {
          if (virtualBlocking) {
            if (park) {
              VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_WRITE, now,
                  socketTimeout::get, this::forceCloseSocket);
            }
            core.configureVirtualBlocking(true);
          }

          final int written;
          try {
            written = NativeUnixSocket.write(fdesc, buf, off, len, opt, ancillaryDataSupport);
            if (written == 0 && virtualBlocking) {
              // try again
              park = true;
              continue virtualThreadLoop;
            }
            if (written < 0) {
              if (len == 0) {
                // This exception is only useful to detect OS-level bugs that we need to
                // work-around
                // in native code.
                // throw new IOException("Error while writing zero-length byte array; try -D"
                // + AFSocket.PROP_LIBRARY_DISABLE_CAPABILITY_PREFIX
                // + AFSocketCapability.CAPABILITY_ZERO_LENGTH_SEND.name() + "=true");

                // ignore
                return;
              } else {
                throw new IOException("Unspecific error while writing");
              }
            }
          } catch (NotConnectedSocketException | SocketClosedException
              | BrokenPipeSocketException e) {
            try {
              forceCloseSocket();
            } catch (Exception e2) {
              e.addSuppressed(e2);
            }
            throw e;
          } catch (SocketTimeoutException e) {
            if (virtualBlocking) {
              // try again
              park = true;
              continue virtualThreadLoop;
            } else {
              throw e;
            }
          } finally {
            if (virtualBlocking) {
              core.configureVirtualBlocking(false);
            }
          }

          len -= written;
          off += written;
          writtenTotal += written;
          break; // NOPMD.AvoidBranchingStatementAsLastInLoop virtualThreadLoop
        } while (true); // NOPMD.WhileLoopWithLiteralBoolean

      } while (len > 0 && checkWriteInterruptedException(writtenTotal));
    }

    private void forceCloseSocket() throws IOException {
      closedInputStream = true;
      close();
    }

    @Override
    public synchronized void close() throws IOException {
      if (streamClosed || isClosed()) {
        return;
      }
      streamClosed = true;
      FileDescriptor fdesc = core.validFd();
      if (fdesc != null && getCore().isShutdownOnClose()) {
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
      return ((Boolean) value) ? 1 : 0;
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
      return reuseAddr.get();
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
      reuseAddr.set((expectBoolean(value) != 0));
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
          try {
            NativeUnixSocket.setSocketOptionInt(fdesc, 0x1005, timeout);
          } catch (InvalidArgumentSocketException e) {
            // Perhaps the socket is shut down?
          }
          try {
            NativeUnixSocket.setSocketOptionInt(fdesc, 0x1006, timeout);
          } catch (InvalidArgumentSocketException e) {
            // Perhaps the socket is shut down?
          }
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
  protected final synchronized void shutdown() throws IOException {
    FileDescriptor fdesc = core.validFd();
    if (fdesc != null) {
      NativeUnixSocket.shutdown(fdesc, SHUT_RD_WR);
      shutdownState = 0;
    }
  }

  @Override
  protected final synchronized void shutdownInput() throws IOException {
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
  protected final synchronized void shutdownOutput() throws IOException {
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
    return core.receive(dst, socketTimeout::get);
  }

  final int send(ByteBuffer src, SocketAddress target) throws IOException {
    return core.write(src, socketTimeout::get, target, 0);
  }

  final int read(ByteBuffer dst, ByteBuffer socketAddressBuffer) throws IOException {
    return core.read(dst, socketTimeout::get, socketAddressBuffer, 0);
  }

  final int write(ByteBuffer src) throws IOException {
    return core.write(src, socketTimeout::get);
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
      getCore().setOption((AFSocketOption<T>) name, value);
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
      return getCore().getOption((AFSocketOption<T>) name);
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
