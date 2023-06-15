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
import java.nio.file.Files;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFUNIXSocket;

/**
 * The {@link AFUNIXSocket}-compatible equivalent of {@link Naming}. Use this class for accessing
 * RMI registries that are reachable by {@link AFUNIXSocket}s.
 *
 * @author Christian Kohlschütter
 */
public final class AFUNIXNaming extends AFNaming {
  private static final String PROP_RMI_SOCKET_DIR = "org.newsclub.net.unix.rmi.socketdir";
  private static final File DEFAULT_SOCKET_DIRECTORY = new File(System.getProperty(
      PROP_RMI_SOCKET_DIR, "/tmp"));

  private boolean deleteRegistrySocketDir = false;
  private final File registrySocketDir;

  private final RMIClientSocketFactory defaultClientSocketFactory;
  private final RMIServerSocketFactory defaultServerSocketFactory;

  private final String socketPrefix;
  private final String socketSuffix;

  private AFUNIXNaming(File socketDir, int registryPort, String socketPrefix, String socketSuffix)
      throws IOException {
    super(registryPort, RMIPorts.RMI_SERVICE_PORT);
    Objects.requireNonNull(socketDir);
    this.registrySocketDir = socketDir;
    this.socketPrefix = socketPrefix;
    this.socketSuffix = socketSuffix;

    this.defaultClientSocketFactory = null; // DefaultRMIClientSocketFactory.getInstance();
    this.defaultServerSocketFactory = null; // DefaultRMIServerSocketFactory.getInstance();
  }

  /**
   * Returns the directory where RMI sockets are stored by default.
   *
   * You can configure this location by setting the System property
   * {@code org.newsclub.net.unix.rmi.socketdir} upon start.
   *
   * @return The directory.
   */
  public static File getDefaultSocketDirectory() {
    return DEFAULT_SOCKET_DIRECTORY;
  }

  /**
   * Returns a new private instance that resides in a custom location, to avoid any collisions with
   * existing instances.
   *
   * @return The private {@link AFNaming} instance.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXNaming newPrivateInstance() throws IOException {
    File tmpDir = Files.createTempDirectory("junixsocket-").toFile();
    if (!tmpDir.canWrite()) {
      throw new IOException("Could not create temporary directory: " + tmpDir);
    }
    AFUNIXNaming instance = getInstance(tmpDir, RMIPorts.DEFAULT_REGISTRY_PORT);
    synchronized (instance) {
      instance.deleteRegistrySocketDir = true;
    }
    return instance;
  }

  /**
   * Returns the default instance of {@link AFUNIXNaming}. Sockets are stored in
   * <code>java.io.tmpdir</code>.
   *
   * @return The default instance.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXNaming getInstance() throws IOException {
    return getInstance(DEFAULT_SOCKET_DIRECTORY, RMIPorts.DEFAULT_REGISTRY_PORT);
  }

  /**
   * Returns a {@link AFUNIXNaming} instance which support several socket files that can be stored
   * under the same, given directory.
   *
   * @param socketDir The directory to store sockets in.
   * @return The instance.
   * @throws RemoteException if the operation fails.
   */
  public static AFUNIXNaming getInstance(final File socketDir) throws RemoteException {
    return getInstance(socketDir, RMIPorts.DEFAULT_REGISTRY_PORT);
  }

  /**
   * Returns a {@link AFUNIXNaming} instance which support several socket files that can be stored
   * under the same, given directory.
   *
   * A custom "registry port" can be specified. Typically, AF-UNIX specific ports should be above
   * {@code 100000}.
   *
   * @param socketDir The directory to store sockets in.
   * @param registryPort The registry port. Should be above {@code 100000}.
   * @return The instance.
   * @throws RemoteException if the operation fails.
   */
  public static AFUNIXNaming getInstance(File socketDir, final int registryPort)
      throws RemoteException {
    return getInstance(socketDir, registryPort, null, null);
  }

  /**
   * Returns a {@link AFUNIXNaming} instance which support several socket files that can be stored
   * under the same, given directory.
   *
   * A custom "registry port" can be specified. Typically, AF-UNIX specific ports should be above
   * {@code 100000}.
   *
   * @param socketDir The directory to store sockets in.
   * @param registryPort The registry port. Should be above {@code 100000}.
   * @param socketPrefix A string to be inserted at the beginning of each socket filename, or
   *          {@code null}.
   * @param socketSuffix A string to be added at the end of each socket filename, or {@code null}.
   * @return The instance.
   * @throws RemoteException if the operation fails.
   */
  public static AFUNIXNaming getInstance(File socketDir, final int registryPort,
      String socketPrefix, String socketSuffix) throws RemoteException {
    return AFNaming.getInstance(registryPort, new AFUNIXNamingProvider(socketDir, socketPrefix,
        socketSuffix));
  }

  private static final class AFUNIXNamingProvider implements AFNamingProvider<AFUNIXNaming> {
    private final File socketDir;
    private final String socketPrefix;
    private final String socketSuffix;

    public AFUNIXNamingProvider(File socketDir, String socketPrefix, String socketSuffix)
        throws RemoteException {
      try {
        this.socketDir = socketDir.getCanonicalFile();
      } catch (IOException e) {
        throw new RemoteException(e.getMessage(), e);
      }
      this.socketPrefix = socketPrefix == null ? AFUNIXRMISocketFactory.DEFAULT_SOCKET_FILE_PREFIX
          : socketPrefix;
      this.socketSuffix = socketSuffix == null ? AFUNIXRMISocketFactory.DEFAULT_SOCKET_FILE_SUFFIX
          : socketSuffix;
    }

    @Override
    public AFUNIXNaming newInstance(int port) throws IOException {
      return new AFUNIXNaming(socketDir, port, socketPrefix, socketSuffix); // NOPMD
    }
  }

  /**
   * Returns an {@link AFUNIXNaming} instance which only supports one file. (Probably only useful
   * when you want/can access the exported {@link UnicastRemoteObject} directly)
   *
   * @param socketFile The socket file.
   * @return The instance.
   * @throws IOException if the operation fails.
   */
  public static AFNaming getSingleFileInstance(final File socketFile) throws IOException {
    return getInstance(socketFile, RMIPorts.PLAIN_FILE_SOCKET);
  }

  @Override
  public AFUNIXRMISocketFactory getSocketFactory() {
    return (AFUNIXRMISocketFactory) super.getSocketFactory();
  }

  @Override
  public AFRegistry getRegistry() throws RemoteException {
    return getRegistry(0, TimeUnit.SECONDS);
  }

  @Override
  public AFUNIXRegistry getRegistry(long timeout, TimeUnit unit) throws RemoteException {
    return (AFUNIXRegistry) super.getRegistry(timeout, unit);
  }

  @Override
  public AFUNIXRegistry getRegistry(boolean create) throws RemoteException {
    return (AFUNIXRegistry) super.getRegistry(create);
  }

  @Override
  public AFUNIXRegistry createRegistry() throws RemoteException {
    return (AFUNIXRegistry) super.createRegistry();
  }

  /**
   * Returns the socket file which is used to control the RMI registry.
   *
   * The file is usually in the directory returned by {@link #getRegistrySocketDir()}.
   *
   * @return The directory.
   */
  public File getRegistrySocketFile() {
    return getSocketFactory().getFile(getRegistryPort());
  }

  @Override
  protected AFUNIXRMISocketFactory initSocketFactory() throws IOException {
    return new AFUNIXRMISocketFactory(this, registrySocketDir, defaultClientSocketFactory,
        defaultServerSocketFactory, socketPrefix, socketSuffix);
  }

  @Override
  protected AFUNIXRegistry newAFRegistry(Registry impl) throws RemoteException {
    return new AFUNIXRegistry(this, impl);
  }

  @Override
  protected AFRegistry openRegistry(long timeout, TimeUnit unit) throws RemoteException {
    File socketFile = getRegistrySocketFile();
    if (!socketFile.exists()) {
      if (waitUntilFileExists(socketFile, timeout, unit)) {
        AFRegistry reg = getRegistry(false);
        if (reg != null) {
          return reg;
        }
      }
    }
    throw new ShutdownException("Could not find registry at " + getRegistrySocketFile());
  }

  private boolean waitUntilFileExists(File f, long timeout, TimeUnit unit) {
    long timeWait = unit.toMillis(timeout);

    try {
      while (timeWait > 0 && !f.exists()) {
        Thread.sleep(Math.min(50, timeWait));
        timeWait -= 50;
      }
    } catch (InterruptedException e) {
      // ignored
    }

    return f.exists();
  }

  private synchronized void deleteSocketDir() {
    if (deleteRegistrySocketDir && registrySocketDir != null) {
      try {
        Files.delete(registrySocketDir.toPath());
      } catch (IOException e) {
        // ignore
      }
    }
  }

  @Override
  protected void shutdownRegistryFinishingTouches() {
    deleteSocketDir();
  }

  /**
   * Returns the directory in which sockets used by this registry are located.
   *
   * @return The directory.
   */
  public File getRegistrySocketDir() {
    return registrySocketDir;
  }

  @Override
  protected void initRegistryPrerequisites() throws ServerException {
    if (registrySocketDir != null && !registrySocketDir.mkdirs() && !registrySocketDir
        .isDirectory()) {
      throw new ServerException("Cannot create socket directory:" + registrySocketDir);
    }
  }
}
