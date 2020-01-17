/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownHook;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownThread;

/**
 * The {@link AFUNIXSocket}-compatible equivalent of {@link Naming}. Use this class for accessing
 * RMI registries that are reachable by {@link AFUNIXSocket}s.
 * 
 * @author Christian Kohlschütter
 */
public final class AFUNIXNaming implements ShutdownHook {
  private static final String RMI_SERVICE_NAME = AFUNIXRMIService.class.getName();
  private static final String PROP_RMI_SOCKET_DIR = "org.newsclub.net.unix.rmi.socketdir";

  private static final File DEFAULT_SOCKET_DIRECTORY = new File(System.getProperty(
      PROP_RMI_SOCKET_DIR, "/tmp"));

  private static final Map<AFUNIXNamingRef, AFUNIXNaming> INSTANCES = new HashMap<>();

  private AFUNIXRegistry registry = null;
  private AFUNIXRMIService rmiService = null;
  private File registrySocketDir;
  private final int registryPort;
  private final int servicePort;
  private AFUNIXRMISocketFactory socketFactory;
  private boolean deleteRegistrySocketDir = false;
  private boolean remoteShutdownAllowed = true;

  private AFUNIXNaming(final File socketDir, final int port, final String socketPrefix,
      final String socketSuffix) throws IOException {
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

  public static AFUNIXNaming getInstance(File socketDir, final int registryPort,
      String socketPrefix, String socketSuffix) throws RemoteException {
    if (socketDir == null) {
      socketDir = DEFAULT_SOCKET_DIRECTORY;
      if (!socketDir.mkdirs() && !socketDir.isDirectory()) {
        throw new RemoteException("Cannot create directory for temporary file: " + socketDir);
      }

      if (socketPrefix == null) {
        File tempFile;
        try {
          tempFile = File.createTempFile("jux", "-", socketDir);
        } catch (IOException e) {
          throw new RemoteException("Cannot create temporary file: " + e.getMessage(), e);
        }
        if (!tempFile.delete()) {
          tempFile.deleteOnExit();
        }

        socketPrefix = tempFile.getName();
      }
    }
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

  public AFUNIXRMISocketFactory getSocketFactory() {
    return socketFactory;
  }

  public File getRegistrySocketDir() {
    return registrySocketDir;
  }

  public int getRegistryPort() {
    return registryPort;
  }

  AFUNIXRMIService getRMIService() throws RemoteException, NotBoundException {
    if (rmiService == null) {
      rmiService = getRMIServiceFromRegistry();
    }
    return rmiService;
  }

  AFUNIXRMIService getRMIServiceFromRegistry() throws RemoteException, NotBoundException {
    AFUNIXRMIService service;
    synchronized (AFUNIXRMIService.class) {
      try {
        service = (AFUNIXRMIService) lookup(RMI_SERVICE_NAME);
      } catch (MalformedURLException e) {
        throw new RemoteException(e.getMessage(), e);
      }
      return service;
    }
  }

  private void closeUponRuntimeShutdown() {
    ShutdownHookSupport.addWeakShutdownHook(this);
  }

  private void rebindRMIService(final AFUNIXRMIService assigner) throws RemoteException {
    rmiService = assigner;
    getRegistry().rebind(RMI_SERVICE_NAME, assigner);
  }

  /**
   * Returns a reference to the existing RMI registry.
   * 
   * If there's no registry running at this port, an exception is thrown.
   * 
   * @return The registry.
   * @throws RemoteException If there was a problem.
   */
  public synchronized AFUNIXRegistry getRegistry() throws RemoteException {
    AFUNIXRegistry reg = getRegistry(false);
    if (reg == null) {
      throw new RemoteException("Could not find registry at " + socketFactory.getFile(
          registryPort));
    }
    return reg;
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
  public synchronized AFUNIXRegistry getRegistry(boolean create) throws RemoteException {
    if (registry != null) {
      return registry;
    } else if (!socketFactory.hasSocketFile(registryPort)) {
      return create ? createRegistry() : null;
    }

    Registry reg = LocateRegistry.getRegistry(null, registryPort, socketFactory);
    if (reg != null) {
      reg = new AFUNIXRegistry(this, reg);
    }
    this.registry = (AFUNIXRegistry) reg;

    AFUNIXRMIService service;
    try {
      service = getRMIService();
      this.remoteShutdownAllowed = service.isShutdownAllowed();
    } catch (ConnectIOException e) {
      if (create) {
        socketFactory.deleteSocketFile(registryPort);
        registry = null;
        return createRegistry();
      } else {
        throw new ServerException("Could not access " + AFUNIXRMIService.class.getName(), e);
      }
    } catch (NotBoundException e) {
      throw new ServerException("Could not access " + AFUNIXRMIService.class.getName(), e);
    }

    return registry;
  }

  public Remote lookup(String name) throws NotBoundException, MalformedURLException,
      RemoteException {
    return getRegistry().lookup(name);
  }

  public void unbind(String name) throws RemoteException, NotBoundException, MalformedURLException {
    getRegistry().unbind(name);
  }

  public void bind(String name, Remote obj) throws AlreadyBoundException, MalformedURLException,
      RemoteException {
    getRegistry().bind(name, obj);
  }

  public void rebind(String name, Remote obj) throws MalformedURLException, RemoteException {
    getRegistry().rebind(name, obj);
  }

  /**
   * Shuts this RMI Registry down.
   * 
   * @throws RemoteException if the operation fails.
   */
  public synchronized void shutdownRegistry() throws RemoteException {
    if (registry == null) {
      return;
    }

    AFUNIXRegistry reg = registry;
    if (!reg.isRemoteServer()) {
      reg.forceUnexportBound();
      shutdownViaRMIService();
      return;
    }

    AFUNIXRMIServiceImpl serviceImpl = (AFUNIXRMIServiceImpl) rmiService;
    if (serviceImpl == null) {
      return;
    }
    serviceImpl.shutdownRegisteredCloseables();

    try {
      unexportObject(rmiService);
      registry.unbind(RMI_SERVICE_NAME);
    } catch (NotBoundException e) {
      // ignore
    }

    reg.forceUnexportBound();

    rmiService = null;

    if (socketFactory != null) {
      socketFactory.deleteSocketFile(registryPort);
      socketFactory.deleteSocketFile(servicePort);
      socketFactory.close();
      socketFactory = null;
    }

    if (deleteRegistrySocketDir && registrySocketDir != null) {
      try {
        Files.delete(registrySocketDir.toPath());
      } catch (IOException e) {
        // ignore
      }
      registrySocketDir = null;
    }
  }

  private void shutdownViaRMIService() throws RemoteException {
    AFUNIXRMIService existingAssigner;
    try {
      existingAssigner = getRMIServiceFromRegistry();
    } catch (ConnectIOException | NotBoundException e) {
      existingAssigner = null;
    }
    if (existingAssigner != null) {
      try {
        existingAssigner.shutdown();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Creates a new RMI {@link Registry}.
   * 
   * Use {@link #getRegistry()} to try to reuse an existing registry.
   * 
   * @return The registry
   * @throws RemoteException if the operation fails.
   * @see #getRegistry()
   */
  public synchronized AFUNIXRegistry createRegistry() throws RemoteException {
    if (registry != null) {
      throw new RemoteException("The Registry is already created: " + registry);
    }

    Registry existingRegistry;
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
    if (existingRegistry != null) {
      if (!isRemoteShutdownAllowed()) {
        throw new ServerException("The server refuses to be shutdown remotely");
      }
      shutdownViaRMIService();
    }

    socketFactory.deleteStaleFiles();

    this.registry = new AFUNIXRegistry(this, LocateRegistry.createRegistry(registryPort,
        socketFactory, socketFactory));

    final AFUNIXRMIService service = new AFUNIXRMIServiceImpl(this);
    UnicastRemoteObject.exportObject(service, servicePort, socketFactory, socketFactory);
    rebindRMIService(service);

    closeUponRuntimeShutdown();

    return registry;
  }

  public boolean isRemoteShutdownAllowed() {
    return remoteShutdownAllowed;
  }

  public void setRemoteShutdownAllowed(boolean remoteShutdownAllowed) {
    this.remoteShutdownAllowed = remoteShutdownAllowed;
  }

  @Override
  public void onRuntimeShutdown(Thread thread) {
    if (thread != Thread.currentThread() || !(thread instanceof ShutdownThread)) {
      throw new IllegalStateException("Illegal caller");
    }
    try {
      shutdownRegistry();
    } catch (IOException e) {
      // ignore
    }
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
}
