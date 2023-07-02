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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A very simple implementation of a {@link AFRMIService}.
 *
 * @author Christian Kohlschütter
 */
final class AFRMIServiceImpl implements AFRMIService {
  private final BitSet ports = new BitSet(1000);
  private final WeakReference<AFNaming> naming;
  private final List<WeakReference<Closeable>> closeAtShutdown = new ArrayList<>();

  public AFRMIServiceImpl(AFNaming naming) {
    this.naming = new WeakReference<>(naming);
  }

  @SuppressFBWarnings("DMI_RANDOM_USED_ONLY_ONCE")
  private/* synchronized */int randomPort() {
    int maxRandom = ports.size();

    Random random = ThreadLocalRandom.current();

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
    port += RMIPorts.ANONYMOUS_PORT_BASE;
    return port;
  }

  @Override
  public synchronized void returnPort(int port) throws IOException {
    ports.clear(port - RMIPorts.ANONYMOUS_PORT_BASE);
  }

  @Override
  public IntStream openPorts() throws RemoteException {
    return ports.stream().map((int v) -> {
      return v + RMIPorts.ANONYMOUS_PORT_BASE;
    });
  }

  @Override
  public void shutdown() throws RemoteException {
    AFNaming namingInstance = naming.get();
    if (namingInstance != null) {
      if (!namingInstance.isRemoteShutdownAllowed()) {
        throw new ServerException("Remote shutdown is disabled");
      }
      try {
        namingInstance.shutdownRegistry();
      } catch (ShutdownException e) {
        // already shut down
      }
    }
  }

  @Override
  public boolean isShutdownAllowed() throws RemoteException {
    AFNaming namingInstance = naming.get();
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
      closeAtShutdown.add(new WeakReference<>(closeable));
    }
  }

  @Override
  public void unregisterForShutdown(Closeable closeable) throws RemoteException {
    synchronized (closeAtShutdown) {
      Objects.requireNonNull(closeable);
      for (Iterator<WeakReference<Closeable>> it = closeAtShutdown.iterator(); it.hasNext();) {
        if (closeable.equals(it.next().get())) {
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
