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
 * @author Christian Kohlschütter
 */
public class AFUNIXRMISocketFactory extends RMISocketFactory implements Externalizable {
  static final String DEFAULT_SOCKET_FILE_PREFIX = "";
  static final String DEFAULT_SOCKET_FILE_SUFFIX = ".rmi";

  private static final long serialVersionUID = 1L;

  private RMIClientSocketFactory defaultClientFactory;
  private RMIServerSocketFactory defaultServerFactory;

  private File socketDir;
  private AFUNIXNaming naming;

  private String socketPrefix;

  private String socketSuffix;

  private PortAssigner generator = null;

  /**
   * Constructor required per definition.
   * 
   * @see RMISocketFactory
   * 
   */
  public AFUNIXRMISocketFactory() {
  }

  public AFUNIXRMISocketFactory(final AFUNIXNaming naming, final File socketDir) throws IOException {
    this(naming, socketDir, DefaultRMIClientSocketFactory.getInstance(),
        DefaultRMIServerSocketFactory.getInstance());
  }

  public AFUNIXRMISocketFactory(final AFUNIXNaming naming, final File socketDir,
      final RMIClientSocketFactory defaultClientFactory,
      final RMIServerSocketFactory defaultServerFactory) throws IOException {
    this(naming, socketDir, defaultClientFactory, defaultServerFactory, null, null);
  }

  public AFUNIXRMISocketFactory(final AFUNIXNaming naming, final File socketDir,
      final RMIClientSocketFactory defaultClientFactory,
      final RMIServerSocketFactory defaultServerFactory, final String socketPrefix,
      final String socketSuffix) throws IOException {
    this.naming = naming;
    this.socketDir = socketDir;
    this.defaultClientFactory = defaultClientFactory;
    this.defaultServerFactory = defaultServerFactory;
    this.socketPrefix = socketPrefix == null ? DEFAULT_SOCKET_FILE_PREFIX : socketPrefix;
    this.socketSuffix = socketSuffix == null ? DEFAULT_SOCKET_FILE_SUFFIX : socketSuffix;
  }

  @Override
  public int hashCode() {
    return socketDir.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof AFUNIXRMISocketFactory)) {
      return false;
    }
    AFUNIXRMISocketFactory sf = (AFUNIXRMISocketFactory) other;
    return sf.socketDir.equals(socketDir);
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    final RMIClientSocketFactory cf = defaultClientFactory;
    if (cf != null && port < AFUNIXRMIPorts.AF_PORT_BASE) {
      return cf.createSocket(host, port);
    }

    final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(getFile(port), port);
    return AFUNIXSocket.connectTo(addr);
  }

  public File getSocketDir() {
    return socketDir;
  }

  private File getFile(int port) {
    if (naming.getRegistryPort() == AFUNIXRMIPorts.PLAIN_FILE_SOCKET) {
      return socketDir;
    } else {
      return new File(socketDir, socketPrefix + port + socketSuffix);
    }
  }

  public void close() {
  }

  protected int newPort() throws IOException {
    if (generator == null) {
      try {
        generator = naming.getPortAssigner();
      } catch (NotBoundException e) {
        throw (IOException) new IOException(e.getMessage()).initCause(e);
      }
    }
    return generator.newPort();
  }

  protected void returnPort(int port) throws IOException {
    if (generator == null) {
      try {
        generator = naming.getPortAssigner();
      } catch (NotBoundException e) {
        throw (IOException) new IOException(e.getMessage()).initCause(e);
      }
    }
    generator.returnPort(port);
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    if (port == 0) {
      port = newPort();
      final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(getFile(port), port);
      final AnonymousServerSocket ass = new AnonymousServerSocket(port);
      ass.bind(addr);
      return ass;
    }

    final RMIServerSocketFactory sf = defaultServerFactory;
    if (sf != null && port < AFUNIXRMIPorts.AF_PORT_BASE) {
      return sf.createServerSocket(port);
    }

    final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(getFile(port), port);
    return AFUNIXServerSocket.bindOn(addr);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    socketDir = new File(in.readUTF());
    int port = in.readInt();
    naming = AFUNIXNaming.getInstance(socketDir, port);

    defaultClientFactory = (RMIClientSocketFactory) in.readObject();
    defaultServerFactory = (RMIServerSocketFactory) in.readObject();

    socketPrefix = in.readUTF();
    socketSuffix = in.readUTF();
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeUTF(socketDir.getAbsolutePath());
    out.writeInt(naming.getRegistryPort());

    out.writeObject(defaultClientFactory);
    out.writeObject(defaultServerFactory);

    out.writeUTF(socketPrefix);
    out.writeUTF(socketSuffix);
  }

  private final class AnonymousServerSocket extends AFUNIXServerSocket {
    private final int returnPort;

    protected AnonymousServerSocket(int returnPort) throws IOException {
      super();
      this.returnPort = returnPort;
    }

    @Override
    public void close() throws IOException {
      super.close();
      returnPort(returnPort);
    }
  }

}
