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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Implementation of an AF_UNIX domain socket.
 * 
 * @author Christian Kohlschütter
 */
public class AFUNIXSocket extends Socket {
  protected AFUNIXSocketImpl impl;
  AFUNIXSocketAddress addr;

  private AFUNIXSocket(final AFUNIXSocketImpl impl) throws IOException {
    super(impl);
    try {
      NativeUnixSocket.setCreated(this);
    } catch (UnsatisfiedLinkError e) {
      e.printStackTrace();
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
   */
  public static AFUNIXSocket newInstance() throws IOException {
    final AFUNIXSocketImpl impl = new AFUNIXSocketImpl.Lenient();
    AFUNIXSocket instance = new AFUNIXSocket(impl);
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
   */
  public static AFUNIXSocket newStrictInstance() throws IOException {
    final AFUNIXSocketImpl impl = new AFUNIXSocketImpl();
    AFUNIXSocket instance = new AFUNIXSocket(impl);
    instance.impl = impl;
    return instance;
  }

  /**
   * Creates a new {@link AFUNIXSocket} and connects it to the given {@link AFUNIXSocketAddress}.
   * 
   * @param addr The address to connect to.
   * @return A new, connected socket.
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
      throw new IOException("Can only connect to endpoints of type "
          + AFUNIXSocketAddress.class.getName());
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
}
