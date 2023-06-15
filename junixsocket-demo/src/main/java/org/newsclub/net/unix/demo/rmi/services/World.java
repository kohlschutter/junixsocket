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
package org.newsclub.net.unix.demo.rmi.services;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * A very simple "world" service.
 *
 * @author Christian Kohlschütter
 * @see HelloWorld
 */
public interface World extends Remote {
  /**
   * Returns "World" (or something else?).
   *
   * @return "World" (usually)
   * @throws RemoteException if the operation fails.
   */
  String world() throws RemoteException;
}
