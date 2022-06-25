/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
//
// based upon jetty-unixdomain-server
// original copyright message from jetty's UnixDomainServerConnector:
//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.newsclub.net.unix.jetty;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.Channel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Path;
import java.util.EventListener;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A {@link Connector} implementation for junixsocket server socket channels (Unix domains etc.)
 * 
 * Based upon jetty's UnixDomainServerConnector.
 * 
 * This implementation should work with jetty version 9.4.12 or newer.
 */
@ManagedObject
public class AFSocketServerConnector extends AbstractConnector {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractConnector.class);

  private final AtomicReference<Closeable> acceptor = new AtomicReference<>();
  private final SelectorManager selectorManager;
  private ServerSocketChannel serverChannel;
  private AFSocketAddress listenSocketAddress;
  private boolean inheritChannel;
  private int acceptQueueSize;
  private int acceptedReceiveBufferSize;
  private int acceptedSendBufferSize;

  private boolean mayStopServer = false;
  private final Class<? extends EventListener> selectorManagerListenerClass;
  private final Server server;

  /**
   * Creates a new {@link AFSocketServerConnector}.
   * 
   * @param server The server this connector will be added to. Must not be null.
   * @param factories The Connection Factories to use.
   */
  public AFSocketServerConnector(Server server, ConnectionFactory... factories) {
    this(server, null, null, null, -1, -1, factories);
  }

  /**
   * Creates a new {@link AFSocketServerConnector}.
   * 
   * @param server The server this connector will be added to. Must not be null.
   * @param acceptors the number of acceptor threads to use, or -1 for a default value. If 0, then
   *          no acceptor threads will be launched and some other mechanism will need to be used to
   *          accept new connections.
   * @param selectors The number of selectors to use, or -1 for a default derived
   * @param factories The Connection Factories to use.
   */
  public AFSocketServerConnector(Server server, int acceptors, int selectors,
      ConnectionFactory... factories) {
    this(server, null, null, null, acceptors, selectors, factories);
  }

  /**
   * Creates a new {@link AFSocketServerConnector}.
   * 
   * @param server The server this connector will be added to. Must not be null.
   * @param executor An executor for this connector or null to use the servers executor
   * @param scheduler A scheduler for this connector or null to either a {@link Scheduler} set as a
   *          server bean or if none set, then a new {@link ScheduledExecutorScheduler} instance.
   * @param pool A buffer pool for this connector or null to either a {@link ByteBufferPool} set as
   *          a server bean or none set, the new {@link ArrayByteBufferPool} instance.
   * @param acceptors the number of acceptor threads to use, or -1 for a default value. If 0, then
   *          no acceptor threads will be launched and some other mechanism will need to be used to
   *          accept new connections.
   * @param selectors The number of selectors to use, or -1 for a default derived
   * @param factories The Connection Factories to use.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public AFSocketServerConnector(Server server, Executor executor, Scheduler scheduler,
      ByteBufferPool pool, int acceptors, int selectors, ConnectionFactory... factories) {
    super(server, executor, scheduler, pool, acceptors, factories.length > 0 ? factories
        : new ConnectionFactory[] {new HttpConnectionFactory()});
    this.server = server;
    this.selectorManager = newSelectorManager(getExecutor(), getScheduler(), selectors);
    addBean(selectorManager, true);

    this.selectorManagerListenerClass = findSelectorManagerListenerClass();
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends EventListener> findSelectorManagerListenerClass() {
    try {
      return (Class<? extends EventListener>) Class.forName(
          "org.eclipse.jetty.io.SelectorManager$SelectorManagerListener");
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private SelectorManager newSelectorManager(Executor executor, Scheduler scheduler,
      int selectors) {
    return new AFSocketSelectorManager(executor, scheduler, selectors);
  }

  /**
   * Returns the Unix-Domain path this connector listens to.
   * 
   * Added for compatibility with jetty's {@code UnixDomainServerConnector}.
   * 
   * @return The Unix-Domain path this connector listens to.
   * @deprecated Use {@link #getListenSocketAddress()} instead.
   * @see #getListenSocketAddress()
   */
  @ManagedAttribute("The Unix-Domain path this connector listens to")
  public Path getUnixDomainPath() {
    if (listenSocketAddress instanceof AFUNIXSocketAddress) {
      AFUNIXSocketAddress addr = (AFUNIXSocketAddress) listenSocketAddress;
      if (addr.hasFilename()) {
        try {
          return addr.getFile().toPath();
        } catch (FileNotFoundException e) {
          return null;
        }
      }
    }
    return null;
  }

  /**
   * Sets the Unix-Domain path this connector listens to.
   * 
   * Added for compatibility with jetty's {@code UnixDomainServerConnector}.
   * 
   * @param unixDomainPath The path.
   * @deprecated Use {@link #setListenSocketAddress(AFSocketAddress)} instead.
   * @see #setListenSocketAddress(AFSocketAddress)
   */
  public void setUnixDomainPath(Path unixDomainPath) {
    try {
      this.listenSocketAddress = AFUNIXSocketAddress.of(unixDomainPath);
    } catch (SocketException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns the socket address this connector listens to.
   * 
   * @return The socket address, or {@code null} if none set.
   */
  @ManagedAttribute("The socket address this connector listens to")
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public AFSocketAddress getListenSocketAddress() {
    return listenSocketAddress;
  }

  /**
   * Sets the socket address this connector listens to.
   * 
   * @param addr The socket address, or {@code null}.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public void setListenSocketAddress(AFSocketAddress addr) {
    this.listenSocketAddress = addr;
  }

  /**
   * Checks whether this connector uses a server channel inherited from the JVM.
   * 
   * @return {@code true} if so.
   */
  @ManagedAttribute("Whether this connector uses a server channel inherited from the JVM")
  public boolean isInheritChannel() {
    return inheritChannel;
  }

  /**
   * Sets whether this connector uses a server channel inherited from the JVM.
   * 
   * @param inheritChannel {@code true} if so.
   */
  public void setInheritChannel(boolean inheritChannel) {
    this.inheritChannel = inheritChannel;
  }

  /**
   * Returns the accept queue size (backlog) for the server socket.
   * 
   * @return The backlog.
   */
  @ManagedAttribute("The accept queue size (backlog) for the server socket")
  public int getAcceptQueueSize() {
    return acceptQueueSize;
  }

  /**
   * Sets the accept queue size (backlog) for the server socket.
   * 
   * @param acceptQueueSize The backlog.
   */
  public void setAcceptQueueSize(int acceptQueueSize) {
    this.acceptQueueSize = acceptQueueSize;
  }

  /**
   * Returns the SO_RCVBUF size for accepted sockets.
   * 
   * @return The buffer size.
   */
  @ManagedAttribute("The SO_RCVBUF option for accepted sockets")
  public int getAcceptedReceiveBufferSize() {
    return acceptedReceiveBufferSize;
  }

  /**
   * Sets the SO_RCVBUF size for accepted sockets.
   * 
   * @param acceptedReceiveBufferSize The buffer size.
   */
  public void setAcceptedReceiveBufferSize(int acceptedReceiveBufferSize) {
    this.acceptedReceiveBufferSize = acceptedReceiveBufferSize;
  }

  /**
   * Returns the SO_SNDBUF size for accepted sockets.
   * 
   * @return The buffer size.
   */
  @ManagedAttribute("The SO_SNDBUF option for accepted sockets")
  public int getAcceptedSendBufferSize() {
    return acceptedSendBufferSize;
  }

  /**
   * Sets the SO_SNDBUF size for accepted sockets.
   * 
   * @param acceptedSendBufferSize The buffer size.
   */
  public void setAcceptedSendBufferSize(int acceptedSendBufferSize) {
    this.acceptedSendBufferSize = acceptedSendBufferSize;
  }

  @Override
  protected void doStart() throws Exception {
    if (selectorManagerListenerClass != null) {
      getBeans(selectorManagerListenerClass).forEach(selectorManager::addEventListener);
    }
    serverChannel = open();
    addBean(serverChannel);
    super.doStart();
  }

  @Override
  protected void doStop() throws Exception {
    super.doStop();
    removeBean(serverChannel);
    close();
    if (selectorManagerListenerClass != null) {
      getBeans(selectorManagerListenerClass).forEach(selectorManager::removeEventListener);
    }
  }

  @Override
  @SuppressWarnings("PMD.CognitiveComplexity")
  protected void accept(int acceptorID) throws IOException {
    ServerSocketChannel sc = this.serverChannel;
    if (sc != null) {
      try {
        SocketChannel channel = sc.accept();
        accepted(channel);
      } catch (SocketException e) {
        boolean takenOver = !sc.isOpen() || sc.getLocalAddress() == null;
        if (!takenOver && sc instanceof AFServerSocketChannel<?>) {
          takenOver = !((AFServerSocketChannel<?>) sc).isLocalSocketAddressValid();
        }

        if (takenOver) {
          ExecutorService es = Executors.newSingleThreadExecutor();
          try {
            LOG.warn("Another server has taken over our address");
            es.execute(() -> {
              Connector[] connectors = server.getConnectors();

              boolean shutdownServer;
              if (connectors == null) {
                shutdownServer = true;
              } else {
                shutdownServer = true;
                for (Connector conn : connectors) {
                  if (conn != AFSocketServerConnector.this && conn.isRunning()) { // NOPMD.CompareObjectsWithEquals
                    shutdownServer = false;
                    break;
                  }
                }
              }

              if (shutdownServer && mayStopServer) {
                LOG.warn("Server has no other connectors; shutting down: " + server); // NOPMD

                try {
                  server.stop();
                } catch (Exception e1) {
                  LOG.warn("Exception upon stopping " + server, e1); // NOPMD
                }
              }
            });
          } finally {
            es.shutdown();
          }
        }
        throw (ClosedByInterruptException) new ClosedByInterruptException().initCause(e);
      }
    }
  }

  private void accepted(SocketChannel channel) throws IOException {
    channel.configureBlocking(false);
    configure(channel);
    selectorManager.accept(channel);
  }

  /**
   * Configures an incoming {@link SocketChannel}, setting socket options such as receive and send
   * buffer sizes.
   * 
   * @param channel The socket channel to configure.
   * @throws IOException on error.
   */
  protected void configure(SocketChannel channel) throws IOException {
    channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    int rcvBufSize = getAcceptedReceiveBufferSize();
    if (rcvBufSize > 0) {
      channel.setOption(StandardSocketOptions.SO_RCVBUF, rcvBufSize);
    }
    int sndBufSize = getAcceptedSendBufferSize();
    if (sndBufSize > 0) {
      channel.setOption(StandardSocketOptions.SO_SNDBUF, sndBufSize);
    }
  }

  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public Object getTransport() {
    return serverChannel;
  }

  private ServerSocketChannel open() throws IOException {
    ServerSocketChannel sc = openServerSocketChannel();
    if (getAcceptors() == 0) {
      sc.configureBlocking(false);
      acceptor.set(selectorManager.acceptor(sc));
    }
    return sc;
  }

  private void close() throws IOException {
    ServerSocketChannel sc = this.serverChannel;
    this.serverChannel = null;
    IO.close(sc);
  }

  private ServerSocketChannel openServerSocketChannel() throws IOException {
    ServerSocketChannel sc = null;
    if (isInheritChannel()) {
      Channel channel = System.inheritedChannel();
      if (channel instanceof ServerSocketChannel) {
        sc = (ServerSocketChannel) channel;
      } else {
        LOG.warn( // NOPMD.GuardLogStatement
            "Unable to use System.inheritedChannel() {}. Trying a new ServerSocketChannel at {}",
            channel, getListenSocketAddress());
      }
    }
    if (sc == null) {
      sc = bindServerSocketChannel();
    }
    return sc;
  }

  private ServerSocketChannel bindServerSocketChannel() throws IOException {
    AFSocketAddress socketAddress = listenSocketAddress;
    AFServerSocketChannel<?> sc = socketAddress.getAddressFamily().newServerSocketChannel();

    try {
      sc.bind(socketAddress, getAcceptQueueSize());
      return sc;
    } catch (IOException x) {
      String message = String.format(Locale.ENGLISH, "Could not bind %s to %s",
          AFSocketServerConnector.class.getSimpleName(), listenSocketAddress);
      throw new IOException(message, x);
    }
  }

  @Override
  public void setAccepting(boolean accepting) {
    super.setAccepting(accepting);
    if (getAcceptors() == 0) {
      return;
    }
    if (accepting) {
      if (acceptor.get() == null) {
        Closeable cl = selectorManager.acceptor(serverChannel);
        if (!this.acceptor.compareAndSet(null, cl)) {
          IO.close(cl);
        }
      }
    } else {
      Closeable cl = this.acceptor.get();
      if (cl != null && this.acceptor.compareAndSet(cl, null)) {
        IO.close(cl);
      }
    }
  }

  @Override
  public String toString() {
    return String.format(Locale.ENGLISH, "%s@%h[%s]", getClass().getSimpleName(), hashCode(),
        listenSocketAddress);
  }

  private final class AFSocketSelectorManager extends SelectorManager {
    public AFSocketSelectorManager(Executor executor, Scheduler scheduler, int selectors) {
      super(executor, scheduler, selectors);
    }

    @Override
    protected Selector newSelector() throws IOException {
      SelectorProvider provider = listenSocketAddress.getAddressFamily().getSelectorProvider();
      return provider.openSelector();
    }

    @Override
    protected void accepted(SelectableChannel channel) throws IOException {
      AFSocketServerConnector.this.accepted((SocketChannel) channel);
    }

    @Override
    protected EndPoint newEndPoint(SelectableChannel channel, ManagedSelector selector,
        SelectionKey selectionKey) {
      SocketChannelEndPoint endPoint = new SocketChannelEndPoint((SocketChannel) channel, selector,
          selectionKey, getScheduler());
      endPoint.setIdleTimeout(getIdleTimeout());
      return endPoint;
    }

    @Override
    public Connection newConnection(SelectableChannel channel, EndPoint endpoint,
        Object attachment) {
      return getDefaultConnectionFactory().newConnection(AFSocketServerConnector.this, endpoint);
    }

    @Override
    protected void endPointOpened(EndPoint endpoint) {
      super.endPointOpened(endpoint);
      onEndPointOpened(endpoint);
    }

    @Override
    protected void endPointClosed(EndPoint endpoint) {
      onEndPointClosed(endpoint);
      super.endPointClosed(endpoint);
    }
  }

  /**
   * Checks if this connector may stop the server when it's no longer able to serve and no other
   * connectors are available.
   * 
   * @return {@code true} if so.
   */
  @ManagedAttribute("Whether this connector may stop the server when it's no longer able to"
      + " serve and no other connectors are available")
  public boolean isMayStopServer() {
    return mayStopServer;
  }

  /**
   * Sets if this connector may stop the server when it's no longer able to serve and no other
   * connectors are available.
   * 
   * @param mayStopServer {@code true} if so.
   */
  public void setMayStopServer(boolean mayStopServer) {
    this.mayStopServer = mayStopServer;
  }
}
