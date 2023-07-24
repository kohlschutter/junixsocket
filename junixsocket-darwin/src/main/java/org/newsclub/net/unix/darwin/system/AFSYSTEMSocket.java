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
package org.newsclub.net.unix.darwin.system;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.newsclub.net.unix.AFSYSTEMSocketAddress;
import org.newsclub.net.unix.AFSYSTEMSocketImplExtensions;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketFactory;

/**
 * Implementation of an {@code AF_SYSTEM} socket.
 *
 * @author Christian Kohlschütter
 */
public final class AFSYSTEMSocket extends AFSocket<AFSYSTEMSocketAddress> implements
    AFSYSTEMSocketExtensions {
  private static AFSYSTEMSocketImplExtensions staticExtensions = null;

  AFSYSTEMSocket(FileDescriptor fdObj, AFSocketFactory<AFSYSTEMSocketAddress> factory)
      throws SocketException {
    super(new AFSYSTEMSocketImpl(fdObj), factory);
  }

  @SuppressWarnings("unused")
  private static synchronized AFSYSTEMSocketImplExtensions getStaticImplExtensions()
      throws IOException {
    if (staticExtensions == null) {
      try (AFSYSTEMSocket socket = new AFSYSTEMSocket(null, null)) {
        staticExtensions = (AFSYSTEMSocketImplExtensions) socket.getImplExtensions();
      }
    }
    return staticExtensions;
  }

  /**
   * Returns <code>true</code> iff {@link AFSYSTEMSocket}s (sockets of type "AF_SYSTEM") are
   * supported by the current Java VM and the kernel.
   *
   * To support {@link AFSYSTEMSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>, and the system must support AF_SYSTEM sockets.
   *
   * This call is equivalent to checking {@link AFSocket#isSupported()} and
   * {@link AFSocket#supports(AFSocketCapability)} with
   * {@link AFSocketCapability#CAPABILITY_DARWIN}.
   *
   * @return {@code true} iff supported.
   */
  public static boolean isSupported() {
    return AFSocket.isSupported() && AFSocket.supports(AFSocketCapability.CAPABILITY_DARWIN);
  }

  @Override
  protected AFSYSTEMSocketChannel newChannel() {
    return new AFSYSTEMSocketChannel(this);
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
  public static AFSYSTEMSocket newInstance() throws IOException {
    return (AFSYSTEMSocket) AFSocket.newInstance(AFSYSTEMSocket::new, (AFSYSTEMSocketFactory) null);
  }

  static AFSYSTEMSocket newInstance(AFSYSTEMSocketFactory factory) throws SocketException {
    return (AFSYSTEMSocket) AFSocket.newInstance(AFSYSTEMSocket::new, factory);
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
  public static AFSYSTEMSocket newStrictInstance() throws IOException {
    return (AFSYSTEMSocket) AFSocket.newInstance(AFSYSTEMSocket::new, (AFSYSTEMSocketFactory) null);
  }

  /**
   * Creates a new {@link AFSocket} and connects it to the given {@link AFSYSTEMSocketAddress}.
   *
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static AFSYSTEMSocket connectTo(AFSYSTEMSocketAddress addr) throws IOException {
    return (AFSYSTEMSocket) AFSocket.connectTo(AFSYSTEMSocket::new, addr);
  }

  @Override
  public AFSYSTEMSocketChannel getChannel() {
    return (AFSYSTEMSocketChannel) super.getChannel();
  }

  /**
   * Very basic self-test function.
   *
   * Prints "supported" and "capabilities" status to System.out.
   *
   * @param args ignored.
   */
  public static void main(String[] args) {
    System.out.print(AFSYSTEMSocket.class.getName() + ".isSupported(): ");
    System.out.flush();
    System.out.println(AFSYSTEMSocket.isSupported());
  }
}
