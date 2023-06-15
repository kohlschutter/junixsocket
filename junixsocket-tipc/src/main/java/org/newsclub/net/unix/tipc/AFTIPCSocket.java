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
package org.newsclub.net.unix.tipc;

import java.io.FileDescriptor;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketImplExtensions;

/**
 * Implementation of an {@code AF_TIPC} socket.
 *
 * @author Christian Kohlschütter
 */
public final class AFTIPCSocket extends AFSocket<AFTIPCSocketAddress> implements
    AFTIPCSocketExtensions {
  private static AFTIPCSocketImplExtensions staticExtensions = null;

  AFTIPCSocket(FileDescriptor fdObj, AFSocketFactory<AFTIPCSocketAddress> factory)
      throws SocketException {
    super(new AFTIPCSocketImpl(fdObj), factory);
  }

  private static synchronized AFTIPCSocketImplExtensions getStaticImplExtensions()
      throws IOException {
    if (staticExtensions == null) {
      try (AFTIPCSocket socket = new AFTIPCSocket(null, null)) {
        staticExtensions = (AFTIPCSocketImplExtensions) socket.getImplExtensions();
      }
    }
    return staticExtensions;
  }

  /**
   * Returns <code>true</code> iff {@link AFTIPCSocket}s (sockets of type "AF_TIPC") are supported
   * by the current Java VM and the kernel.
   *
   * To support {@link AFTIPCSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>, and the system must support AF_TIPC sockets.
   *
   * This call is equivalent to checking {@link AFSocket#isSupported()} and
   * {@link AFSocket#supports(AFSocketCapability)} with {@link AFSocketCapability#CAPABILITY_TIPC}.
   *
   * @return {@code true} iff supported.
   */
  public static boolean isSupported() {
    return AFSocket.isSupported() && AFSocket.supports(AFSocketCapability.CAPABILITY_TIPC);
  }

  @Override
  protected AFTIPCSocketChannel newChannel() {
    return new AFTIPCSocketChannel(this);
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
  public static AFTIPCSocket newInstance() throws IOException {
    return (AFTIPCSocket) AFSocket.newInstance(AFTIPCSocket::new, (AFTIPCSocketFactory) null);
  }

  static AFTIPCSocket newInstance(AFTIPCSocketFactory factory) throws SocketException {
    return (AFTIPCSocket) AFSocket.newInstance(AFTIPCSocket::new, factory);
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
  public static AFTIPCSocket newStrictInstance() throws IOException {
    return (AFTIPCSocket) AFSocket.newInstance(AFTIPCSocket::new, (AFTIPCSocketFactory) null);
  }

  /**
   * Creates a new {@link AFSocket} and connects it to the given {@link AFTIPCSocketAddress}.
   *
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static AFTIPCSocket connectTo(AFTIPCSocketAddress addr) throws IOException {
    return (AFTIPCSocket) AFSocket.connectTo(AFTIPCSocket::new, addr);
  }

  @Override
  public AFTIPCSocketChannel getChannel() {
    return (AFTIPCSocketChannel) super.getChannel();
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
    // org.newsclub.net.unix.tipc.SocketTest#testMain.
    System.out.print(AFTIPCSocket.class.getName() + ".isSupported(): ");
    System.out.flush();
    System.out.println(AFTIPCSocket.isSupported());
  }

  @Override
  public AFTIPCErrInfo getErrInfo() {
    return AFTIPCErrInfo.of(((AFTIPCSocketImplExtensions) getImplExtensions()).getTIPCErrInfo());
  }

  @Override
  public AFTIPCDestName getDestName() {
    return AFTIPCDestName.of(((AFTIPCSocketImplExtensions) getImplExtensions()).getTIPCDestName());
  }

  /**
   * Retrieves the 16-byte node ID given a node hash.
   *
   * @param peerId The node hash.
   * @return The node ID, or {@code  null} if unsupported.
   * @throws IOException on error.
   */
  public static byte[] getNodeIdentity(int peerId) throws IOException {
    return getStaticImplExtensions().getTIPCNodeId(peerId);
  }

  /**
   * Retrieves the TIPC node identity given the node hash of the given address.
   *
   * @param address The address.
   * @return The node identity, or {@code  null} if unsupported.
   * @throws IOException on error.
   */
  public byte[] getNodeIdentity(AFTIPCSocketAddress address) throws IOException {
    return AFTIPCSocket.getNodeIdentity(address.getTIPCNodeHash());
  }

  /**
   * Retrieves the node ID given a node hash, as a hexadecimal string.
   *
   * @param peerId The node hash.
   * @return The node ID, or {@code  null} if unsupported.
   * @throws IOException on error.
   */
  public static String getNodeIdHexString(int peerId) throws IOException {
    byte[] id = getNodeIdentity(peerId);
    return id == null ? null : new BigInteger(1, id).toString(16);
  }

  /**
   * Retrieves the link name given a node hash and a bearer ID.
   *
   * @param peerId The node hash.
   * @param bearerId The bearer Id.
   * @return The link name, or {@code  null} if unsupported.
   * @throws IOException on error.
   */
  public static String getLinkName(int peerId, int bearerId) throws IOException {
    return getStaticImplExtensions().getTIPCLinkName(peerId, bearerId);
  }
}
