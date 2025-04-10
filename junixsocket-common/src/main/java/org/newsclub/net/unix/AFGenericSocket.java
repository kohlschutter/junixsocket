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
import java.net.Socket;
import java.net.SocketException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Implementation of an unknown-type socket.
 *
 * @author Christian Kohlschütter
 */
final class AFGenericSocket extends AFSocket<AFGenericSocketAddress> implements
    AFGenericSocketExtensions {
  private static AFGenericSocketImplExtensions staticExtensions = null;

  AFGenericSocket(FileDescriptor fdObj, AFSocketFactory<AFGenericSocketAddress> factory)
      throws SocketException {
    super(new AFGenericSocketImpl(fdObj), factory);
  }

  @SuppressWarnings("unused")
  private static synchronized AFGenericSocketImplExtensions getStaticImplExtensions()
      throws IOException {
    if (staticExtensions == null) {
      try (AFGenericSocket socket = new AFGenericSocket(null, null)) {
        staticExtensions = (AFGenericSocketImplExtensions) socket.getImplExtensions();
      }
    }
    return staticExtensions;
  }

  /**
   * Returns <code>true</code> iff {@link AFGenericSocket}s (sockets of unknown/otherwise
   * unsupported type) are supported by the current Java VM and the kernel.
   *
   * To support {@link AFGenericSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>.
   *
   * This call is equivalent to checking {@link AFSocket#isSupported()}.
   *
   * @return {@code true} iff supported.
   */
  @SuppressFBWarnings("HSM_HIDING_METHOD")
  public static boolean isSupported() {
    return AFSocket.isSupported();
  }

  @Override
  protected AFGenericSocketChannel newChannel() {
    return new AFGenericSocketChannel(this);
  }

  /**
   * Creates a new, unbound {@link AFSocket}.
   *
   * This "default" implementation is a bit "lenient" with respect to the specification.
   *
   * In particular, we ignore calls to {@link Socket#getTcpNoDelay()} and
   * {@link Socket#setTcpNoDelay(boolean)}.
   *
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFGenericSocket newInstance() throws IOException {
    return (AFGenericSocket) AFSocket.newInstance(AFGenericSocket::new,
        (AFGenericSocketFactory) null);
  }

  static AFGenericSocket newInstance(AFGenericSocketFactory factory) throws SocketException {
    return (AFGenericSocket) AFSocket.newInstance(AFGenericSocket::new, factory);
  }

  /**
   * Creates a new, unbound, "strict" {@link AFSocket}.
   *
   * This call uses an implementation that tries to be closer to the specification than
   * {@link #newInstance()}, at least for some cases.
   *
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFGenericSocket newStrictInstance() throws IOException {
    return (AFGenericSocket) AFSocket.newInstance(AFGenericSocket::new,
        (AFGenericSocketFactory) null);
  }

  /**
   * Creates a new {@link AFSocket} and connects it to the given {@link AFGenericSocketAddress}.
   *
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static AFGenericSocket connectTo(AFGenericSocketAddress addr) throws IOException {
    return (AFGenericSocket) AFSocket.connectTo(AFGenericSocket::new, addr);
  }

  @Override
  public AFGenericSocketChannel getChannel() {
    return (AFGenericSocketChannel) super.getChannel();
  }
}
