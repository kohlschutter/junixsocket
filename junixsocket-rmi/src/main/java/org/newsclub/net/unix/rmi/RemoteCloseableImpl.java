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
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;

/**
 * The default implementation of {@link RemoteCloseable}.
 *
 * @author Christian Kohlschütter
 * @see RemoteCloseable
 * @param <T> The resource type.
 */
public final class RemoteCloseableImpl<T> implements RemoteCloseable<T> {
  private final T remote;

  /**
   * Created a new instance.
   *
   * @param socketFactory The socket factory.
   * @param obj The object to wrap.
   * @throws RemoteException on error.
   */
  public RemoteCloseableImpl(RMISocketFactory socketFactory, T obj) throws RemoteException {
    this.remote = obj;
    AFNaming.exportObject(this, socketFactory);
  }

  @Override
  public void close() throws IOException {
    AFNaming.unexportObject(this);
    doClose(remote);
  }

  /**
   * Closes the given object.
   *
   * @param obj The object to close.
   * @throws IOException on error.
   */
  private void doClose(T obj) throws IOException {
    if (obj instanceof Closeable) {
      ((Closeable) obj).close();
    }
  }

  @Override
  public T get() throws IOException {
    return remote;
  }
}
