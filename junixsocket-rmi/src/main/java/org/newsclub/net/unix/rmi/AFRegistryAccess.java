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

import java.net.MalformedURLException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;

abstract class AFRegistryAccess {
  /**
   * Returns a reference to the existing RMI registry.
   *
   * If there's no registry running at this port, an exception is thrown.
   *
   * @return The registry.
   * @throws RemoteException If there was a problem.
   */
  public abstract AFRegistry getRegistry() throws RemoteException;

  /**
   * Convenience method for {@code getRegistry().lookup}.
   *
   * @param name the name for the remote reference to look up
   * @return The instance
   * @throws NotBoundException upon error
   * @throws MalformedURLException upon error
   * @throws RemoteException upon error
   * @see AFRegistry#lookup(String)
   */
  public Remote lookup(String name) throws NotBoundException, MalformedURLException,
      RemoteException {
    return getRegistry().lookup(name);
  }

  /**
   * Convenience method for {@code getRegistry().lookup}.
   *
   * @param name the name for the remote reference to look up
   * @param timeout The timeout value.
   * @param unit The timeout unit.
   * @return The instance
   * @throws NotBoundException upon error
   * @throws MalformedURLException upon error
   * @throws RemoteException upon error
   * @see AFRegistry#lookup(String, long, TimeUnit)
   */
  public Remote lookup(String name, long timeout, TimeUnit unit) throws NotBoundException,
      MalformedURLException, RemoteException {
    return getRegistry().lookup(name, timeout, unit);
  }

  /**
   * Convenience method for {@code getRegistry().unbind}.
   *
   * @param name the name for the remote reference to unbind
   * @throws RemoteException upon error
   * @throws NotBoundException upon error
   * @throws MalformedURLException upon error
   * @see AFRegistry#unbind(String)
   */
  public void unbind(String name) throws RemoteException, NotBoundException, MalformedURLException {
    getRegistry().unbind(name);
  }

  /**
   * Convenience method for {@code getRegistry().bind}.
   *
   * @param name the name for the remote reference to bind
   * @param obj the remote reference to bind
   * @throws RemoteException upon error
   * @throws AlreadyBoundException upon error
   * @throws MalformedURLException upon error
   * @see AFRegistry#bind(String, Remote)
   */
  public void bind(String name, Remote obj) throws AlreadyBoundException, MalformedURLException,
      RemoteException {
    getRegistry().bind(name, obj);
  }

  /**
   * Convenience method for {@code getRegistry().rebind}.
   *
   * @param name the name for the remote reference to rebind
   * @param obj the remote reference to rebind
   * @throws RemoteException upon error
   * @throws MalformedURLException upon error
   * @see AFRegistry#rebind(String, Remote)
   */
  public void rebind(String name, Remote obj) throws MalformedURLException, RemoteException {
    getRegistry().rebind(name, obj);
  }

  /**
   * Convenience method for {@code getRegistry().list}.
   *
   * Unlike {@link AFRegistry#list()}, in case the registry has been shut down already, an empty
   * array is returned.
   *
   * @return an array of the names bound in this registry
   * @throws RemoteException upon error
   * @throws AccessException upon error
   */
  public String[] list() throws RemoteException, AccessException {
    try {
      return getRegistry().list();
    } catch (ShutdownException e) {
      return new String[0];
    }
  }
}
