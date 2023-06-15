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
package org.newsclub.net.unix.vsock;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.newsclub.net.unix.AFVSOCKSocketImplExtensions;

/**
 * Implementation of an {@code AF_VSOCK} socket.
 *
 * @author Christian Kohlschütter
 */
public final class AFVSOCKSocket extends AFSocket<AFVSOCKSocketAddress> implements
    AFVSOCKSocketExtensions {
  private static AFVSOCKSocketImplExtensions staticExtensions = null;

  AFVSOCKSocket(FileDescriptor fdObj, AFSocketFactory<AFVSOCKSocketAddress> factory)
      throws SocketException {
    super(new AFVSOCKSocketImpl(fdObj), factory);
  }

  private static synchronized AFVSOCKSocketImplExtensions getStaticImplExtensions()
      throws IOException {
    if (staticExtensions == null) {
      try (AFVSOCKSocket socket = new AFVSOCKSocket(null, null)) {
        staticExtensions = (AFVSOCKSocketImplExtensions) socket.getImplExtensions();
      }
    }
    return staticExtensions;
  }

  /**
   * Returns <code>true</code> iff {@link AFVSOCKSocket}s (sockets of type "AF_VSOCK") are supported
   * by the current Java VM and the kernel.
   *
   * To support {@link AFVSOCKSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>, and the system must support AF_VSOCK sockets.
   *
   * This call is equivalent to checking {@link AFSocket#isSupported()} and
   * {@link AFSocket#supports(AFSocketCapability)} with {@link AFSocketCapability#CAPABILITY_VSOCK}.
   *
   * @return {@code true} iff supported.
   */
  public static boolean isSupported() {
    return AFSocket.isSupported() && AFSocket.supports(AFSocketCapability.CAPABILITY_VSOCK);
  }

  @Override
  protected AFVSOCKSocketChannel newChannel() {
    return new AFVSOCKSocketChannel(this);
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
  public static AFVSOCKSocket newInstance() throws IOException {
    return (AFVSOCKSocket) AFSocket.newInstance(AFVSOCKSocket::new, (AFVSOCKSocketFactory) null);
  }

  static AFVSOCKSocket newInstance(AFVSOCKSocketFactory factory) throws SocketException {
    return (AFVSOCKSocket) AFSocket.newInstance(AFVSOCKSocket::new, factory);
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
  public static AFVSOCKSocket newStrictInstance() throws IOException {
    return (AFVSOCKSocket) AFSocket.newInstance(AFVSOCKSocket::new, (AFVSOCKSocketFactory) null);
  }

  /**
   * Creates a new {@link AFSocket} and connects it to the given {@link AFVSOCKSocketAddress}.
   *
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static AFVSOCKSocket connectTo(AFVSOCKSocketAddress addr) throws IOException {
    return (AFVSOCKSocket) AFSocket.connectTo(AFVSOCKSocket::new, addr);
  }

  @Override
  public AFVSOCKSocketChannel getChannel() {
    return (AFVSOCKSocketChannel) super.getChannel();
  }

  /**
   * Returns the local CID.
   *
   * If the system does not support vsock, or status about support cannot be retrieved, -1
   * ({@link AFVSOCKSocketAddress#VMADDR_CID_ANY}) is returned. The value may be cached upon
   * initialization of the library.
   *
   * @return The CID, or -1.
   * @throws IOException on error.
   */
  public static int getLocalCID() throws IOException {
    return getStaticImplExtensions().getLocalCID();
  }

  /**
   * Very basic self-test function.
   *
   * Prints "supported" and "capabilities" status to System.out.
   *
   * @param args ignored.
   */
  public static void main(String[] args) {
    // If you want to run this directly from within Eclipse, see
    // org.newsclub.net.unix.vsock.SocketTest#testMain.
    System.out.print(AFVSOCKSocket.class.getName() + ".isSupported(): ");
    System.out.flush();
    System.out.println(AFVSOCKSocket.isSupported());
  }
}
