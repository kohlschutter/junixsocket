/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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
import java.lang.ProcessBuilder.Redirect;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.newsclub.net.unix.AFSelector.PollFd;

import com.kohlschutter.annotations.compiletime.ExcludeFromCodeCoverageGeneratedReport;
import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * JNI connector to native JNI C code.
 *
 * @author Christian Kohlschütter
 */
final class NativeUnixSocket {
  private static final CompletableFuture<Boolean> LOADED = new CompletableFuture<>();

  static final int DOMAIN_UNIX = 1;
  static final int DOMAIN_TIPC = 30;
  static final int DOMAIN_VSOCK = 40;
  static final int DOMAIN_SYSTEM = 32;

  static final int SOCK_STREAM = 1;
  static final int SOCK_DGRAM = 2;
  static final int SOCK_RAW = 3;
  static final int SOCK_RDM = 4;
  static final int SOCK_SEQPACKET = 5;

  static final int OPT_LOOKUP_SENDER = 1;
  static final int OPT_PEEK = 2;
  static final int OPT_NON_BLOCKING = 4;

  /**
   * Indicator that the handle isn't referring to a socket but some other file descriptor.
   */
  static final int OPT_NON_SOCKET = 8;

  static final int OPT_DGRAM_MODE = 16;

  static final int BIND_OPT_REUSE = 1;

  static final int SOCKETSTATUS_INVALID = -1;
  static final int SOCKETSTATUS_UNKNOWN = 0;
  static final int SOCKETSTATUS_BOUND = 1;
  static final int SOCKETSTATUS_CONNECTED = 2;

  private static Throwable initError = null;

  @ExcludeFromCodeCoverageGeneratedReport(reason = "unreachable")
  private NativeUnixSocket() {
    throw new UnsupportedOperationException("No instances");
  }

  static {
    boolean loadSuccessful = false;
    try (NativeLibraryLoader nll = new NativeLibraryLoader()) {
      nll.loadLibrary();
      loadSuccessful = true;
    } catch (RuntimeException | Error e) {
      initError = e;
      e.printStackTrace(); // keep
    } finally {
      setLoaded(loadSuccessful);
    }

    AFAddressFamily.registerAddressFamily("un", NativeUnixSocket.DOMAIN_UNIX,
        "org.newsclub.net.unix.AFUNIXSocketAddress");
    AFAddressFamily.registerAddressFamily("tipc", NativeUnixSocket.DOMAIN_TIPC,
        "org.newsclub.net.unix.AFTIPCSocketAddress");
    AFAddressFamily.registerAddressFamily("vsock", NativeUnixSocket.DOMAIN_VSOCK,
        "org.newsclub.net.unix.AFVSOCKSocketAddress");
    AFAddressFamily.registerAddressFamily("system", NativeUnixSocket.DOMAIN_SYSTEM,
        "org.newsclub.net.unix.AFSYSTEMSocketAddress");
  }

  static boolean isLoaded() {
    boolean loadSuccessful;
    try {
      loadSuccessful = LOADED.get();
    } catch (InterruptedException | ExecutionException e) {
      loadSuccessful = false;
    }
    return loadSuccessful;
  }

  static void ensureSupported() throws UnsupportedOperationException {
    if (!isLoaded()) {
      throw unsupportedException();
    }
  }

  static UnsupportedOperationException unsupportedException() {
    if (!isLoaded()) {
      return (UnsupportedOperationException) new UnsupportedOperationException(
          "junixsocket may not be fully supported on this platform").initCause(initError);
    } else {
      return null;
    }
  }

  static Throwable retrieveInitError() {
    return initError;
  }

  static void initPre() {
    // in some environments, JNI FindClass won't find these classes unless we resolve them first
    tryResolveClass(AbstractSelectableChannel.class.getName());
    tryResolveClass("java.lang.ProcessBuilder$RedirectPipeImpl");
    tryResolveClass(InetSocketAddress.class.getName());
    tryResolveClass(OperationNotSupportedSocketException.class.getName());
  }

  private static void tryResolveClass(String className) {
    try {
      Class.forName(className);
    } catch (Exception e) {
      // ignore
    }
  }

  @SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
  static native void init() throws Exception;

  @SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
  static native void destroy() throws Exception;

  /**
   * Can be used to check (without side-effects) if the library has been loaded.
   *
   * Terminates normally if so; throws {@link UnsatisfiedLinkError} if not.
   */
  static native void noop();

  static native int capabilities();

  static native byte[] sockname(int domain, FileDescriptor fd, boolean peer);

  static native long bind(ByteBuffer sockaddr, int sockaddrLen, FileDescriptor fd, int options)
      throws IOException;

  static native void listen(FileDescriptor fd, int backlog) throws IOException;

  static native boolean accept(ByteBuffer sockaddr, int sockaddrLen, FileDescriptor fdServer,
      FileDescriptor fd, long inode, int timeout) throws IOException;

  static native boolean connect(ByteBuffer sockaddr, int sockaddrLen, FileDescriptor fd, long inode)
      throws IOException;

  static native boolean finishConnect(FileDescriptor fd) throws IOException;

  static native void disconnect(FileDescriptor fd) throws IOException;

  static native int socketStatus(FileDescriptor fd) throws IOException;

  static native Class<?> primaryType(FileDescriptor fd) throws IOException;

  /**
   * Reads data from an {@link AFSocketImpl}.
   *
   * @param fd The corresponding file descriptor.
   * @param buf The buffer to read into, or {@code null} if a single byte should be read.
   * @param off The buffer offset.
   * @param len The maximum number of bytes to read. Must be 1 if {@code buf} is {@code null}.
   * @param options Options.
   * @param ancillaryDataSupport The ancillary data support instance, or {@code null}.
   * @return The number of bytes read, -1 if nothing could be read, or the byte itself iff
   *         {@code buf} was {@code null}.
   * @throws IOException upon error.
   */
  static native int read(FileDescriptor fd, byte[] buf, int off, int len, int options,
      AncillaryDataSupport ancillaryDataSupport, int timeoutMillis) throws IOException;

  /**
   * Writes data to an {@link AFSocketImpl}.
   *
   * @param fd The corresponding file descriptor.
   * @param buf The buffer to write from, or {@code null} if a single byte should be written.
   * @param off The buffer offset, or the byte to write if {@code buf} is {@code null}.
   * @param len The number of bytes to write. Must be 1 if {@code buf} is {@code null}.
   * @param options Options.
   * @param ancillaryDataSupport The ancillary data support instance, or {@code null}.
   * @return The number of bytes written (which could be 0).
   * @throws IOException upon error.
   */
  static native int write(FileDescriptor fd, byte[] buf, int off, int len, int options,
      AncillaryDataSupport ancillaryDataSupport) throws IOException;

  static native int receive(FileDescriptor fd, ByteBuffer directBuffer, int offset, int length,
      ByteBuffer directSocketAddressOut, int options, AncillaryDataSupport ancillaryDataSupport,
      int timeoutMillis) throws IOException;

  static native int send(FileDescriptor fd, ByteBuffer directBuffer, int offset, int length,
      ByteBuffer directSocketAddress, int addrLen, int options,
      AncillaryDataSupport ancillaryDataSupport) throws IOException;

  static native void close(FileDescriptor fd) throws IOException;

  static native void shutdown(FileDescriptor fd, int mode) throws IOException;

  static native int getSocketOptionInt(FileDescriptor fd, int optionId) throws IOException;

  static native void setSocketOptionInt(FileDescriptor fd, int optionId, int value)
      throws IOException;

  static native <T> T getSocketOption(FileDescriptor fd, int level, int optionName,
      Class<T> valueType) throws IOException;

  static native void setSocketOption(FileDescriptor fd, int level, int optionName, Object value)
      throws IOException;

  static native int available(FileDescriptor fd, ByteBuffer buf) throws IOException;

  static native AFUNIXSocketCredentials peerCredentials(FileDescriptor fd,
      AFUNIXSocketCredentials creds) throws IOException;

  static native void initServerImpl(ServerSocket serverSocket, AFSocketImpl<?> impl)
      throws IOException;

  static native void createSocket(FileDescriptor fdesc, int domain, int type) throws IOException;

  static native void setPort(SocketAddress addr, int port);

  static native void initFD(FileDescriptor fdesc, int fd) throws IOException;

  static native int getFD(FileDescriptor fdesc) throws IOException;

  static native void copyFileDescriptor(FileDescriptor source, FileDescriptor target)
      throws IOException;

  static native void attachCloseable(FileDescriptor fdsec, Closeable closeable)
      throws SocketException;

  static native int maxAddressLength();

  static native int sockAddrLength(int domain);

  static native int ancillaryBufMinLen();

  static native byte[] sockAddrToBytes(int domain, ByteBuffer sockAddrDirect);

  static native int bytesToSockAddr(int domain, ByteBuffer sockAddrDirectBuf, byte[] address);

  @SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
  static void setPort1(SocketAddress addr, int port) throws SocketException {
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

  static native int poll(PollFd pollFd, int timeout) throws IOException;

  static native void configureBlocking(FileDescriptor fd, boolean blocking) throws IOException;

  static native void socketPair(int domain, int type, FileDescriptor fd, FileDescriptor fd2);

  static native Redirect initRedirect(FileDescriptor fd);

  static native void deregisterSelectionKey(AbstractSelectableChannel chann, SelectionKey key);

  static native byte[] tipcGetNodeId(int peer) throws IOException;

  static native byte[] tipcGetLinkName(int peer, int bearerId) throws IOException;

  static native int sockAddrNativeDataOffset();

  static native int sockAddrNativeFamilyOffset();

  static native int sockTypeToNative(int type) throws IOException;

  static native int vsockGetLocalCID() throws IOException;

  static native int systemResolveCtlId(FileDescriptor fd, String ctlName) throws IOException;

  static void setLoaded(boolean successful) {
    LOADED.complete(successful);
  }
}
