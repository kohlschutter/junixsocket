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

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * A very simple implementation of a {@link AFUNIXRMIService}.
 * 
 * @author Christian Kohlschütter
 */
final class AFUNIXRMIServiceImpl implements AFUNIXRMIService {
  private final BitSet ports = new BitSet(1000);
  private final WeakReference<AFUNIXNaming> naming;
  private final List<WeakReference<Closeable>> closeAtShutdown = new ArrayList<>();

  public AFUNIXRMIServiceImpl(AFUNIXNaming naming) {
    this.naming = new WeakReference<>(naming);
  }

  private/* synchronized */int randomPort() {
    final Random random = new Random();
    int maxRandom = ports.size();

    int port;
    for (int i = 0; i < 3; i++) {
      port = ports.nextClearBit(random.nextInt(maxRandom));
      if (port < maxRandom) {
        return port;
      } else {
        maxRandom = port;
        if (maxRandom == 0) {
          break;
        }
      }
    }
    return ports.nextClearBit(0);
  }

  @Override
  public synchronized int newPort() throws IOException {
    int port = randomPort();
    ports.set(port);
    port += AFUNIXRMIPorts.ANONYMOUS_PORT_BASE;
    return port;
  }

  @Override
  public synchronized void returnPort(int port) throws IOException {
    ports.clear(port - AFUNIXRMIPorts.ANONYMOUS_PORT_BASE);
  }

  @Override
  public IntStream openPorts() throws RemoteException {
    return ports.stream().map((int v) -> {
      return v + AFUNIXRMIPorts.ANONYMOUS_PORT_BASE;
    });
  }

  @Override
  public void shutdown() throws RemoteException {
    AFUNIXNaming namingInstance = naming.get();
    if (namingInstance != null) {
      if (!namingInstance.isRemoteShutdownAllowed()) {
        throw new ServerException("Remote shutdown is disabled");
      }
      namingInstance.shutdownRegistry();
    }
  }

  @Override
  public boolean isShutdownAllowed() throws RemoteException {
    AFUNIXNaming namingInstance = naming.get();
    if (namingInstance != null) {
      return namingInstance.isRemoteShutdownAllowed();
    } else {
      return true;
    }
  }

  @Override
  public void registerForShutdown(Closeable closeable) throws RemoteException {
    synchronized (closeAtShutdown) {
      unregisterForShutdown(closeable);
      closeAtShutdown.add(new WeakReference<Closeable>(closeable));
    }
  }

  @Override
  public void unregisterForShutdown(Closeable closeable) throws RemoteException {
    synchronized (closeAtShutdown) {
      Objects.requireNonNull(closeable);
      for (Iterator<WeakReference<Closeable>> it = closeAtShutdown.iterator(); it.hasNext();) {
        if (it.next().get() == closeable) {
          it.remove();
          return;
        }
      }
    }
  }

  void shutdownRegisteredCloseables() {
    List<WeakReference<Closeable>> list;
    synchronized (closeAtShutdown) {
      list = new ArrayList<>(closeAtShutdown);
      closeAtShutdown.clear();
    }

    ExecutorService executor = Executors.newCachedThreadPool();
    for (WeakReference<Closeable> ref : list) {
      executor.submit(new Runnable() {
        @Override
        public void run() {
          @SuppressWarnings("resource")
          Closeable cl = ref.get();
          if (cl == null) {
            return;
          }
          try {
            cl.close();
          } catch (NoSuchObjectException e) {
            // ignore
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      });
    }
    executor.shutdown();
  }
}
