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
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;

/**
 * Helper class for RMI remote objects.
 * 
 * @author Christian Kohlschütter
 */
public final class RemoteObjectUtil {
  private RemoteObjectUtil() {
    throw new UnsupportedOperationException("No instances");
  }

  /**
   * Exports the given Remote object, using the given socket factory and a randomly assigned port.
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
   * Exports and binds the given Remote object to the given name, using the given
   * {@link AFUNIXNaming} setup.
   * 
   * @param naming The {@link AFUNIXNaming} instance to use.
   * @param name The name to use to bind the object in the registry.
   * @param obj The object to export and bind.
   * @throws RemoteException if the operation fails.
   * @throws AlreadyBoundException if there already was something bound at that name
   */
  public static void exportAndBind(AFUNIXNaming naming, String name, Remote obj)
      throws RemoteException, AlreadyBoundException {
    RemoteObjectUtil.exportObject(obj, naming.getSocketFactory());

    naming.getRegistry().bind(name, obj);
  }

  /**
   * Exports and re-binds the given Remote object to the given name, using the given
   * {@link AFUNIXNaming} setup.
   * 
   * @param naming The {@link AFUNIXNaming} instance to use.
   * @param name The name to use to bind the object in the registry.
   * @param obj The object to export and bind.
   * @throws RemoteException if the operation fails.
   */
  public static void exportAndRebind(AFUNIXNaming naming, String name, Remote obj)
      throws RemoteException {
    RemoteObjectUtil.exportObject(obj, naming.getSocketFactory());

    naming.getRegistry().rebind(name, obj);
  }

  /**
   * Forcibly un-exports the given object, if it exists (otherwise returns without an error). This
   * should be called upon closing a {@link Closeable} {@link Remote} object.
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

  /**
   * Forcibly un-exports the given object, if it exists, and unbinds the object from the registry
   * (otherwise returns without an error).
   * 
   * @param obj The object to un-export.
   */
  public static void unexportAndUnbind(AFUNIXNaming naming, String name, Remote obj)
      throws RemoteException {
    unexportObject(obj);
    try {
      naming.unbind(name);
    } catch (MalformedURLException | NotBoundException e) {
      // ignore
    }
  }
}
