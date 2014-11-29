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

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;

/**
 * JNI connector to native JNI C code.
 * 
 * @author Christian Kohlschütter
 */
final class NativeUnixSocket {
  private static boolean loaded = false;

  static {
    try {
      Class.forName("org.newsclub.net.unix.NarSystem").getMethod("loadLibrary").invoke(null);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Could not find NarSystem class.\n\n*** ECLIPSE USERS ***\nIf you're running from "
              + "within Eclipse, please try closing the \"junixsocket-native-common\" "
              + "project\n", e);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    loaded = true;
  }

  static boolean isLoaded() {
    return loaded;
  }

  static void checkSupported() {
  }

  static native void bind(final String socketFile, final FileDescriptor fd, final int backlog)
      throws IOException;

  static native void listen(final FileDescriptor fd, final int backlog) throws IOException;

  static native void accept(final String socketFile, final FileDescriptor fdServer,
      final FileDescriptor fd) throws IOException;

  static native void connect(final String socketFile, final FileDescriptor fd) throws IOException;

  static native int read(final FileDescriptor fd, byte[] buf, int off, int len) throws IOException;

  static native int write(final FileDescriptor fd, byte[] buf, int off, int len) throws IOException;

  static native void close(final FileDescriptor fd) throws IOException;

  static native void shutdown(final FileDescriptor fd, int mode) throws IOException;

  static native int getSocketOptionInt(final FileDescriptor fd, int optionId) throws IOException;

  static native void setSocketOptionInt(final FileDescriptor fd, int optionId, int value)
      throws IOException;

  static native void unlink(final String socketFile) throws IOException;

  static native int available(final FileDescriptor fd) throws IOException;

  static native void initServerImpl(final AFUNIXServerSocket serverSocket,
      final AFUNIXSocketImpl impl);

  static native void setCreated(final AFUNIXSocket socket);

  static native void setConnected(final AFUNIXSocket socket);

  static native void setBound(final AFUNIXSocket socket);

  static native void setCreatedServer(final AFUNIXServerSocket socket);

  static native void setBoundServer(final AFUNIXServerSocket socket);

  static native void setPort(final AFUNIXSocketAddress addr, int port);

  static void setPort1(AFUNIXSocketAddress addr, int port) throws AFUNIXSocketException {
    if (port < 0) {
      throw new IllegalArgumentException("port out of range:" + port);
    }

    boolean setOk = false;
    try {
      final Field holderField = InetSocketAddress.class.getDeclaredField("holder");
      if (holderField != null) {
        holderField.setAccessible(true);

        final Object holder = holderField.get(addr);
        if (holder != null) {
          final Field portField = holder.getClass().getDeclaredField("port");
          if (portField != null) {
            portField.setAccessible(true);
            portField.set(holder, port);
            setOk = true;
          }
        }
      } else {
        setPort(addr, port);
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      if (e instanceof AFUNIXSocketException) {
        throw (AFUNIXSocketException) e;
      }
      throw new AFUNIXSocketException("Could not set port", e);
    }
    if (!setOk) {
      throw new AFUNIXSocketException("Could not set port");
    }
  }
}
