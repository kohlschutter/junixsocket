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

import java.rmi.RemoteException;
import java.rmi.registry.Registry;

/**
 * A wrapper for RMI registries, both remote and local, to allow for a clean removal of bound
 * resources upon shutdown.
 *
 * This subclass mostly exists for backwards compatibility.
 *
 * @author Christian Kohlschütter
 */
public class AFUNIXRegistry extends AFRegistry {
  AFUNIXRegistry(AFNaming naming, Registry impl) throws RemoteException {
    super(naming, impl);
  }
}
