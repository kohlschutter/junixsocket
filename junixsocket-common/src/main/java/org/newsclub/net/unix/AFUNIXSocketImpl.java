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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
  private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

  private final SocketCleanableState cleanableState;

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean bound = new AtomicBoolean(false);
  private boolean connected = false;

  private volatile boolean closedInputStream = false;
  private volatile boolean closedOutputStream = false;

  private final AFUNIXInputStream in = newInputStream();
  private final AFUNIXOutputStream out = newOutputStream();

  private boolean reuseAddr = true;

  private ByteBuffer ancillaryReceiveBuffer = EMPTY_BUFFER;
  private final List<FileDescriptor[]> receivedFileDescriptors = Collections.synchronizedList(
      new LinkedList<FileDescriptor[]>());
  private int[] pendingFileDescriptors = null;

  private int timeout = 0;

  /**
   * When the {@link AFUNIXSocketImpl} becomes unreachable (but not yet closed), we must ensure that
   * the underlying socket and all related file descriptors are closed.
   *
   * @author Christian Kohlschütter
   */
  private static class SocketCleanableState extends CleanableState {
    private final FileDescriptor fd = new FileDescriptor();
    private final AtomicInteger pendingAccepts = new AtomicInteger(0);
    private final Map<FileDescriptor, Integer> openReceivedFileDescriptors = Collections
        .synchronizedMap(new HashMap<FileDescriptor, Integer>());

    /**
     * We keep track of the server's inode to detect when another server connects to our address.
     */
    private final AtomicLong inode = new AtomicLong(-1);

    private AFUNIXSocketAddress socketAddress;

    private SocketCleanableState(AFUNIXSocketImpl observed) throws IOException {
      super(observed);
    }

    @Override
    protected void doClean() {
      if (fd != null && fd.valid()) {
        try {
          doClose();
        } catch (IOException e) {
          // ignore
        }
      }
      closeReceivedFileDescriptors();
    }

    private void incPendingAccepts() throws SocketException {
      if (pendingAccepts.incrementAndGet() >= Integer.MAX_VALUE) {
        throw new SocketException("Too many pending accepts");
      }
    }

    private void decPendingAccepts() throws SocketException {
      pendingAccepts.decrementAndGet();
    }

    private void doClose() throws IOException {
      NativeUnixSocket.shutdown(fd, SHUT_RD_WR);

      if (socketAddress != null && socketAddress.getBytes() != null && inode.get() >= 0) {
        unblockAccepts();
      }

      NativeUnixSocket.close(fd);
    }

    /**
     * Unblock other threads that are currently waiting on accept, simply by connecting to the
     * socket.
     */
    private void unblockAccepts() {
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

    private void closeReceivedFileDescriptors() {
      synchronized (openReceivedFileDescriptors) {
        for (FileDescriptor desc : openReceivedFileDescriptors.keySet()) {
          try {
            NativeUnixSocket.close(desc);
          } catch (Exception e) {
            // ignore
          }
        }
      }
    }
  }

  protected AFUNIXSocketImpl() throws IOException {
    super();
    this.address = InetAddress.getLoopbackAddress();
    this.cleanableState = new SocketCleanableState(this);
    this.fd = cleanableState.fd;
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

  boolean isBound() {
    return bound.get();
  }

  private boolean isClosed() {
    return closed.get();
  }

  @Override
  protected void accept(SocketImpl socket) throws IOException {
    FileDescriptor fdesc = validFdOrException();
    if (!isBound() || isClosed()) {
      throw new SocketException("Socket is closed");
    }

    AFUNIXSocketAddress socketAddress = cleanableState.socketAddress;

    final AFUNIXSocketImpl si = (AFUNIXSocketImpl) socket;
    try {
      cleanableState.incPendingAccepts();
      NativeUnixSocket.accept(socketAddress.getBytes(), fdesc, si.fd, cleanableState.inode.get(),
          this.timeout);
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
      cleanableState.decPendingAccepts();
    }
    si.setSocketAddress(socketAddress);
    si.connected = true;
    si.port = socketAddress.getPort();
    si.address = socketAddress.getAddress();
  }

  private void setSocketAddress(AFUNIXSocketAddress socketAddress) {
    this.cleanableState.socketAddress = socketAddress;
  }

  @Override
  protected int available() throws IOException {
    FileDescriptor fdesc = validFdOrException();
    return NativeUnixSocket.available(fdesc);
  }

  protected void bind(SocketAddress addr, int options) throws IOException {
    if (addr == null) {
      throw new IllegalArgumentException("Cannot bind to null address");
    }
    if (!(addr instanceof AFUNIXSocketAddress)) {
      throw new SocketException("Cannot bind to this type of address: " + addr.getClass());
    }
    AFUNIXSocketAddress socketAddress = (AFUNIXSocketAddress) addr;

    this.setSocketAddress(socketAddress);
    this.address = socketAddress.getAddress();

    cleanableState.inode.set(NativeUnixSocket.bind(socketAddress.getBytes(), fd, options));
    validFdOrException();
    bound.set(true);
    this.localport = socketAddress.getPort();
  }

  @Override
  @SuppressWarnings("hiding")
  protected void bind(InetAddress host, int port) throws IOException {
    throw new SocketException("Cannot bind to this type of address: " + InetAddress.class);
  }

  private void checkClose() throws IOException {
    if (closedInputStream && closedOutputStream) {
      close();
    }
  }

  @Override
  protected final void close() throws IOException {
    closed.set(true);
    cleanableState.runCleaner();
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
    if (!(addr instanceof AFUNIXSocketAddress)) {
      throw new SocketException("Cannot bind to this type of address: " + addr.getClass());
    }
    AFUNIXSocketAddress socketAddress = (AFUNIXSocketAddress) addr;
    setSocketAddress(socketAddress);
    NativeUnixSocket.connect(socketAddress.getBytes(), fd, -1);
    validFdOrException();
    this.address = socketAddress.getAddress();
    this.port = socketAddress.getPort();
    this.localport = 0;
    this.connected = true;
  }

  @Override
  protected void create(boolean stream) throws IOException {
    if (isClosed()) {
      throw new SocketException("Already closed");
    }
    NativeUnixSocket.createSocket(fd, stream ? NativeUnixSocket.SOCK_STREAM
        : NativeUnixSocket.SOCK_DGRAM);
  }

  @Override
  protected InputStream getInputStream() throws IOException {
    if (!connected && !isBound()) {
      throw new IOException("Not connected/not bound");
    }
    validFdOrException();
    return in;
  }

  @Override
  protected OutputStream getOutputStream() throws IOException {
    if (!connected && !isBound()) {
      throw new IOException("Not connected/not bound");
    }
    validFdOrException();
    return out;
  }

  @Override
  protected void listen(int backlog) throws IOException {
    FileDescriptor fdesc = validFdOrException();
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
      FileDescriptor fdesc = validFdOrException();
      if (len == 0) {
        return 0;
      } else if (off < 0 || len < 0 || (len > buf.length - off)) {
        throw new IndexOutOfBoundsException();
      }

      return NativeUnixSocket.read(AFUNIXSocketImpl.this, fdesc, buf, off, len,
          ancillaryReceiveBuffer);
    }

    @Override
    public int read() throws IOException {
      FileDescriptor fdesc = validFdOrException();

      if (eofReached) {
        return -1;
      }
      int byteRead = NativeUnixSocket.read(AFUNIXSocketImpl.this, fdesc, null, 0, 1,
          ancillaryReceiveBuffer);
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
      FileDescriptor fdesc = validFdOrException();

      int written;
      do {
        written = NativeUnixSocket.write(AFUNIXSocketImpl.this, fdesc, null, oneByte, 1,
            pendingFileDescriptors);
        if (written != 0) {
          break;
        }
      } while (checkWriteInterruptedException(0));
      pendingFileDescriptors = null;
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
      if (len == 0) {
        return;
      }

      int writtenTotal = 0;

      do {
        final int written = NativeUnixSocket.write(AFUNIXSocketImpl.this, fdesc, buf, off, len,
            pendingFileDescriptors);
        if (written < 0) {
          throw new IOException("Unspecific error while writing");
        } else if (written > 0) {
          pendingFileDescriptors = null;
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
    if (isClosed()) {
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
    return super.toString() + "[fd=" + fd + "; addr=" + this.cleanableState.socketAddress
        + "; connected=" + connected + "; bound=" + bound + "]";
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

    FileDescriptor fdesc = validFdOrException();
    try {
      switch (optID) {
        case SocketOptions.SO_KEEPALIVE:
        case SocketOptions.TCP_NODELAY:
          return NativeUnixSocket.getSocketOptionInt(fdesc, optID) != 0 ? true : false;
        case SocketOptions.SO_TIMEOUT:
          return Math.max(timeout, Math.max(NativeUnixSocket.getSocketOptionInt(fdesc, 0x1005),
              NativeUnixSocket.getSocketOptionInt(fdesc, 0x1006)));
        case SocketOptions.SO_LINGER:
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
          return NativeUnixSocket.getSocketOptionInt(fdesc, optID);
        case SocketOptions.IP_TOS:
          return 0;
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
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
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
        case SocketOptions.SO_TIMEOUT:
          this.timeout = expectInteger(value);
          NativeUnixSocket.setSocketOptionInt(fdesc, 0x1005, timeout);
          NativeUnixSocket.setSocketOptionInt(fdesc, 0x1006, timeout);
          return;
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
          NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectInteger(value));
          return;
        case SocketOptions.SO_KEEPALIVE:
        case SocketOptions.TCP_NODELAY:
          NativeUnixSocket.setSocketOptionInt(fdesc, optID, expectBoolean(value));
          return;
        case SocketOptions.IP_TOS:
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
    protected Lenient() throws IOException {
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

  AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return NativeUnixSocket.peerCredentials(fd, new AFUNIXSocketCredentials());
  }

  int getAncillaryReceiveBufferSize() {
    return ancillaryReceiveBuffer.capacity();
  }

  void setAncillaryReceiveBufferSize(int size) {
    if (size == ancillaryReceiveBuffer.capacity()) {
      return;
    }
    this.ancillaryReceiveBuffer = size <= 0 ? EMPTY_BUFFER : ByteBuffer.allocateDirect(size);
  }

  public final void ensureAncillaryReceiveBufferSize(int minSize) {
    if (minSize <= 0) {
      return;
    }
    if (ancillaryReceiveBuffer.capacity() < minSize) {
      setAncillaryReceiveBufferSize(minSize);
    }
  }

  public final FileDescriptor[] getReceivedFileDescriptors() {
    if (receivedFileDescriptors.isEmpty()) {
      return null;
    }
    List<FileDescriptor[]> copy = new ArrayList<>(receivedFileDescriptors);
    if (copy.isEmpty()) {
      return null;
    }
    receivedFileDescriptors.removeAll(copy);
    int count = 0;
    for (FileDescriptor[] fds : copy) {
      count += fds.length;
    }
    if (count == 0) {
      return null;
    }
    FileDescriptor[] oneArray = new FileDescriptor[count];
    int offset = 0;
    for (FileDescriptor[] fds : copy) {
      System.arraycopy(fds, 0, oneArray, offset, fds.length);
      offset += fds.length;
    }
    return oneArray;
  }

  public final void clearReceivedFileDescriptors() {
    receivedFileDescriptors.clear();
  }

  // called from native code
  final void receiveFileDescriptors(int[] fds) throws IOException {
    if (fds == null || fds.length == 0) {
      return;
    }
    final int fdsLength = fds.length;
    FileDescriptor[] descriptors = new FileDescriptor[fdsLength];
    for (int i = 0; i < fdsLength; i++) {
      final FileDescriptor fdesc = new FileDescriptor();
      NativeUnixSocket.initFD(fdesc, fds[i]);
      descriptors[i] = fdesc;

      cleanableState.openReceivedFileDescriptors.put(fdesc, fds[i]);

      @SuppressWarnings("resource")
      final Closeable cleanup = new Closeable() {

        @Override
        public void close() throws IOException {
          cleanableState.openReceivedFileDescriptors.remove(fdesc);
        }
      };
      NativeUnixSocket.attachCloseable(fdesc, cleanup);
    }

    this.receivedFileDescriptors.add(descriptors);
  }

  // called from native code, too (but only with null)
  final void setOutboundFileDescriptors(int... fds) {
    this.pendingFileDescriptors = (fds == null || fds.length == 0) ? null : fds;
  }

  public final void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    final int[] fds;
    if (fdescs == null || fdescs.length == 0) {
      fds = null;
    } else {
      final int numFdescs = fdescs.length;
      fds = new int[numFdescs];
      for (int i = 0; i < numFdescs; i++) {
        FileDescriptor fdesc = fdescs[i];
        fds[i] = NativeUnixSocket.getFD(fdesc);
      }
    }
    this.setOutboundFileDescriptors(fds);
  }
}
