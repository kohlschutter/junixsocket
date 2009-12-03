/**
 * junixsocket
 *
 * Copyright (c) 2009 NewsClub, Christian Kohlschütter
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
     * @return
     * @throws IOException
     */
    public static AFUNIXSocket newInstance() throws IOException {
        final AFUNIXSocketImpl impl = new AFUNIXSocketImpl();
        AFUNIXSocket instance = new AFUNIXSocket(impl);
        instance.impl = impl;
        return instance;
    }

    /**
     * Creates a new {@link AFUNIXSocket} and connects it to the given
     * {@link AFUNIXSocketAddress}.
     * 
     * @param addr
     * @return
     * @throws IOException
     */
    public static AFUNIXSocket connectTo(AFUNIXSocketAddress addr)
            throws IOException {
        AFUNIXSocket socket = newInstance();
        socket.connect(addr);
        return socket;
    }

    /**
     * Binds this {@link AFUNIXSocket} to the given bindpoint.
     * Only bindpoints of the type {@link AFUNIXSocketAddress} are supported.
     */
    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        if (!(addr instanceof AFUNIXSocketAddress)) {
            throw new IOException("Can only bind to endpoints of type "
                    + AFUNIXSocketAddress.class.getName());
        }
        super.bind(bindpoint);
        this.addr = (AFUNIXSocketAddress) bindpoint;
    }

    /** {@inheritDoc} */
    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    /** {@inheritDoc} */
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
        if (isConnected())
            return "AFUNIXSocket[fd=" + impl.getFD() + ";path="
                    + addr.getSocketFile() + "]";
        return "AFUNIXSocket[unconnected]";
    }
    
    /**
     * Returns <code>true</code> iff {@link AFUNIXSocket}s are supported
     * by the current Java VM.
     * 
     * To support {@link AFUNIXSocket}s, a custom JNI library must be loaded
     * that is supplied with junixsocket.
     * 
     * @return
     */
    public static boolean isSupported() {
        return NativeUnixSocket.isSupported();
    }
}
