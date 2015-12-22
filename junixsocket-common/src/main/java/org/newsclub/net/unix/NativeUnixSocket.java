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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
/**
 * JNI connector to native JNI C code.
 * 
 * @author Christian Kohlschütter
 */
final class NativeUnixSocket {
  private static native void registerNatives();
  private static boolean loaded = false;
  private static Method getMethod(Class<?> klass, String name, Class<?>...parameterTypes) {
    Method method = null;
    try {
      method = klass.getDeclaredMethod(name, parameterTypes);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (method == null) {
      throw new RuntimeException("Cannot find method \"" + name + "\" in "
          + klass.getName() + "\". Unsupported JVM?");
    }
    method.setAccessible(true);
    return method;
  }

  private static Field getField(Class<?> klass, String name) {
    Field field = null;
    try {
      field = klass.getDeclaredField(name);
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (field == null) {
      throw new RuntimeException("Cannot find field \"" + name + "\" in "
          + klass.getName() + "\". Unsupported JVM?");
    }
    field.setAccessible(true);
	  return field;
  }

  static {
    try {
      Class.forName("org.newsclub.net.unix.NarSystem").getMethod("loadLibrary").invoke(null);
      registerNatives();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new IllegalStateException(
          "Could not find NarSystem class.\n\n*** ECLIPSE USERS ***\nIf you're running from "
              + "within Eclipse, please try closing the \"junixsocket-native-common\" "
              + "project\n", e);
    } catch (Exception e) {
      e.printStackTrace();
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

  static native int write(final FileDescriptor fd, final byte[] buf, final int off, final int len) throws IOException;

  static native void close(final FileDescriptor fd) throws IOException;

  static native void shutdown(final FileDescriptor fd, int mode) throws IOException;

  static native int getSocketOptionInt(final FileDescriptor fd, int optionId) throws IOException;

  static native void setSocketOptionInt(final FileDescriptor fd, int optionId, int value)
      throws IOException;

  static native void unlink(final String socketFile) throws IOException;

  static native int available(final FileDescriptor fd) throws IOException;

  private static final Field socketImpl = getField(ServerSocket.class, "impl");
  static void initServerImpl(final AFUNIXServerSocket addr, final AFUNIXSocketImpl impl) {
    try {
      socketImpl.set(addr, impl);
    } catch (final IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
 
  private static final Method setCreated = getMethod(Socket.class, "setCreated");
  static void setCreated(final AFUNIXSocket socket) {
    try {
      setCreated.invoke(socket);
    } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  private static final Method setConnected = getMethod(Socket.class, "setConnected");
  static void setConnected(final AFUNIXSocket socket) {
    try {
      setConnected.invoke(socket);
    } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  private static final Method setBound = getMethod(Socket.class, "setBound");
  static void setBound(final AFUNIXSocket socket) {
    try {
      setBound.invoke(socket);
    } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  private static final Method setCreatedServer = getMethod(ServerSocket.class, "setCreated");
  static void setCreatedServer(final AFUNIXServerSocket socket) {
    try {
      setCreatedServer.invoke(socket);
    } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  private static final Method setBoundServer = getMethod(ServerSocket.class, "setBound");
  static void setBoundServer(final AFUNIXServerSocket socket) {
    try {
      setBoundServer.invoke(socket);
    } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new AssertionError(e);
    }
  }

  @Deprecated
  static void setPortBad(final AFUNIXSocketAddress addr, final int port) {
    try {
      portField.setInt(addr, port);
    } catch (final IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
  private static Field getHolderField() {
    Field field;
    try {
      field = InetSocketAddress.class.getDeclaredField("holder");
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException e) {
      return null;
    }
  }
  private static Field getPortField() {
    Field holderField = getHolderField();
    Class<?> portClass = holderField != null ? holderField.getType() : InetSocketAddress.class;
    return getField(portClass, "port");
  }
  private static final Field holderField = getHolderField();
  private static final Field portField = getPortField();
  static void setPort(final AFUNIXSocketAddress addr, final int port) throws AFUNIXSocketException {
    if (port < 0) {
      throw new IllegalArgumentException("port out of range:" + port);
    }

    try {
      portField.set(holderField != null ? holderField.get(addr) : addr, port);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      if (e instanceof AFUNIXSocketException) {
        throw (AFUNIXSocketException) e;
      }
      throw new AFUNIXSocketException("Could not set port", e);
    }
  }
}
