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
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.StackTraceUtil;
import org.newsclub.net.unix.rmi.ShutdownHookSupport.ShutdownHook;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * An {@link RMISocketFactory} that supports {@link AFSocket}s.
 *
 * @author Christian Kohlschütter
 */
public abstract class AFRMISocketFactory extends RMISocketFactory implements Externalizable,
    Closeable {
  private static final long serialVersionUID = 1L;

  private transient AFRMIService rmiService = null;

  private Externables externables;
  private final transient Map<Integer, AFServerSocket<?>> openServerSockets = new HashMap<>();
  private final transient Set<AFSocket<?>> openSockets = new HashSet<>();

  private static final class Externables {
    private final AFNaming naming;
    private final RMIClientSocketFactory defaultClientFactory;
    private final RMIServerSocketFactory defaultServerFactory;

    private Externables(AFNaming naming, RMIClientSocketFactory defaultClientFactory,
        RMIServerSocketFactory defaultServerFactory) {
      this.naming = naming;
      this.defaultClientFactory = defaultClientFactory;
      this.defaultServerFactory = defaultServerFactory;
    }
  }

  /**
   * Constructor required per definition.
   *
   * @see RMISocketFactory
   */
  public AFRMISocketFactory() {
    this(null, null, null);
  }

  /**
   * Creates a new socket factory.
   *
   * @param naming The {@link AFNaming} instance to use.
   * @param defaultClientFactory The default {@link RMIClientSocketFactory}.
   * @param defaultServerFactory The default {@link RMIServerSocketFactory}.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public AFRMISocketFactory(final AFNaming naming,
      final RMIClientSocketFactory defaultClientFactory,
      final RMIServerSocketFactory defaultServerFactory) {
    super();
    this.externables = new Externables(naming, defaultClientFactory, defaultServerFactory);

    closeUponRuntimeShutdown();
  }

  // only to be called from the constructor
  private void closeUponRuntimeShutdown() {
    ShutdownHookSupport.addWeakShutdownHook(new ShutdownHook() {

      @Override
      public void onRuntimeShutdown(Thread thread) {
        try {
          close();
        } catch (IOException e) {
          // ignore
        }
      }
    });
  }

  /**
   * Creates a new socket address for the given RMI port.
   *
   * @param port The port.
   * @return The socket address.
   * @throws IOException on error.
   */
  protected abstract AFSocketAddress newSocketAddress(int port) throws IOException;

  /**
   * Creates a new socket that is connected to the given socket address.
   *
   * @param addr The socket address.
   * @return The connected socket.
   * @throws IOException on error.
   */
  protected abstract AFSocket<?> newConnectedSocket(AFSocketAddress addr) throws IOException;

  private synchronized Externables getExternables() {
    return externables;
  }

  private synchronized void setExternable(Externables externable) {
    this.externables = externable;
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    final RMIClientSocketFactory cf = getExternables().defaultClientFactory;
    if (cf != null && port < RMIPorts.AF_PORT_BASE) {
      return cf.createSocket(host, port);
    }

    final AFSocketAddress addr = newSocketAddress(port);
    AFSocket<?> socket = newConnectedSocket(addr);

    synchronized (openSockets) {
      openSockets.add(socket);
    }
    socket.addCloseable(() -> {
      synchronized (openSockets) {
        openSockets.remove(socket);
      }
    });
    return socket;
  }

  @Override
  public void close() throws IOException {
    synchronized (getExternables().naming) {
      rmiService = null;
      closeServerSockets();
      closeSockets();
    }
  }

  private AFRMIService getRmiService() throws IOException {
    AFNaming naming = getExternables().naming;
    synchronized (naming) {
      if (rmiService == null) {
        try {
          rmiService = naming.getRMIService();
        } catch (NotBoundException e) {
          throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
      }
      return rmiService;
    }
  }

  /**
   * Returns a new free port.
   *
   * @return The new port.
   * @throws IOException on error.
   * @see #returnPort(int)
   * @deprecated use {@link #newPortLease()}.
   */
  @Deprecated
  protected int newPort() throws IOException {
    return getRmiService().newPort();
  }

  /**
   * Returns a new free port.
   *
   * @return The new port, wrapped as a {@link PortLease}. Closing the lease will return the port.
   * @throws IOException on error.
   */
  protected PortLease newPortLease() throws IOException {
    AFRMIService service = getRmiService();
    int port = service.newPort();
    return new PortLease(port, service);
  }

  /**
   * Returns a port that was previously returned by {@link #newPort()}.
   *
   * Note that this may call may stall unnecessarily upon shutdown due to locking issues.
   *
   * @param port The port to return.
   * @throws IOException on error.
   * @deprecated use {@link #newPortLease()}
   */
  @Deprecated
  protected void returnPort(int port) throws IOException {
    try {
      getRmiService().returnPort(port);
    } catch (ShutdownException e) {
      // ignore
    } catch (IOException e) {
      StackTraceUtil.printStackTrace(e);
    }
  }

  /**
   * A lease on a registered port; closing the lease will return the port.
   *
   * @author Christian Kohlschütter
   */
  protected static final class PortLease implements Closeable {
    private final int port;
    private final AFRMIService rmiService;

    private PortLease(int port, AFRMIService rmiService) {
      this.port = port;
      this.rmiService = rmiService;
    }

    /**
     * Closes the lease, returning the port to the {@link AFRMIService} it was leased from.
     */
    @Override
    public void close() throws IOException {
      rmiService.returnPort(getPort());
    }

    /**
     * Returns the port number.
     *
     * @return the port number.
     */
    public int getPort() {
      return port;
    }

    /**
     * Returns the service the port was leased from.
     *
     * @return The service.
     */
    public AFRMIService getRmiService() {
      return rmiService;
    }
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    if (port == 0) {
      PortLease portLease = newPortLease();
      port = portLease.getPort();
      final AFSocketAddress addr = newSocketAddress(port);
      AFServerSocket<?> ass = addr.getAddressFamily().newServerSocket();
      ass.addCloseable(portLease);
      ass.setReuseAddress(true);
      ass.setDeleteOnClose(true);
      ass.bind(addr);

      if (port >= RMIPorts.AF_PORT_BASE) {
        ass.addCloseable(new ServerSocketCloseable(ass, port));
      }
      return ass;
    }

    final RMIServerSocketFactory sf = getExternables().defaultServerFactory;
    if (sf != null && port < RMIPorts.AF_PORT_BASE) {
      return sf.createServerSocket(port);
    }

    final AFSocketAddress addr = newSocketAddress(port);
    AFServerSocket<?> socket = addr.getAddressFamily().newServerSocket();
    socket.setDeleteOnClose(true);
    socket.setReuseAddress(true);
    socket.bind(addr);
    socket.addCloseable(new ServerSocketCloseable(socket, port));
    return socket;
  }

  private void closeServerSockets() throws IOException {
    Map<Integer, AFServerSocket<?>> map;
    synchronized (openServerSockets) {
      map = new HashMap<>(openServerSockets);
    }
    IOException ex = null;
    for (Map.Entry<Integer, AFServerSocket<?>> en : map.entrySet()) {
      try {
        en.getValue().close();
      } catch (ShutdownException e) {
        // ignore
      } catch (IOException e) {
        if (ex == null) {
          ex = e;
        } else {
          ex.addSuppressed(e);
        }
      }
    }
    synchronized (openServerSockets) {
      openServerSockets.clear();
    }
    if (ex != null) {
      throw ex;
    }
  }

  private void closeSockets() {
    Set<AFSocket<?>> set;
    synchronized (openSockets) {
      set = new HashSet<>(openSockets);
    }
    for (AFSocket<?> socket : set) {
      try {
        socket.close();
      } catch (IOException e) {
        // ignore
      }
    }
    synchronized (openSockets) {
      openSockets.clear();
    }
  }

  private final class ServerSocketCloseable implements Closeable {
    private final int port;

    ServerSocketCloseable(AFServerSocket<?> socket, int port) {
      this.port = port;
      synchronized (openServerSockets) {
        openServerSockets.put(port, socket);
      }
    }

    @Override
    public void close() throws IOException {
      synchronized (openServerSockets) {
        openServerSockets.remove(port);
      }
    }
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    setExternable(new Externables(readNamingInstance(in), //
        (RMIClientSocketFactory) in.readObject(), //
        (RMIServerSocketFactory) in.readObject()));
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    Externables ext = getExternables();

    writeNamingInstance(out, ext.naming);
    out.writeObject(ext.defaultClientFactory);
    out.writeObject(ext.defaultServerFactory);
  }

  /**
   * Deserializes information necessary to instantiate the {@link AFNaming} instance.
   *
   * @param in The stream.
   * @return The {@link AFNaming} instance.
   * @throws IOException on error.
   */
  protected abstract AFNaming readNamingInstance(ObjectInput in) throws IOException;

  /**
   * Serializes information necessary to instantiate the given {@link AFNaming} instance.
   *
   * @param out The stream.
   * @param namingInstance The {@link AFNaming} instance.
   * @throws IOException on error.
   */
  protected abstract void writeNamingInstance(ObjectOutput out, AFNaming namingInstance)
      throws IOException;

  /**
   * Checks if the given port refers to a local server port.
   *
   * @param port The port to check.
   * @return {@code true} if the given port is a local server.
   */
  public boolean isLocalServer(int port) {
    if (port < RMIPorts.AF_PORT_BASE) {
      return false;
    }
    synchronized (openServerSockets) {
      return openServerSockets.containsKey(port);
    }
  }

  /**
   * The naming instance.
   *
   * @return The instance.
   */
  protected AFNaming getNaming() {
    return getExternables().naming;
  }

  /**
   * Checks if this socket factory has some knowledge about the given port.
   *
   * @param port The port.
   * @return {@code true} if registered.
   */
  abstract boolean hasRegisteredPort(int port);
}
