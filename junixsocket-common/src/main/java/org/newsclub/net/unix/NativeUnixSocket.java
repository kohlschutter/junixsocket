/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
import java.nio.ByteBuffer;

/**
 * JNI connector to native JNI C code.
 * 
 * @author Christian Kohlschütter
 */
final class NativeUnixSocket {
  private static boolean loaded = false;

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

  static native long bind(final byte[] socketAddr, final FileDescriptor fd, final int options)
      throws IOException;

  static native void listen(final FileDescriptor fd, final int backlog) throws IOException;

  static native void accept(final byte[] socketAddr, final FileDescriptor fdServer,
      final FileDescriptor fd, long inode, int timeout) throws IOException;

  static native void connect(final byte[] socketAddr, final FileDescriptor fd, long inode)
      throws IOException;

  static native int read(AFUNIXSocketImpl afunixSocketImpl, final FileDescriptor fd, byte[] buf,
      int off, int len, ByteBuffer ancillaryReceiveBuffer) throws IOException;

  static native int write(AFUNIXSocketImpl afunixSocketImpl, final FileDescriptor fd, byte[] buf,
      int off, int len, int[] fileDescriptors) throws IOException;

  static native void close(final FileDescriptor fd) throws IOException;

  static native void shutdown(final FileDescriptor fd, int mode) throws IOException;

  static native int getSocketOptionInt(final FileDescriptor fd, int optionId) throws IOException;

  static native void setSocketOptionInt(final FileDescriptor fd, int optionId, int value)
      throws IOException;

  static native int available(final FileDescriptor fd) throws IOException;

  static native AFUNIXSocketCredentials peerCredentials(FileDescriptor fd,
      AFUNIXSocketCredentials creds) throws IOException;

  static native void initServerImpl(final AFUNIXServerSocket serverSocket,
      final AFUNIXSocketImpl impl) throws IOException;

  static native void setCreated(final AFUNIXSocket socket);

  static native void setConnected(final AFUNIXSocket socket);

  static native void setBound(final AFUNIXSocket socket);

  static native void setCreatedServer(final AFUNIXServerSocket socket);

  static native void setBoundServer(final AFUNIXServerSocket socket);

  static native void setPort(final AFUNIXSocketAddress addr, int port);

  static native void initFD(FileDescriptor fdesc, int fd) throws IOException;

  static native int getFD(FileDescriptor fdesc) throws IOException;

  static native void attachCloseable(FileDescriptor fdsec, Closeable closeable);

  static native int maxAddressLength();

  static void setPort1(AFUNIXSocketAddress addr, int port) throws IOException {
    if (port < 0) {
      throw new IllegalArgumentException("port out of range:" + port);
    }

    try {
      setPort(addr, port);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Could not set port", e);
    }
  }
}
