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
import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * To be used by {@link RemoteCloseableTest}.
 *
 * @author Christian Kohlschütter
 */
abstract class RemoteCloseableThingImpl implements RemoteCloseableThing {
  private static final long serialVersionUID = 1L;

  private final AtomicInteger count = new AtomicInteger(0);

  protected RemoteCloseableThingImpl() throws RemoteException {
  }

  public void close() throws IOException {
    count.incrementAndGet();
  }

  int numberOfCloseCalls() {
    return count.get();
  }

  void resetCount() {
    count.set(0);
  }

  static class NotCloseableImpl extends RemoteCloseableThingImpl implements
      RemoteCloseableThing.NotCloseable {
    private static final long serialVersionUID = 1L;

    public NotCloseableImpl() throws RemoteException {
      super();
    }
  }

  static class IsCloseableImpl extends RemoteCloseableThingImpl implements IsCloseable {
    private static final long serialVersionUID = 1L;

    public IsCloseableImpl() throws RemoteException {
      super();
    }
  }
}
