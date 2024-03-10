/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
package org.newsclub.net.unix.jetty;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import org.eclipse.jetty.io.DatagramChannelEndPoint;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.io.Transport;
import org.eclipse.jetty.util.thread.Scheduler;
import org.newsclub.net.unix.AFSocketAddress;

/**
 * A {@link Transport} implementation for junixsocket socket and datagram channels (Unix domains
 * etc.)
 * <p>
 * This implementation should work with jetty version 12.0.7 or newer.
 *
 * @author Christian Kohlschütter
 */
public class AFSocketTransport extends Transport.Socket {
  private final AFSocketAddress socketAddress;

  private AFSocketTransport(AFSocketAddress socketAddress) {
    super();
    this.socketAddress = socketAddress;
  }

  /**
   * Constructs an {@link AFSocketTransport} that establishes a {@link SocketChannel} to the given
   * address.
   *
   * @param addr The target address.
   * @return The {@link Transport} instance.
   */
  public static AFSocketTransport withSocketChannel(AFSocketAddress addr) {
    return new WithSocketChannel(addr);
  }

  /**
   * Constructs an {@link AFSocketTransport} that establishes a {@link DatagramChannel} to the given
   * address.
   *
   * @param addr The target address.
   * @return The {@link Transport} instance.
   */
  public static AFSocketTransport withDatagramChannel(AFSocketAddress addr) {
    return new WithDatagramChannel(addr);
  }

  @Override
  public AFSocketAddress getSocketAddress() {
    return socketAddress;
  }

  @Override
  public int hashCode() {
    return Objects.hash(socketAddress);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof AFSocketTransport) {
      return Objects.equals(socketAddress, ((AFSocketTransport) obj).socketAddress);
    }
    return false;
  }

  @Override
  public String toString() {
    return super.toString() + "[" + socketAddress.toString() + "]";
  }

  static class WithSocketChannel extends AFSocketTransport {
    public WithSocketChannel(AFSocketAddress address) {
      super(address);
    }

    @Override
    public SelectableChannel newSelectableChannel() throws IOException {
      return getSocketAddress().getAddressFamily().getSelectorProvider().openSocketChannel();
    }

    @Override
    public EndPoint newEndPoint(Scheduler scheduler, ManagedSelector selector,
        SelectableChannel selectable, SelectionKey selectionKey) {
      return new SocketChannelEndPoint((SocketChannel) selectable, selector, selectionKey,
          scheduler);
    }
  }

  static class WithDatagramChannel extends AFSocketTransport {
    public WithDatagramChannel(AFSocketAddress address) {
      super(address);
    }

    @Override
    public SelectableChannel newSelectableChannel() throws IOException {
      return getSocketAddress().getAddressFamily().getSelectorProvider().openDatagramChannel();
    }

    @Override
    public EndPoint newEndPoint(Scheduler scheduler, ManagedSelector selector,
        SelectableChannel selectable, SelectionKey selectionKey) {
      return new DatagramChannelEndPoint((DatagramChannel) selectable, selector, selectionKey,
          scheduler);
    }
  }
}
