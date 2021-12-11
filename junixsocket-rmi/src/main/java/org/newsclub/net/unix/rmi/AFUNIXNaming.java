/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.ConnectIOException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownHook;

/**
 * The {@link AFUNIXSocket}-compatible equivalent of {@link Naming}. Use this class for accessing
 * RMI registries that are reachable by {@link AFUNIXSocket}s.
 * 
 * @author Christian Kohlschütter
 */
public final class AFUNIXNaming extends AFUNIXRegistryAccess {
  private static final String RMI_SERVICE_NAME = AFUNIXRMIService.class.getName();
  private static final String PROP_RMI_SOCKET_DIR = "org.newsclub.net.unix.rmi.socketdir";

  private static final File DEFAULT_SOCKET_DIRECTORY = new File(System.getProperty(
      PROP_RMI_SOCKET_DIR, "/tmp"));

  private static final Map<AFUNIXNamingRef, AFUNIXNaming> INSTANCES = new HashMap<>();

  private AFUNIXRegistry registry = null;
  private AFUNIXRMIService rmiService = null;
  private final File registrySocketDir;
  private final int registryPort;
  private final int servicePort;
  private final AFUNIXRMISocketFactory socketFactory;
  private boolean deleteRegistrySocketDir = false;
  private boolean remoteShutdownAllowed = true;
  private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
  private final AtomicBoolean addedShutdownHook = new AtomicBoolean(false);

  private AFUNIXNaming(final File socketDir, final int port, final String socketPrefix,
      final String socketSuffix) throws IOException {
    super();
    this.registrySocketDir = socketDir;
    this.registryPort = port;
    this.servicePort = AFUNIXRMIPorts.RMI_SERVICE_PORT;
    this.socketFactory = new AFUNIXRMISocketFactory(this, socketDir, null, null, socketPrefix,
        socketSuffix);
  }

  /**
   * Returns a new private instance that resides in a custom location, to avoid any collisions with
   * existing instances.
   * 
   * @return The private {@link AFUNIXNaming} instance.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXNaming newPrivateInstance() throws IOException {
    File tmpDir = Files.createTempDirectory("junixsocket-").toFile();
    if (!tmpDir.canWrite()) {
      throw new IOException("Could not create temporary directory: " + tmpDir);
    }
    AFUNIXNaming instance = getInstance(tmpDir, AFUNIXRMIPorts.DEFAULT_REGISTRY_PORT);
    synchronized (AFUNIXNaming.class) {
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
    return getInstance(DEFAULT_SOCKET_DIRECTORY, AFUNIXRMIPorts.DEFAULT_REGISTRY_PORT);
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
    return getInstance(socketDir, AFUNIXRMIPorts.DEFAULT_REGISTRY_PORT);
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
    Objects.requireNonNull(socketDir);
    final AFUNIXNamingRef sap = new AFUNIXNamingRef(socketDir, registryPort, socketPrefix,
        socketSuffix);
    AFUNIXNaming instance;
    synchronized (AFUNIXNaming.class) {
      instance = INSTANCES.get(sap);
      if (instance == null) {
        try {
          instance = new AFUNIXNaming(sap.socketDir, registryPort, socketPrefix, socketSuffix);
        } catch (RemoteException e) {
          throw e;
        } catch (IOException e) {
          throw new RemoteException(e.getMessage(), e);
        }
        INSTANCES.put(sap, instance);
      }
    }
    return instance;
  }

  /**
   * Returns an {@link AFUNIXNaming} instance which only supports one file. (Probably only useful
   * when you want/can access the exported {@link UnicastRemoteObject} directly)
   * 
   * @param socketFile The socket file.
   * @return The instance.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXNaming getSingleFileInstance(final File socketFile) throws IOException {
    return getInstance(socketFile, AFUNIXRMIPorts.PLAIN_FILE_SOCKET);
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
   * Returns the {@link AFUNIXRMISocketFactory} associated with this instance.
   * 
   * @return The {@link AFUNIXRMISocketFactory}.
   */
  public AFUNIXRMISocketFactory getSocketFactory() {
    return socketFactory;
  }

  /**
   * Returns the directory in which sockets used by this registry are located.
   * 
   * @return The directory.
   */
  public File getRegistrySocketDir() {
    return registrySocketDir;
  }

  /**
   * Returns the socket file which is used to control the RMI registry.
   *
   * The file is usually in the directory returned by {@link #getRegistrySocketDir()}.
   * 
   * @return The directory.
   */
  public File getRegistrySocketFile() {
    return socketFactory.getFile(registryPort);
  }

  /**
   * Returns the registry port.
   * 
   * @return The port.
   */
  public int getRegistryPort() {
    return registryPort;
  }

  AFUNIXRMIService getRMIService() throws RemoteException, NotBoundException {
    return getRMIService(getRegistry());
  }

  AFUNIXRMIService getRMIService(AFUNIXRegistry reg) throws RemoteException, NotBoundException {
    if (rmiService == null) {
      this.rmiService = getRMIServiceFromRegistry(reg);
    }
    return rmiService;
  }

  AFUNIXRMIService getRMIServiceFromRegistry(AFUNIXRegistry reg) throws RemoteException,
      NotBoundException {
    AFUNIXRMIService service;
    service = (AFUNIXRMIService) reg.lookup(RMI_SERVICE_NAME, 5, TimeUnit.SECONDS);
    this.remoteShutdownAllowed = service.isShutdownAllowed();
    return service;
  }

  private void closeUponRuntimeShutdown() {
    if (addedShutdownHook.compareAndSet(false, true)) {
      ShutdownHookSupport.addWeakShutdownHook(new ShutdownHook() {

        @Override
        public void onRuntimeShutdown(Thread thread) throws IOException {
          synchronized (AFUNIXNaming.class) {
            if (registry != null && registry.isLocal()) {
              shutdownRegistry();
            }
          }
        }
      });
    }
  }

  private void rebindRMIService(final AFUNIXRMIService assigner) throws RemoteException {
    rmiService = assigner;
    getRegistry().rebind(RMI_SERVICE_NAME, assigner);
  }

  @Override
  public AFUNIXRegistry getRegistry() throws RemoteException {
    return getRegistry(0, TimeUnit.SECONDS);
  }

  /**
   * Returns a reference to the existing RMI registry.
   * 
   * If there's no registry running at this port after waiting for up to the given time, an
   * exception is thrown.
   * 
   * @param timeout The timeout value.
   * @param unit The timeout unit.
   * @return The registry.
   * @throws RemoteException If there was a problem.
   */
  public AFUNIXRegistry getRegistry(long timeout, TimeUnit unit) throws RemoteException {
    if (shutdownInProgress.get()) {
      throw new ShutdownException();
    }
    synchronized (AFUNIXNaming.class) {
      AFUNIXRegistry reg = getRegistry(false);
      if (reg == null) {
        File socketFile = getRegistrySocketFile();
        if (!socketFile.exists()) {
          if (waitUntilFileExists(socketFile, timeout, unit)) {
            reg = getRegistry(false);
            if (reg != null) {
              return reg;
            }
          }
        }
        throw new ShutdownException("Could not find registry at " + getRegistrySocketFile());
      }
      return reg;
    }
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

  /**
   * Returns a reference to the RMI registry, or {@code null}.
   *
   * If there's no registry running at this port, and {@code create} is set to {@code true}, a new
   * one is created; when {@code create} is set to {@code false}, {@code null} is returned.
   * 
   * @param create {@code true} if a new register may be created if necessary.
   * @return The registry, or {@code null}
   * @throws RemoteException If there was a problem.
   */
  public AFUNIXRegistry getRegistry(boolean create) throws RemoteException {
    if (shutdownInProgress.get()) {
      throw new ShutdownException();
    }
    synchronized (AFUNIXNaming.class) {
      if (registry != null) {
        return registry;
      } else if (!socketFactory.hasSocketFile(registryPort)) {
        return create ? createRegistry() : null;
      }

      AFUNIXRegistry reg = locateRegistry();
      setRegistry(reg);

      try {
        getRMIService(reg);
      } catch (NotBoundException | NoSuchObjectException | ConnectIOException e) {
        if (create) {
          setRegistry(null);
          return createRegistry();
        } else {
          throw new ServerException("Could not access " + AFUNIXRMIService.class.getName(), e);
        }
      }

      return registry;
    }
  }

  private AFUNIXRegistry locateRegistry() throws RemoteException {
    Registry regImpl = LocateRegistry.getRegistry(null, registryPort, socketFactory);
    return regImpl == null ? null : new AFUNIXRegistry(this, regImpl);
  }

  /**
   * Shuts this RMI Registry down.
   * 
   * @throws RemoteException if the operation fails.
   */
  public void shutdownRegistry() throws RemoteException {
    synchronized (AFUNIXNaming.class) {
      if (registry == null) {
        return;
      }

      AFUNIXRegistry registryToBeClosed = registry;
      AFUNIXRMIService rmiServiceToBeClosed = rmiService;

      if (!registryToBeClosed.isLocal()) {
        if (!isRemoteShutdownAllowed()) {
          throw new ServerException("The server refuses to be shutdown remotely");
        }
        setRegistry(null);

        try {
          shutdownViaRMIService(registryToBeClosed, rmiServiceToBeClosed);
        } catch (Exception e) {
          // ignore
        }
        return;
      }

      setRegistry(null);

      if (!shutdownInProgress.compareAndSet(false, true)) {
        return;
      }
      try {
        unexportRMIService(registryToBeClosed, (AFUNIXRMIServiceImpl) rmiServiceToBeClosed);
        forceUnexportBound(registryToBeClosed);
        closeSocketFactory();
        deleteSocketDir();

      } finally {
        shutdownInProgress.set(false);
      }
    }
  }

  private void unexportRMIService(AFUNIXRegistry reg, AFUNIXRMIServiceImpl serv)
      throws AccessException, RemoteException {
    if (serv != null) {
      serv.shutdownRegisteredCloseables();
    }

    try {
      if (serv != null) {
        unexportObject(serv);
      }
      reg.unbind(RMI_SERVICE_NAME);
    } catch (ShutdownException | NotBoundException e) {
      // ignore
    }
    this.rmiService = null;
  }

  private void forceUnexportBound(AFUNIXRegistry reg) {
    try {
      reg.forceUnexportBound();
    } catch (Exception e) {
      // ignore
    }
  }

  private void closeSocketFactory() {
    if (socketFactory != null) {
      try {
        socketFactory.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  private void deleteSocketDir() {
    if (deleteRegistrySocketDir && registrySocketDir != null) {
      try {
        Files.delete(registrySocketDir.toPath());
      } catch (IOException e) {
        // ignore
      }
    }
  }

  private void shutdownViaRMIService(AFUNIXRegistry reg, AFUNIXRMIService serv)
      throws RemoteException {
    try {
      if (serv == null) {
        serv = getRMIService(reg);
      }
      if (serv.isShutdownAllowed()) {
        serv.shutdown();
      }
    } catch (ServerException | ConnectIOException | NotBoundException e) {
      // ignore
    }
  }

  /**
   * Creates a new RMI {@link Registry}.
   * 
   * If there already was a registry created previously, it is shut down and replaced by the current
   * one.
   * 
   * Use {@link #getRegistry()} to try to reuse an existing registry.
   * 
   * @return The registry
   * @throws RemoteException if the operation fails.
   * @see #getRegistry()
   */
  public AFUNIXRegistry createRegistry() throws RemoteException {
    synchronized (AFUNIXNaming.class) {
      AFUNIXRegistry existingRegistry = registry;
      if (existingRegistry == null) {
        try {
          existingRegistry = getRegistry(false);
        } catch (ServerException e) {
          Throwable cause = e.getCause();
          if (cause instanceof NotBoundException || cause instanceof ConnectIOException) {
            existingRegistry = null;
          } else {
            throw e;
          }
        }
      }
      if (existingRegistry != null) {
        if (!isRemoteShutdownAllowed()) {
          throw new ServerException("The server refuses to be shutdown remotely");
        }
        shutdownRegistry();
      }

      if (registrySocketDir != null && !registrySocketDir.mkdirs() && !registrySocketDir
          .isDirectory()) {
        throw new ServerException("Cannot create socket directory:" + registrySocketDir);
      }
      setRegistry(new AFUNIXRegistry(this, LocateRegistry.createRegistry(registryPort,
          socketFactory, socketFactory)));

      final AFUNIXRMIService service = new AFUNIXRMIServiceImpl(this);
      UnicastRemoteObject.exportObject(service, servicePort, socketFactory, socketFactory);

      rebindRMIService(service);

      return registry;
    }
  }

  /**
   * Checks if this {@link AFUNIXNaming} instance can be shut down remotely.
   * 
   * @return {@code true} if remote shutdown is allowed.
   */
  public boolean isRemoteShutdownAllowed() {
    return remoteShutdownAllowed;
  }

  /**
   * Controls whether this {@link AFUNIXNaming} instance can be shut down remotely.
   * 
   * @param remoteShutdownAllowed {@code true} if remote shutdown is allowed.
   */
  public void setRemoteShutdownAllowed(boolean remoteShutdownAllowed) {
    this.remoteShutdownAllowed = remoteShutdownAllowed;
  }

  /**
   * Exports and binds the given Remote object to the given name, using the given
   * {@link AFUNIXNaming} setup.
   * 
   * @param name The name to use to bind the object in the registry.
   * @param obj The object to export and bind.
   * @throws RemoteException if the operation fails.
   * @throws AlreadyBoundException if there already was something bound at that name
   */
  public void exportAndBind(String name, Remote obj) throws RemoteException, AlreadyBoundException {
    exportObject(obj, getSocketFactory());

    getRegistry().bind(name, obj);
  }

  /**
   * Exports and re-binds the given Remote object to the given name, using the given
   * {@link AFUNIXNaming} setup.
   * 
   * @param name The name to use to bind the object in the registry.
   * @param obj The object to export and bind.
   * @throws RemoteException if the operation fails.
   */
  public void exportAndRebind(String name, Remote obj) throws RemoteException {
    exportObject(obj, getSocketFactory());

    getRegistry().rebind(name, obj);
  }

  /**
   * Forcibly un-exports the given object, if it exists, and unbinds the object from the registry
   * (otherwise returns without an error).
   *
   * @param name The name used to bind the object.
   * @param obj The object to un-export.
   * @throws RemoteException if the operation fails.
   */
  public void unexportAndUnbind(String name, Remote obj) throws RemoteException {
    unexportObject(obj);
    try {
      unbind(name);
    } catch (MalformedURLException | NotBoundException e) {
      // ignore
    }
  }

  /**
   * Exports the given Remote object, using the given socket factory and a randomly assigned port.
   * 
   * NOTE: This helper function can also be used for regular RMI servers.
   * 
   * @param obj The object to export.
   * @param socketFactory The socket factory to use.
   * @return The remote stub.
   * @throws RemoteException if the operation fails.
   */
  public static Remote exportObject(Remote obj, RMISocketFactory socketFactory)
      throws RemoteException {
    return UnicastRemoteObject.exportObject(obj, 0, socketFactory, socketFactory);
  }

  /**
   * Forcibly un-exports the given object, if it exists (otherwise returns without an error). This
   * should be called upon closing a {@link Closeable} {@link Remote} object.
   * 
   * NOTE: This helper function can also be used for regular RMI servers.
   * 
   * @param obj The object to un-export.
   */
  public static void unexportObject(Remote obj) {
    try {
      UnicastRemoteObject.unexportObject(obj, true);
    } catch (NoSuchObjectException e) {
      // ignore
    }
  }

  private void setRegistry(AFUNIXRegistry registry) {
    synchronized (AFUNIXNaming.class) {
      this.registry = registry;
      if (registry == null) {
        rmiService = null;
      } else if (registry.isLocal()) {
        closeUponRuntimeShutdown();
      }
    }
  }
}
