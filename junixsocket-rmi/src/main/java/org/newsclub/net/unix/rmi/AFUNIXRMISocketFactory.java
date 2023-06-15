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
package org.newsclub.net.unix.rmi;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketCredentials;
import org.newsclub.net.unix.HostAndPort;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * An {@link RMISocketFactory} that supports {@link AFUNIXSocket}s.
 *
 * @author Christian Kohlschütter
 */
public class AFUNIXRMISocketFactory extends AFRMISocketFactory {
  private static final long serialVersionUID = 1L;

  static final String DEFAULT_SOCKET_FILE_PREFIX = "";
  static final String DEFAULT_SOCKET_FILE_SUFFIX = ".rmi";

  private File socketDir;
  private String socketPrefix;
  private String socketSuffix;

  private final transient Map<HostAndPort, AFUNIXSocketCredentials> credentials = new HashMap<>();

  /**
   * Constructor required per definition.
   *
   * @see RMISocketFactory
   */
  public AFUNIXRMISocketFactory() {
    super();
  }

  /**
   * Creates a new socket factory.
   *
   * @param naming The {@link AFNaming} instance to use.
   * @param socketDir The directory to store the sockets in.
   * @param defaultClientFactory The default {@link RMIClientSocketFactory}.
   * @param defaultServerFactory The default {@link RMIServerSocketFactory}.
   * @param socketPrefix A string that will be inserted at the beginning of each socket filename, or
   *          {@code null}.
   * @param socketSuffix A string that will be added to the end of each socket filename, or
   *          {@code null}.
   * @throws IOException on error.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public AFUNIXRMISocketFactory(final AFNaming naming, final File socketDir,
      final RMIClientSocketFactory defaultClientFactory,
      final RMIServerSocketFactory defaultServerFactory, final String socketPrefix,
      final String socketSuffix) throws IOException {
    super(naming, defaultClientFactory, defaultServerFactory);
    Objects.requireNonNull(socketDir);
    this.socketDir = socketDir;
    this.socketPrefix = socketPrefix == null ? DEFAULT_SOCKET_FILE_PREFIX : socketPrefix;
    this.socketSuffix = socketSuffix == null ? DEFAULT_SOCKET_FILE_SUFFIX : socketSuffix;
  }

  /**
   * Creates a new socket factory.
   *
   * @param naming The {@link AFNaming} instance to use.
   * @param socketDir The directory to store the sockets in.
   * @param defaultClientFactory The default {@link RMIClientSocketFactory}.
   * @param defaultServerFactory The default {@link RMIServerSocketFactory}.
   * @throws IOException on error.
   */
  public AFUNIXRMISocketFactory(AFNaming naming, File socketDir,
      RMIClientSocketFactory defaultClientFactory, RMIServerSocketFactory defaultServerFactory)
      throws IOException {
    this(naming, socketDir, defaultClientFactory, defaultServerFactory, null, null);
  }

  /**
   * Creates a new socket factory.
   *
   * @param naming The {@link AFNaming} instance to use.
   * @param socketDir The directory to store the sockets in.
   * @throws IOException on error.
   */
  public AFUNIXRMISocketFactory(AFNaming naming, File socketDir) throws IOException {
    this(naming, socketDir, DefaultRMIClientSocketFactory.getInstance(),
        DefaultRMIServerSocketFactory.getInstance());
  }

  @Override
  protected AFNaming readNamingInstance(ObjectInput in) throws IOException {
    socketDir = new File(in.readUTF());
    int port = in.readInt();
    return AFUNIXNaming.getInstance(socketDir, port);
  }

  @Override
  protected void writeNamingInstance(ObjectOutput out, AFNaming naming) throws IOException {
    out.writeUTF(socketDir.getAbsolutePath());
    out.writeInt(naming.getRegistryPort());
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    super.readExternal(in);

    socketPrefix = in.readUTF();
    socketSuffix = in.readUTF();
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    super.writeExternal(out);

    out.writeUTF(socketPrefix);
    out.writeUTF(socketSuffix);
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
    if (socketDir == null) {
      return sf == this;
    } else {
      return socketDir.equals(sf.socketDir);
    }
  }

  /**
   * The directory in which socket files are stored.
   *
   * @return The directory.
   */
  public File getSocketDir() {
    return socketDir;
  }

  File getFile(int port) {
    if (isPlainFileSocket()) {
      return getSocketDir();
    } else {
      Objects.requireNonNull(socketDir);
      return new File(socketDir, socketPrefix + port + socketSuffix);
    }
  }

  boolean hasSocketFile(int port) {
    return getFile(port).exists();
  }

  private boolean isPlainFileSocket() {
    return (getNaming().getRegistryPort() == RMIPorts.PLAIN_FILE_SOCKET);
  }

  @Override
  protected AFUNIXSocketAddress newSocketAddress(int port) throws IOException {
    return AFUNIXSocketAddress.of(getFile(port), port);
  }

  @Override
  protected final AFSocket<?> newConnectedSocket(AFSocketAddress addr) throws IOException {
    final AFUNIXSocket socket = ((AFUNIXSocketAddress) addr).newConnectedSocket();
    AFUNIXSocketCredentials creds = socket.getPeerCredentials();

    final HostAndPort hap = new HostAndPort(addr.getHostString(), addr.getPort());
    synchronized (credentials) {
      if (credentials.put(hap, creds) != null) {
        // unexpected
      }
    }
    socket.addCloseable(() -> {
      synchronized (credentials) {
        credentials.remove(hap);
      }
    });
    return socket;
  }

  @Override
  public String toString() {
    return super.toString() + //
        "[path=" + socketDir + //
        (isPlainFileSocket() ? "" : //
            ";prefix=" + socketPrefix + ";suffix=" + socketSuffix) + "]";
  }

  @Override
  public void close() throws IOException {
    credentials.clear();
    super.close();
  }

  AFUNIXSocketCredentials peerCredentialsFor(RemotePeerInfo data) {
    synchronized (credentials) {
      return credentials.get(new HostAndPort(data.host, data.port));
    }
  }

  @Override
  boolean hasRegisteredPort(int port) {
    return hasSocketFile(port);
  }
}
