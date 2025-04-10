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

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.newsclub.net.unix.pool.ObjectPool.Lease;

/**
 * A shared core that is common for all AF* sockets (datagrams, streams).
 *
 * @author Christian Kohlschütter
 */
class AFSocketCore extends AFCore {
  private final AtomicInteger pendingAccepts = new AtomicInteger(0);
  private static final int SHUT_RD_WR = 2;

  /**
   * We keep track of the server's inode to detect when another server connects to our address.
   */
  final AtomicLong inode = new AtomicLong(-1);

  AFSocketAddress socketAddress;

  private final AFAddressFamily<?> af;
  private final AtomicBoolean shutdownOnClose = new AtomicBoolean(true);

  protected AFSocketCore(Object observed, FileDescriptor fd,
      AncillaryDataSupport ancillaryDataSupport, AFAddressFamily<?> af, boolean datagramMode) {
    super(observed, fd, ancillaryDataSupport, datagramMode);
    this.af = af;
  }

  protected AFAddressFamily<?> addressFamily() {
    return af;
  }

  @Override
  @SuppressWarnings("UnsafeFinalization" /* errorprone */)
  protected void doClose() throws IOException {
    if (isShutdownOnClose()) {
      NativeUnixSocket.shutdown(fd, SHUT_RD_WR);
      unblockAccepts();
    }

    super.doClose();
  }

  protected void unblockAccepts() {
    // see AFSocketImpl
  }

  AFSocketAddress receive(ByteBuffer dst, AFSupplier<Integer> socketTimeout) throws IOException {
    try (Lease<ByteBuffer> socketAddressBufferLease = AFSocketAddress.SOCKETADDRESS_BUFFER_TL
        .take()) {
      ByteBuffer socketAddressBuffer = socketAddressBufferLease.get();

      int read = read(dst, socketTimeout, socketAddressBuffer, 0);
      if (read > 0) {
        return AFSocketAddress.ofInternal(socketAddressBuffer, af);
      } else {
        return null;
      }
    }
  }

  boolean isConnected(boolean boundOk) {
    try {
      if (fd.valid()) {
        switch (NativeUnixSocket.socketStatus(fd)) {
          case NativeUnixSocket.SOCKETSTATUS_CONNECTED:
            return true;
          case NativeUnixSocket.SOCKETSTATUS_BOUND:
            if (boundOk) {
              return true;
            }
            break;
          default:
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return false;
  }

  @SuppressWarnings({"unchecked"})
  <T> T getOption(AFSocketOption<T> name) throws IOException {
    Class<T> type = name.type();
    if (Boolean.class.isAssignableFrom(type)) {
      return (T) (Object) (NativeUnixSocket.getSocketOption(fd, name.level(), name.optionName(),
          Integer.class) != 0);
    } else if (NamedInteger.HasOfValue.class.isAssignableFrom(type)) {
      @SuppressWarnings("all") // "null" creates another warning
      int v = NativeUnixSocket.getSocketOption(fd, name.level(), name.optionName(), Integer.class);
      try {
        return (T) type.getMethod("ofValue", int.class).invoke(null, v);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
          | NoSuchMethodException | SecurityException e) {
        throw new IOException("Value casting problem", e);
      }
    } else {
      return NativeUnixSocket.getSocketOption(fd, name.level(), name.optionName(), type);
    }
  }

  <T> void setOption(AFSocketOption<T> name, T value) throws IOException {
    final Object val;
    if (value instanceof Boolean) {
      val = (((Boolean) value) ? 1 : 0);
    } else if (value instanceof NamedInteger) {
      val = ((NamedInteger) value).value();
    } else {
      val = value;
    }
    int level = name.level();
    int optionName = name.optionName();
    NativeUnixSocket.setSocketOption(fd, level, optionName, val);
    if (level == 271 && optionName == 135) {
      // AFTIPCSocketOptions.TIPC_GROUP_JOIN
      // unclear why, but sleeping for at least 1ms prevents issues with GROUP_JOIN
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        // ignore
      }
    }
  }

  protected void incPendingAccepts() throws SocketException {
    if (pendingAccepts.incrementAndGet() >= Integer.MAX_VALUE) {
      pendingAccepts.decrementAndGet();
      throw new SocketException("Too many pending accepts");
    }
  }

  protected void decPendingAccepts() {
    pendingAccepts.decrementAndGet();
  }

  protected boolean hasPendingAccepts() {
    return pendingAccepts.get() > 0;
  }

  boolean isShutdownOnClose() {
    return shutdownOnClose.get();
  }

  void setShutdownOnClose(boolean enabled) {
    this.shutdownOnClose.set(enabled);
  }
}
