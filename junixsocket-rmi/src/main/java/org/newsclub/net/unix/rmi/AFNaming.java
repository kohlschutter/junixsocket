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

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
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

import org.eclipse.jdt.annotation.NonNull;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownHook;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * The {@link AFSocket}-compatible equivalent of {@link Naming}. Use this class for accessing RMI
 * registries that are reachable by {@link AFSocket}s.
 *
 * @author Christian Kohlschütter
 */
public abstract class AFNaming extends AFRegistryAccess {
  private static final String RMI_SERVICE_NAME = AFRMIService.class.getName();

  private static final Map<AFNamingRef, AFNaming> INSTANCES = new HashMap<>();

  private AFRegistry registry = null;
  private AFRMIService rmiService = null;
  private final int registryPort;
  private final int servicePort;
  AFRMISocketFactory socketFactory;
  private boolean remoteShutdownAllowed = true;
  private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
  private final AtomicBoolean addedShutdownHook = new AtomicBoolean(false);

  /**
   * Creates a new naming instance with the given ports.
   *
   * @param registryPort The registry port.
   * @param servicePort The port for AFRMIService.
   * @throws IOException on error.
   */
  protected AFNaming(final int registryPort, final int servicePort) throws IOException {
    super();
    this.registryPort = registryPort;
    this.servicePort = servicePort;
  }

  /**
   * Creates a new {@link AFRegistry} given a {@link Registry} implementation.
   *
   * @param impl The implementation.
   * @return The new {@link AFRegistry} instance.
   * @throws RemoteException on error.
   */
  protected abstract AFRegistry newAFRegistry(Registry impl) throws RemoteException;

  /**
   * Creates or returns the {@link AFRMISocketFactory} to be used with this instance.
   *
   * @return The socket factory.
   * @throws IOException on error.
   */
  protected abstract AFRMISocketFactory initSocketFactory() throws IOException;

  @SuppressWarnings("unchecked")
  static <T extends AFNaming> T getInstance(final int registryPort,
      @NonNull AFNamingProvider<T> provider) throws RemoteException {
    Objects.requireNonNull(provider);
    final AFNamingRef sap = new AFNamingRef(provider, registryPort);
    T instance;
    synchronized (AFNaming.class) {
      instance = (T) INSTANCES.get(sap);
      if (instance == null) {
        try {
          instance = provider.newInstance(registryPort);
          Objects.requireNonNull(instance);
          instance.socketFactory = instance.initSocketFactory();
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
   * Returns the {@link AFRMISocketFactory} associated with this instance.
   *
   * @return The {@link AFRMISocketFactory}.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public synchronized AFRMISocketFactory getSocketFactory() {
    return socketFactory;
  }

  /**
   * Returns the registry port.
   *
   * @return The port.
   */
  public final int getRegistryPort() {
    return registryPort;
  }

  AFRMIService getRMIService() throws RemoteException, NotBoundException {
    return getRMIService(getRegistry());
  }

  AFRMIService getRMIService(AFRegistry reg) throws RemoteException, NotBoundException {
    if (rmiService == null) {
      this.rmiService = getRMIServiceFromRegistry(reg);
    }
    return rmiService;
  }

  AFRMIService getRMIServiceFromRegistry(AFRegistry reg) throws RemoteException, NotBoundException {
    AFRMIService service;
    service = (AFRMIService) reg.lookup(RMI_SERVICE_NAME, 5, TimeUnit.SECONDS);
    this.remoteShutdownAllowed = service.isShutdownAllowed();
    return service;
  }

  private void closeUponRuntimeShutdown() {
    if (addedShutdownHook.compareAndSet(false, true)) {
      ShutdownHookSupport.addWeakShutdownHook(new ShutdownHook() {

        @Override
        public void onRuntimeShutdown(Thread thread) throws IOException {
          synchronized (AFNaming.class) {
            if (registry != null && registry.isLocal()) {
              shutdownRegistry();
            }
          }
        }
      });
    }
  }

  private void rebindRMIService(final AFRMIService assigner) throws RemoteException {
    rmiService = assigner;
    getRegistry().rebind(RMI_SERVICE_NAME, assigner);
  }

  @Override
  public AFRegistry getRegistry() throws RemoteException {
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
  public AFRegistry getRegistry(long timeout, TimeUnit unit) throws RemoteException {
    if (shutdownInProgress.get()) {
      throw new ShutdownException();
    }
    synchronized (AFNaming.class) {
      AFRegistry reg = getRegistry(false);
      if (reg == null) {
        reg = openRegistry(timeout, unit);
      }
      return reg;
    }
  }

  /**
   * Tries to access the registry, waiting some time if necessary.
   *
   * @param timeout The timeout.
   * @param unit The unit for the timeout.
   * @return The registry instance.
   * @throws RemoteException on error.
   */
  protected abstract AFRegistry openRegistry(long timeout, TimeUnit unit) throws RemoteException;

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
  public AFRegistry getRegistry(boolean create) throws RemoteException {
    if (shutdownInProgress.get()) {
      throw new ShutdownException();
    }
    synchronized (AFNaming.class) {
      if (registry != null) {
        return registry;
      } else if (!socketFactory.hasRegisteredPort(registryPort)) {
        return create ? createRegistry() : null;
      }

      AFRegistry reg = locateRegistry();
      setRegistry(reg);

      try {
        getRMIService(reg);
      } catch (NotBoundException | NoSuchObjectException | ConnectIOException e) {
        if (create) {
          setRegistry(null);
          return createRegistry();
        } else {
          throw new ServerException("Could not access " + AFRMIService.class.getName(), e);
        }
      }

      return registry;
    }
  }

  private AFRegistry locateRegistry() throws RemoteException {
    Registry regImpl = LocateRegistry.getRegistry(null, registryPort, socketFactory);
    return regImpl == null ? null : newAFRegistry(regImpl);
  }

  /**
   * Shuts this RMI Registry down.
   *
   * @throws RemoteException if the operation fails.
   */
  public void shutdownRegistry() throws RemoteException {
    synchronized (AFNaming.class) {
      if (registry == null) {
        return;
      }

      AFRegistry registryToBeClosed = registry;
      AFRMIService rmiServiceToBeClosed = rmiService;

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
        unexportRMIService(registryToBeClosed, (AFRMIServiceImpl) rmiServiceToBeClosed);
        forceUnexportBound(registryToBeClosed);
        closeSocketFactory();
        shutdownRegistryFinishingTouches();
      } finally {
        shutdownInProgress.set(false);
      }
    }
  }

  /**
   * Called by {@link #shutdownRegistry()} as the final step.
   */
  protected abstract void shutdownRegistryFinishingTouches();

  private void unexportRMIService(AFRegistry reg, AFRMIServiceImpl serv) throws AccessException,
      RemoteException {
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

  private void forceUnexportBound(AFRegistry reg) {
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

  private void shutdownViaRMIService(AFRegistry reg, AFRMIService serv) throws RemoteException {
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
  public AFRegistry createRegistry() throws RemoteException {
    synchronized (AFNaming.class) {
      AFRegistry existingRegistry = registry;
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

      initRegistryPrerequisites();

      setRegistry(newAFRegistry(LocateRegistry.createRegistry(registryPort, socketFactory,
          socketFactory)));

      final AFRMIService service = new AFRMIServiceImpl(this);
      UnicastRemoteObject.exportObject(service, servicePort, socketFactory, socketFactory);

      rebindRMIService(service);

      return registry;
    }
  }

  /**
   * Called by {@link #createRegistry()} right before creating/setting the registry.
   *
   * @throws ServerException on error.
   */
  protected abstract void initRegistryPrerequisites() throws ServerException;

  /**
   * Checks if this {@link AFNaming} instance can be shut down remotely.
   *
   * @return {@code true} if remote shutdown is allowed.
   */
  public boolean isRemoteShutdownAllowed() {
    return remoteShutdownAllowed;
  }

  /**
   * Controls whether this {@link AFNaming} instance can be shut down remotely.
   *
   * @param remoteShutdownAllowed {@code true} if remote shutdown is allowed.
   */
  public void setRemoteShutdownAllowed(boolean remoteShutdownAllowed) {
    this.remoteShutdownAllowed = remoteShutdownAllowed;
  }

  /**
   * Exports and binds the given Remote object to the given name, using the given {@link AFNaming}
   * setup.
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
   * {@link AFNaming} setup.
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

  private void setRegistry(AFRegistry registry) {
    synchronized (AFNaming.class) {
      this.registry = registry;
      if (registry == null) {
        rmiService = null;
      } else if (registry.isLocal()) {
        closeUponRuntimeShutdown();
      }
    }
  }
}
