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
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.stream.IntStream;

/**
 * The {@link AFRMIService} assigns and keeps track of anonymous ports, among other things.
 *
 * This feature is to be used by {@link AFRMISocketFactory} only.
 *
 * @author Christian Kohlschütter
 */
public interface AFRMIService extends Remote {
  /**
   * Registers a new anonymous port and returns it. When the port is not required anymore, it must
   * be returned via {@link #returnPort(int)}.
   *
   * @return The new port.
   * @throws IOException if the operation fails.
   */
  int newPort() throws IOException;

  /**
   * Returns a previously registered port. No error is thrown if the given port has not been
   * assigned before.
   *
   * @param port The port.
   * @throws IOException if the operation fails.
   */
  void returnPort(int port) throws IOException;

  /**
   * Returns a stream of open ports.
   *
   * @return A sequence of open ports.
   * @throws RemoteException if the operation fails.
   */
  IntStream openPorts() throws RemoteException;

  /**
   * Indicates whether a remote-shutdown of the RMI registry is allowed.
   *
   * NOTE: A call to {@link #shutdown()} may or may not succeed regardless.
   *
   * @return Indication of whether a remote-shutdown of the RMI registry is allowed.
   * @throws RemoteException if the operation fails.
   */
  boolean isShutdownAllowed() throws RemoteException;

  /**
   * Asks that this RMI registry gets shut down.
   *
   * @throws RemoteException if the operation fails.
   */
  void shutdown() throws RemoteException;

  /**
   * Adds the given {@link Closeable} to the list of instances to be closed upon shutdown of the RMI
   * registry.
   *
   * @param closeable The instance.
   * @throws RemoteException if the operation fails.
   */
  void registerForShutdown(Closeable closeable) throws RemoteException;

  /**
   * Removes the given {@link Closeable} from the list of instances to be closed upon shutdown of
   * the RMI registry.
   *
   * No error is returned if the given element was not registered before.
   *
   * @param closeable The instance.
   * @throws RemoteException if the operation fails.
   */
  void unregisterForShutdown(Closeable closeable) throws RemoteException;
}
