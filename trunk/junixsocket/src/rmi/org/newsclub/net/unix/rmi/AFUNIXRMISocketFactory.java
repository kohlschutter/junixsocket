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
package org.newsclub.net.unix.rmi;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * An {@link RMISocketFactory} that supports {@link AFUNIXSocket}s.
 * 
 * @author Christian Kohlschuetter
 */
public class AFUNIXRMISocketFactory extends RMISocketFactory implements
        Externalizable {
    private static final long serialVersionUID = 1L;
    
    public static final File DEFAULT_SOCKET_DIR = new File(System
            .getProperty("java.io.tmpdir")); 

    private RMIClientSocketFactory defaultClientFactory;
    private RMIServerSocketFactory defaultServerFactory;

    private File socketDir;
    private AFUNIXNaming naming;
    
    public int hashCode() {
        return socketDir.hashCode();
    }
    public boolean equals(Object other) {
        if(!(other instanceof AFUNIXRMISocketFactory)) {
            return false;
        }
        AFUNIXRMISocketFactory sf = (AFUNIXRMISocketFactory) other;
        return sf.socketDir.equals(socketDir);
    }

    /**
     * Constructor required per definition.
     * @see RMISocketFactory
     *
     */
    public AFUNIXRMISocketFactory() {
    }

    public AFUNIXRMISocketFactory(final AFUNIXNaming naming,
            final File socketDir) {
        this(naming, socketDir, DefaultRMIClientSocketFactory.getInstance(),
                DefaultRMIServerSocketFactory.getInstance());
    }

    public AFUNIXRMISocketFactory(final AFUNIXNaming naming,
            final File socketDir,
            final RMIClientSocketFactory defaultClientFactory,
            final RMIServerSocketFactory defaultServerFactory) {
        this.naming = naming;
        this.socketDir = socketDir == null ? DEFAULT_SOCKET_DIR : socketDir;
        this.defaultClientFactory = defaultClientFactory;
        this.defaultServerFactory = defaultServerFactory;
    }

    public Socket createSocket(String host, int port) throws IOException {
        if (port < AFUNIXRMIPorts.AF_PORT_BASE) {
            return defaultClientFactory.createSocket(host, port);
        }
        final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(getFile(port),
                port);
        return AFUNIXSocket.connectTo(addr);
    }

    public File getSocketDir() {
        return socketDir;
    }

    private File getFile(int port) {
        return new File(socketDir, "junixsocket-rmi-" + port + ".sock");
    }

    private PortAssigner generator = null;

    protected int newPort() throws IOException {
        if (generator == null) {
            try {
                generator = naming.getPortAssigner();
            } catch (NotBoundException e) {
                throw (IOException) new IOException(e.getMessage())
                        .initCause(e);
            }
        }
        return generator.newPort();
    }

    protected void returnPort(int port) throws IOException {
        if (generator == null) {
            try {
                generator = naming.getPortAssigner();
            } catch (NotBoundException e) {
                throw (IOException) new IOException(e.getMessage())
                        .initCause(e);
            }
        }
        generator.returnPort(port);
    }

    public ServerSocket createServerSocket(int port) throws IOException {


        if (port == 0) {
            port = newPort();
            final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(
                    getFile(port), port);
            final AnonymousServerSocket ass = new AnonymousServerSocket(port);
            ass.bind(addr);
            return ass;
        } else if (port < AFUNIXRMIPorts.AF_PORT_BASE) {
            return defaultServerFactory.createServerSocket(port);
        } else {
            final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(
                    getFile(port), port);
            return AFUNIXServerSocket.bindOn(addr);
        }

    }

    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        socketDir = new File(in.readUTF());
        int port = in.readInt();
        naming = AFUNIXNaming.getInstance(socketDir, port);

        defaultClientFactory = (RMIClientSocketFactory) in.readObject();
        defaultServerFactory = (RMIServerSocketFactory) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(socketDir.getAbsolutePath());
        out.writeInt(naming.getRegistryPort());

        out.writeObject(defaultClientFactory);
        out.writeObject(defaultServerFactory);
    }

    private final class AnonymousServerSocket extends AFUNIXServerSocket {
        private final int returnPort;

        protected AnonymousServerSocket(int returnPort) throws IOException {
            super();
            this.returnPort = returnPort;
        }

        public void close() throws IOException {
            super.close();
            returnPort(returnPort);
        }
    }

}
