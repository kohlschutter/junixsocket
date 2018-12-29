/**
 * junixsocket
 *
 * Copyright 2009-2018 Christian Kohlschütter
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Implementation of an AF_UNIX domain socket.
 * 
 * @author Christian Kohlschütter
 */
public class AFUNIXSocket extends Socket {
  private static final String PROP_LIBRARY_LOADED = "org.newsclub.net.unix.library.loaded";
  protected AFUNIXSocketImpl impl;
  AFUNIXSocketAddress addr;

  private final AFUNIXSocketFactory socketFactory;

  private AFUNIXSocket(final AFUNIXSocketImpl impl, AFUNIXSocketFactory factory)
      throws IOException {
    super(impl);
    this.socketFactory = factory;
    if (factory == null) {
      setIsCreated();
    }
  }

  private void setIsCreated() throws IOException {
    try {
      NativeUnixSocket.setCreated(this);
    } catch (LinkageError e) {
      throw new IOException("Couldn't load native library", e);
    }
  }

  /**
   * Creates a new, unbound {@link AFUNIXSocket}.
   * 
   * This "default" implementation is a bit "lenient" with respect to the specification.
   * 
   * In particular, we ignore calls to {@link Socket#getTcpNoDelay()} and
   * {@link Socket#setTcpNoDelay(boolean)}.
   * 
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket newInstance() throws IOException {
    return newInstance(null);
  }

  static AFUNIXSocket newInstance(AFUNIXSocketFactory factory) throws IOException {
    final AFUNIXSocketImpl impl = new AFUNIXSocketImpl.Lenient();
    AFUNIXSocket instance = new AFUNIXSocket(impl, factory);
    instance.impl = impl;
    return instance;
  }

  /**
   * Creates a new, unbound, "strict" {@link AFUNIXSocket}.
   * 
   * This call uses an implementation that tries to be closer to the specification than
   * {@link #newInstance()}, at least for some cases.
   * 
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket newStrictInstance() throws IOException {
    final AFUNIXSocketImpl impl = new AFUNIXSocketImpl();
    AFUNIXSocket instance = new AFUNIXSocket(impl, null);
    instance.impl = impl;
    return instance;
  }

  /**
   * Creates a new {@link AFUNIXSocket} and connects it to the given {@link AFUNIXSocketAddress}.
   * 
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket connectTo(AFUNIXSocketAddress addr) throws IOException {
    AFUNIXSocket socket = newInstance();
    socket.connect(addr);
    return socket;
  }

  /**
   * Binds this {@link AFUNIXSocket} to the given bindpoint. Only bindpoints of the type
   * {@link AFUNIXSocketAddress} are supported.
   */
  @Override
  public void bind(SocketAddress bindpoint) throws IOException {
    super.bind(bindpoint);
    this.addr = (AFUNIXSocketAddress) bindpoint;
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    connect(endpoint, 0);
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    if (!(endpoint instanceof AFUNIXSocketAddress)) {
      if (socketFactory != null) {
        if (endpoint instanceof InetSocketAddress) {
          InetSocketAddress isa = (InetSocketAddress) endpoint;

          String hostname = isa.getHostString();
          if (socketFactory.isHostnameSupported(hostname)) {
            endpoint = socketFactory.addressFromHost(hostname, isa.getPort());
          }
        }
      }
      if (!(endpoint instanceof AFUNIXSocketAddress)) {
        throw new IllegalArgumentException("Can only connect to endpoints of type " + AFUNIXSocketAddress.class
            .getName() + ", got: " + endpoint);
      }
    }
    impl.connect(endpoint, timeout);
    this.addr = (AFUNIXSocketAddress) endpoint;
    NativeUnixSocket.setConnected(this);
  }

  @Override
  public String toString() {
    if (isConnected()) {
      return "AFUNIXSocket[fd=" + impl.getFD() + ";path=" + addr.getSocketFile() + "]";
    }
    return "AFUNIXSocket[unconnected]";
  }

  /**
   * Returns <code>true</code> iff {@link AFUNIXSocket}s are supported by the current Java VM.
   * 
   * To support {@link AFUNIXSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>.
   * 
   * @return {@code true} iff supported.
   */
  public static boolean isSupported() {
    return NativeUnixSocket.isLoaded();
  }

  /**
   * Returns an identifier of the loaded native library, or {@code null} if the library hasn't been
   * loaded yet.
   * 
   * The identifier is useful mainly for debugging purposes.
   * 
   * @return The identifier of the loaded junixsocket-native library, or {@code null}.
   */
  public static String getLoadedLibrary() {
    return System.getProperty(PROP_LIBRARY_LOADED);
  }
}
