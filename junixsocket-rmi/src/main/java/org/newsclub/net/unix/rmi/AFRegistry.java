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

import java.io.IOException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteServer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownHook;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A wrapper for RMI registries, both remote and local, to allow for a clean removal of bound
 * resources upon shutdown.
 *
 * @author Christian Kohlschütter
 */
public abstract class AFRegistry implements Registry {
  final RemoteCloseable<?> boundCloser;

  private final Registry impl;
  private final Map<String, Remote> bound = new HashMap<>();
  private final AFNaming naming;
  private final AtomicBoolean boundCloserExported = new AtomicBoolean(false);

  AFRegistry(AFNaming naming, Registry impl) throws RemoteException {
    this.naming = naming;
    this.impl = impl;
    this.boundCloser = new RemoteCloseable<Void>() {
      @Override
      public Void get() throws IOException {
        return null;
      }

      @Override
      public void close() throws IOException {
        AFRegistry.this.forceUnexportBound();
      }
    };

    if (isLocal()) {
      ShutdownHookSupport.addWeakShutdownHook(new ShutdownHook() {
        @Override
        public void onRuntimeShutdown(Thread thread) {
          forceUnexportBound();
        }
      });
    }
  }

  /**
   * Returns {@code true} if the wrapped Registry instance is a locally created
   * {@link RemoteServer}.
   *
   * @return {@code true} if wrapped instance is a locally created {@link RemoteServer}.
   * @see #isLocal()
   */
  @Deprecated
  public boolean isRemoteServer() {
    return isLocal();
  }

  /**
   * Returns {@code true} if the wrapped Registry instance is locally created.
   *
   * @return {@code true} if wrapped instance is locally created.
   */
  public final boolean isLocal() {
    return (impl instanceof RemoteServer);
  }

  /**
   * Returns the {@link AFNaming} instance responsible for this registry.
   *
   * @return The {@link AFNaming} instance.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public AFNaming getNaming() {
    return naming;
  }

  @Override
  public Remote lookup(String name) throws RemoteException, NotBoundException, AccessException {
    return impl.lookup(name);
  }

  @Override
  public void bind(String name, Remote obj) throws RemoteException, AlreadyBoundException,
      AccessException {
    impl.bind(name, RemoteObject.toStub(obj));
    synchronized (bound) {
      bound.put(name, obj);
    }
    checkBound();
  }

  @Override
  public void unbind(String name) throws RemoteException, NotBoundException, AccessException {
    impl.unbind(name);
    synchronized (bound) {
      bound.remove(name);
    }
    checkBound();
  }

  @Override
  public void rebind(String name, Remote obj) throws RemoteException, AccessException {
    impl.rebind(name, RemoteObject.toStub(obj));
    synchronized (bound) {
      bound.put(name, obj);
    }
    checkBound();
  }

  @Override
  public String[] list() throws RemoteException, AccessException {
    return impl == null ? new String[0] : impl.list();
  }

  /**
   * Returns the remote reference bound to the specified <code>name</code> in this registry. If the
   * reference has not been bound yet, repeated attempts to resolve it are made until the specified
   * time elapses.
   *
   * @param name the name for the remote reference to look up
   * @param timeout The timeout value.
   * @param unit The timeout unit.
   *
   * @return a reference to a remote object
   *
   * @throws NotBoundException if <code>name</code> is not currently bound and couldn't be resolved
   *           in the specified time.
   *
   * @throws RemoteException if remote communication with the registry failed; if exception is a
   *           <code>ServerException</code> containing an <code>AccessException</code>, then the
   *           registry denies the caller access to perform this operation
   *
   * @throws AccessException if this registry is local and it denies the caller access to perform
   *           this operation
   *
   * @throws NullPointerException if <code>name</code> is <code>null</code>
   */
  public Remote lookup(String name, long timeout, TimeUnit unit) throws NotBoundException,
      RemoteException {
    long timeWait = unit.toMillis(timeout);

    Exception exFirst = null;
    do {
      try {
        return impl.lookup(name);
      } catch (NotBoundException | ConnectIOException | NoSuchObjectException e) {
        if (exFirst == null) {
          exFirst = e;
        }
      }

      try {
        Thread.sleep(Math.min(timeWait, 50));
      } catch (InterruptedException e1) {
        exFirst.addSuppressed(e1);
        break;
      }
      timeWait -= 50;
    } while (timeWait > 0);

    if (exFirst instanceof NotBoundException) {
      throw (NotBoundException) exFirst;
    } else if (exFirst instanceof RemoteException) {
      throw (RemoteException) exFirst;
    } else {
      throw new RemoteException("Lookup timed out");
    }
  }

  void forceUnexportBound() {
    final Map<String, Remote> map;
    synchronized (bound) {
      map = new HashMap<>(bound);
      bound.clear();
    }
    try {
      checkBound();
    } catch (RemoteException e1) {
      // ignore
    }
    for (Map.Entry<String, Remote> en : map.entrySet()) {
      String name = en.getKey();
      Remote obj = en.getValue();
      if (obj == null) {
        continue;
      }
      AFNaming.unexportObject(obj);
      try {
        unbind(name);
      } catch (RemoteException | NotBoundException e) {
        // ignore
      }

    }
    try {
      for (String list : list()) {
        try {
          unbind(list);
        } catch (RemoteException | NotBoundException e) {
          // ignore
        }
      }
    } catch (RemoteException e) {
      // ignore
    }

    AFNaming.unexportObject(this.impl);
    AFNaming.unexportObject(this);
  }

  private void checkBound() throws RemoteException {
    boolean empty;
    synchronized (bound) {
      empty = bound.isEmpty();
    }
    if (empty) {
      if (boundCloserExported.compareAndSet(true, false)) {
        AFRMIService service;
        try {
          service = naming.getRMIService(this);
          service.unregisterForShutdown(boundCloser);
        } catch (NoSuchObjectException | NotBoundException e) {
          return;
        } finally {
          AFNaming.unexportObject(boundCloser);
        }
      }
    } else if (boundCloserExported.compareAndSet(false, true)) {
      AFNaming.exportObject(boundCloser, naming.getSocketFactory());

      AFRMIService service;
      try {
        service = naming.getRMIService(this);
      } catch (NotBoundException e) {
        return;
      }
      service.registerForShutdown(boundCloser);
    }
  }
}
