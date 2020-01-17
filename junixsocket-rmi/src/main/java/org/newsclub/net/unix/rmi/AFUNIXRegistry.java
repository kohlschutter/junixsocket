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

import java.io.IOException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownHook;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownThread;

/**
 * A wrapper for RMI registries, both remote and local, to allow for a clean removal of bound
 * resources upon shutdown.
 * 
 * @author Christian Kohlschütter
 */
public final class AFUNIXRegistry implements Registry, ShutdownHook {
  final RemoteCloseable<?> boundCloser;

  private final Registry impl;
  private final Map<String, Remote> bound = new HashMap<>();
  private final AFUNIXNaming naming;
  private boolean boundCloserExported = false;

  AFUNIXRegistry(AFUNIXNaming naming, Registry impl) throws RemoteException {
    this.naming = naming;
    this.impl = impl;
    this.boundCloser = new RemoteCloseable<Void>() {
      @Override
      public Void get() throws IOException {
        return null;
      }

      @Override
      public void close() throws IOException {
        AFUNIXRegistry.this.forceUnexportBound();
        AFUNIXNaming.unexportObject(this);
      }
    };

    ShutdownHookSupport.addWeakShutdownHook(this);
  }

  /**
   * Returns {@code true} if the wrapped Registry instance is a locally created
   * {@link RemoteServer}.
   * 
   * @return {@code true} if wrapped instance is a locally created {@link RemoteServer}.
   */
  public boolean isRemoteServer() {
    return (impl instanceof RemoteServer);
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
      e1.printStackTrace();
      // ignore
    }
    for (Map.Entry<String, Remote> en : map.entrySet()) {
      String name = en.getKey();
      Remote obj = en.getValue();
      if (obj == null) {
        continue;
      }
      try {
        naming.unexportAndUnbind(name, obj);
      } catch (RemoteException e) {
        // ignore
      }
    }
  }

  Remote getInstance(String name) throws NoSuchObjectException {
    Remote remote;
    synchronized (bound) {
      remote = bound.get(name);
    }
    if (remote == null) {
      throw new NoSuchObjectException(name);
    }
    return remote;
  }

  void unexport(String name) throws NoSuchObjectException {
    UnicastRemoteObject.unexportObject(getInstance(name), true);
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
    return impl.list();
  }

  private void checkBound() throws RemoteException {
    boolean empty = false;
    synchronized (bound) {
      empty = bound.isEmpty();
    }
    synchronized (boundCloser) {
      if (empty) {
        if (boundCloserExported) {
          boundCloserExported = false;

          AFUNIXRMIService service;
          try {
            service = naming.getRMIService();
            service.unregisterForShutdown(boundCloser);
          } catch (NoSuchObjectException | NotBoundException e) {
            return;
          } finally {
            AFUNIXNaming.unexportObject(boundCloser);
          }
        }
      } else if (!boundCloserExported) {
        AFUNIXNaming.exportObject(boundCloser, naming.getSocketFactory());
        boundCloserExported = true;

        AFUNIXRMIService service;
        try {
          service = naming.getRMIService();
        } catch (NotBoundException e) {
          return;
        }
        service.registerForShutdown(boundCloser);
      }
    }
  }

  @Override
  public void onRuntimeShutdown(Thread thread) {
    if (thread != Thread.currentThread() | !(thread instanceof ShutdownThread)) {
      throw new IllegalStateException("Illegal caller");
    }
    forceUnexportBound();
  }
}
