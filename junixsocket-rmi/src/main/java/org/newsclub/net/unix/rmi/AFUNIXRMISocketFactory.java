/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
package org.newsclub.net.unix.rmi;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownHook;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownThread;

/**
 * An {@link RMISocketFactory} that supports {@link AFUNIXSocket}s.
 * 
 * @author Christian Kohlschütter
 */
public class AFUNIXRMISocketFactory extends RMISocketFactory implements Externalizable, Closeable,
    ShutdownHook {
  static final String DEFAULT_SOCKET_FILE_PREFIX = "";
  static final String DEFAULT_SOCKET_FILE_SUFFIX = ".rmi";

  private static final long serialVersionUID = 1L;

  private RMIClientSocketFactory defaultClientFactory;
  private RMIServerSocketFactory defaultServerFactory;

  private File socketDir;
  private AFUNIXNaming naming;

  private String socketPrefix;
  private String socketSuffix;

  private AFUNIXRMIService rmiService = null;

  /**
   * Constructor required per definition.
   * 
   * @see RMISocketFactory
   */
  public AFUNIXRMISocketFactory() {
    super();
    closeUponRuntimeShutdown();
  }

  public AFUNIXRMISocketFactory(final AFUNIXNaming naming, final File socketDir)
      throws IOException {
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
    super();
    this.naming = naming;
    this.socketDir = socketDir;
    this.defaultClientFactory = defaultClientFactory;
    this.defaultServerFactory = defaultServerFactory;
    this.socketPrefix = socketPrefix == null ? DEFAULT_SOCKET_FILE_PREFIX : socketPrefix;
    this.socketSuffix = socketSuffix == null ? DEFAULT_SOCKET_FILE_SUFFIX : socketSuffix;

    closeUponRuntimeShutdown();
  }

  private boolean isPlainFileSocket() {
    return (naming.getRegistryPort() == AFUNIXRMIPorts.PLAIN_FILE_SOCKET);
  }

  void deleteStaleFiles() {
    if (isPlainFileSocket()) {
      // nothing to do
      return;
    }
    File sd = socketDir;
    if (sd == null) {
      return;
    }
    File[] files = sd.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith(socketPrefix) && name.endsWith(socketSuffix);
      }
    });
    if (files == null) {
      return;
    }
    for (File f : files) {
      String name = f.getName();
      String portName = name.substring(0, name.length() - socketSuffix.length()).substring(
          socketPrefix.length());

      int port;
      try {
        port = Integer.parseInt(portName);
      } catch (Exception e) {
        port = -1;
      }
      if (port >= AFUNIXRMIPorts.AF_PORT_BASE) {
        boolean connected = false;
        try (Socket s = this.createSocket("", port)) {
          connected = true;
        } catch (IOException e) {
          // connection refused or something else
        }

        if (!connected) {
          try {
            Files.delete(f.toPath());
          } catch (IOException e) {
            // ignore
          }
        }
      }
    }
  }

  private void closeUponRuntimeShutdown() {
    ShutdownHookSupport.addShutdownHook(this);
  }

  @Override
  public int hashCode() {
    return socketDir == null ? super.hashCode() : socketDir.hashCode();
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

    final AFUNIXSocket socket = AFUNIXSocket.newInstance();
    socket.connect(addr);
    return socket;
  }

  public File getSocketDir() {
    return socketDir;
  }

  File getFile(int port) {
    if (isPlainFileSocket()) {
      return socketDir;
    } else {
      return new File(socketDir, socketPrefix + port + socketSuffix);
    }
  }

  void deleteSocketFile(int port) {
    try {
      Files.delete(getFile(port).toPath());
    } catch (IOException e) {
      // ignore
    }
  }

  boolean hasSocketFile(int port) {
    return getFile(port).exists();
  }

  @Override
  public void close() throws RemoteException {
    if (rmiService != null) {
      rmiService.openPorts().forEach((int port) -> {
        deleteSocketFile(port);
      });
    }
  }

  private AFUNIXRMIService getRmiService() throws IOException {
    if (rmiService == null) {
      try {
        rmiService = naming.getRMIService();
      } catch (NotBoundException e) {
        throw (IOException) new IOException(e.getMessage()).initCause(e);
      }
    }
    return rmiService;
  }

  protected int newPort() throws IOException {
    return getRmiService().newPort();
  }

  protected void returnPort(int port) throws IOException {
    deleteSocketFile(port);
    getRmiService().returnPort(port);
  }

  public AFUNIXSocketAddress newAnonymousServerSocketAddress() throws IOException {
    int port = newPort();
    return new AFUNIXSocketAddress(getFile(port), port);
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

  @Override
  public String toString() {
    return super.toString() + //
        "[path=" + socketDir + //
        (isPlainFileSocket() ? "" : //
            ";prefix=" + socketPrefix + ";suffix=" + socketSuffix) + "]";
  }

  @Override
  public void onRuntimeShutdown(@SuppressWarnings("exports") ShutdownThread thread) {
    if (thread != Thread.currentThread()) {
      throw new IllegalStateException("Illegal caller");
    }
    try {
      close();
    } catch (IOException e) {
      // ignore
    }
  }
}
