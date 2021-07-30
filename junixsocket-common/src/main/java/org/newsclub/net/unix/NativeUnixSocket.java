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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.newsclub.net.unix.AFUNIXSelector.PollFd;

import com.kohlschutter.annotations.compiletime.ExcludeFromCodeCoverageGeneratedReport;

/**
 * JNI connector to native JNI C code.
 * 
 * @author Christian Kohlschütter
 */
final class NativeUnixSocket {
  private static boolean loaded;

  static final int SOCK_STREAM = 1;
  static final int SOCK_DGRAM = 2;
  static final int SOCK_SEQPACKET = 5;

  static final int OPT_LOOKUP_SENDER = 1;
  static final int OPT_PEEK = 2;
  static final int OPT_NON_BLOCKING = 4;
  static final int OPT_NON_SOCKET = 8;

  static final int SOCKETSTATUS_INVALID = -1;
  static final int SOCKETSTATUS_UNKNOWN = 0;
  static final int SOCKETSTATUS_BOUND = 1;
  static final int SOCKETSTATUS_CONNECTED = 2;

  @ExcludeFromCodeCoverageGeneratedReport
  private NativeUnixSocket() {
    throw new UnsupportedOperationException("No instances");
  }

  static {
    try (NativeLibraryLoader nll = new NativeLibraryLoader()) {
      nll.loadLibrary();
    }
    loaded = true;
  }

  static boolean isLoaded() {
    return loaded;
  }

  static void checkSupported() {
  }

  static native void init() throws Exception;

  static native void destroy() throws Exception;

  static native int capabilities();

  static native byte[] sockname(FileDescriptor fd, boolean peer);

  static native long bind(final byte[] socketAddr, final FileDescriptor fd, final int options)
      throws IOException;

  static native void listen(final FileDescriptor fd, final int backlog) throws IOException;

  static native boolean accept(final byte[] socketAddr, final FileDescriptor fdServer,
      final FileDescriptor fd, long inode, int timeout) throws IOException;

  static native boolean connect(final byte[] socketAddr, final FileDescriptor fd, long inode)
      throws IOException;

  static native boolean finishConnect(FileDescriptor fd) throws IOException;

  static native void disconnect(final FileDescriptor fd) throws IOException;

  static native int socketStatus(final FileDescriptor fd) throws IOException;

  static native Class<?> primaryType(final FileDescriptor fd) throws IOException;

  /**
   * Reads data from an {@link AFUNIXSocketImpl}.
   * 
   * @param fd The corresponding file descriptor.
   * @param buf The buffer to read into, or {@code null} if a single byte should be read.
   * @param off The buffer offset.
   * @param len The maximum number of bytes to read. Must be 1 if {@code buf} is {@code null}.
   * @param ancillaryDataSupport The ancillary data support instance, or {@code null}.
   * @return The number of bytes read, -1 if nothing could be read, or the byte itself iff
   *         {@code buf} was {@code null}.
   * @throws IOException upon error.
   */
  static native int read(final FileDescriptor fd, byte[] buf, int off, int len,
      AncillaryDataSupport ancillaryDataSupport, int timeoutMillis) throws IOException;

  /**
   * Writes data to an {@link AFUNIXSocketImpl}.
   * 
   * @param fd The corresponding file descriptor.
   * @param buf The buffer to write from, or {@code null} if a single byte should be written.
   * @param off The buffer offset, or the byte to write if {@code buf} is {@code null}.
   * @param len The number of bytes to write. Must be 1 if {@code buf} is {@code null}.
   * @param ancillaryDataSupport The ancillary data support instance, or {@code null}.
   * @return The number of bytes written (which could be 0).
   * @throws IOException upon error.
   */
  static native int write(final FileDescriptor fd, byte[] buf, int off, int len,
      AncillaryDataSupport ancillaryDataSupport) throws IOException;

  static native int receive(final FileDescriptor fd, ByteBuffer directBuffer, int offset,
      int length, ByteBuffer directSocketAddressOut, int options,
      AncillaryDataSupport ancillaryDataSupport, int timeoutMillis) throws IOException;

  static native int send(final FileDescriptor fd, ByteBuffer directBuffer, int offset, int length,
      ByteBuffer directSocketAddress, int options, AncillaryDataSupport ancillaryDataSupport)
      throws IOException;

  static native void close(final FileDescriptor fd) throws IOException;

  static native void shutdown(final FileDescriptor fd, int mode) throws IOException;

  static native int getSocketOptionInt(final FileDescriptor fd, int optionId) throws IOException;

  static native void setSocketOptionInt(final FileDescriptor fd, int optionId, int value)
      throws IOException;

  static native int available(final FileDescriptor fd) throws IOException;

  static native AFUNIXSocketCredentials peerCredentials(FileDescriptor fd,
      AFUNIXSocketCredentials creds) throws IOException;

  static native void initServerImpl(final ServerSocket serverSocket, final AFUNIXSocketImpl impl)
      throws IOException;

  static native void createSocket(FileDescriptor fdesc, int type) throws IOException;

  static native void setPort(final AFUNIXSocketAddress addr, int port);

  static native void initFD(FileDescriptor fdesc, int fd) throws IOException;

  static native int getFD(FileDescriptor fdesc) throws IOException;

  static native void copyFileDescriptor(FileDescriptor source, FileDescriptor target)
      throws IOException;

  static native void attachCloseable(FileDescriptor fdsec, Closeable closeable);

  static native int maxAddressLength();

  static native int sockAddrUnLength();

  static native byte[] sockAddrUnToBytes(ByteBuffer sockAddrDirect);

  static native void bytesToSockAddrUn(ByteBuffer sockAddrDirectBuf, byte[] address);

  static void setPort1(AFUNIXSocketAddress addr, int port) throws SocketException {
    if (port < 0) {
      throw new IllegalArgumentException("port out of range:" + port);
    }

    try {
      setPort(addr, port);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw (SocketException) new SocketException("Could not set port").initCause(e);
    }
  }

  static native Socket currentRMISocket();

  static native boolean initPipe(FileDescriptor source, FileDescriptor sink, boolean selectable)
      throws IOException;

  static native int poll(PollFd pollFd, int timeout);

  static native void configureBlocking(FileDescriptor fd, boolean blocking) throws IOException;

  static native void socketPair(int type, FileDescriptor fd, FileDescriptor fd2);
}
