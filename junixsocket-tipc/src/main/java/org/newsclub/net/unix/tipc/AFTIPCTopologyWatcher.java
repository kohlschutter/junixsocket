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
package org.newsclub.net.unix.tipc;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicBoolean;

import org.newsclub.net.unix.AFSocketType;
import org.newsclub.net.unix.AFTIPCSocketAddress;

/**
 * Provides access to the TIPC topology service.
 *
 * @author Christian Kohlschütter
 */
public class AFTIPCTopologyWatcher implements Closeable {
  private final int defaultTimeout;
  private final AFTIPCDatagramChannel channel;
  private final Selector selector;
  private final AtomicBoolean doLoop = new AtomicBoolean(false);
  private final AtomicBoolean running = new AtomicBoolean(false);

  /**
   * Creates an {@link AFTIPCTopologyWatcher} whose subscription requests do not time out by
   * default.
   *
   * @throws IOException on error.
   */
  public AFTIPCTopologyWatcher() throws IOException {
    this(AFTIPCTopologySubscription.TIPC_WAIT_FOREVER);
  }

  /**
   * Creates an {@link AFTIPCTopologyWatcher} whose subscription requests use the given default
   * timeout.
   *
   * @param defaultTimeoutSeconds The timeout in seconds (or
   *          {@link AFTIPCTopologySubscription#TIPC_WAIT_FOREVER};
   * @throws IOException on error.
   */
  public AFTIPCTopologyWatcher(int defaultTimeoutSeconds) throws IOException {
    this.defaultTimeout = defaultTimeoutSeconds;
    this.channel = AFTIPCDatagramSocket.newInstance(AFSocketType.SOCK_SEQPACKET).getChannel();
    this.selector = channel.provider().openSelector();
    channel.connect(AFTIPCSocketAddress.ofTopologyService());
    channel.configureBlocking(false);
  }

  /**
   * Watches for all port changes.
   *
   * @return The subscription object.
   * @throws IOException on error.
   * @see #cancelSubscription(AFTIPCTopologySubscription)
   */
  public final AFTIPCTopologySubscription addPortSubscription() throws IOException {
    return addPortSubscription(0, ~0);
  }

  /**
   * Watches for port changes of the given port ("port" meaning TIPC port, not TCP).
   *
   * @param port The port.
   * @return The subscription object.
   * @throws IOException on error.
   * @see #cancelSubscription(AFTIPCTopologySubscription)
   */
  public final AFTIPCTopologySubscription addPortSubscription(int port) throws IOException {
    return addPortSubscription(port, port);
  }

  /**
   * Watches for port changes within the given range ("port" meaning TIPC port, not TCP).
   *
   * @param lower The lower value of the port range.
   * @param upper The upper value of the port range.
   * @return The subscription object.
   * @throws IOException on error.
   * @see #cancelSubscription(AFTIPCTopologySubscription)
   */
  public final AFTIPCTopologySubscription addPortSubscription(int lower, int upper)
      throws IOException {
    return sendMessage(new AFTIPCTopologySubscription(AFTIPCTopologySubscription.TIPC_NODE_STATE,
        lower, upper, AFTIPCTopologySubscription.Flags.TIPC_SUB_PORTS, defaultTimeout,
        AFTIPCTopologySubscription.USR_EMPTY));
  }

  /**
   * Watches for all link state changes.
   *
   * @return The subscription object.
   * @throws IOException on error.
   * @see #cancelSubscription(AFTIPCTopologySubscription)
   */
  public final AFTIPCTopologySubscription addLinkStateSubscription() throws IOException {
    return sendMessage(new AFTIPCTopologySubscription(AFTIPCTopologySubscription.TIPC_LINK_STATE, 0,
        ~0, AFTIPCTopologySubscription.Flags.NONE, defaultTimeout,
        AFTIPCTopologySubscription.USR_EMPTY));
  }

  /**
   * Watches for service changes of the given service type, matching any instance.
   *
   * @param type The service type.
   * @return The subscription object.
   * @throws IOException on error.
   * @see #cancelSubscription(AFTIPCTopologySubscription)
   */
  public final AFTIPCTopologySubscription addServiceSubscription(int type) throws IOException {
    return addServiceSubscription(type, 0, ~0);
  }

  /**
   * Watches for service changes of the given service type, matching only the specified instance.
   *
   * @param type The service type.
   * @param instance The instance to match.
   * @return The subscription object.
   * @throws IOException on error.
   * @see #cancelSubscription(AFTIPCTopologySubscription)
   */
  public final AFTIPCTopologySubscription addServiceSubscription(int type, int instance)
      throws IOException {
    return addServiceSubscription(type, instance, instance);
  }

  /**
   * Watches for service changes of the given service type and instance range.
   *
   * @param type The service type.
   * @param lower The lower value of the instance range.
   * @param upper The upper value of the instance range.
   * @return The subscription object.
   * @throws IOException on error.
   * @see #cancelSubscription(AFTIPCTopologySubscription)
   */
  public final AFTIPCTopologySubscription addServiceSubscription(int type, int lower, int upper)
      throws IOException {
    return sendMessage(new AFTIPCTopologySubscription(type, lower, upper,
        AFTIPCTopologySubscription.Flags.TIPC_SUB_SERVICE, defaultTimeout,
        AFTIPCTopologySubscription.USR_EMPTY));
  }

  /**
   * Cancels a previously added service subscription.
   *
   * @param sub The subscription to cancel.
   * @throws IOException on error.
   */
  public final void cancelSubscription(AFTIPCTopologySubscription sub) throws IOException {
    sendMessage(sub.toCancellation());
  }

  /**
   * Sends a manually crafted subscription message to the TIPC topology server. You usually don't
   * need to do this directly; use the
   * {@code #addPortSubscription(int, int)}/{@link #cancelSubscription(AFTIPCTopologySubscription)}
   * methods instead.
   *
   * @param sub The subscription message.
   * @return The very message.
   * @throws IOException on error.
   */
  public final AFTIPCTopologySubscription sendMessage(AFTIPCTopologySubscription sub)
      throws IOException {
    channel.write(sub.toBuffer());
    return sub;
  }

  /**
   * Runs a receive loop until {@link #stopLoop()} or {@link #close()} is called.
   *
   * This method returns after the run loop terminates.
   *
   * @throws IOException on error.
   */
  @SuppressWarnings("null")
  public final void runLoop() throws IOException {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("Already running");
    }

    ByteBuffer buf = ByteBuffer.allocate(64);
    SelectionKey key = channel.register(selector, SelectionKey.OP_READ);

    try {
      doLoop.set(true);
      while (!Thread.interrupted() && doLoop.get()) {
        int n = selector.select();
        if (!key.isValid() || !doLoop.get()) {
          break;
        }
        if (n > 0) {
          channel.receive(buf);
          @SuppressWarnings("cast")
          AFTIPCTopologyEvent event = AFTIPCTopologyEvent.readFromBuffer((ByteBuffer) buf.flip());
          onEvent(event);
          buf.clear();
        }
      }
    } finally {
      key.cancel();
      running.set(false);
    }
  }

  /**
   * Called for every event encountered by the run loop.
   *
   * @param event The event.
   * @throws IOException on error. Any exception will terminate the run loop.
   * @see #runLoop()
   */
  protected void onEvent(AFTIPCTopologyEvent event) throws IOException {
  }

  /**
   * Called upon {@link #close()}.
   *
   * @throws IOException on error.
   */
  protected void onClose() throws IOException {
  }

  /**
   * Checks if the watcher run loop is running.
   *
   * @return {@code true} if running.
   * @see #runLoop()
   */
  public boolean isRunning() {
    return running.get();
  }

  /**
   * Stops the run loop.
   */
  public final void stopLoop() {
    doLoop.set(false);
    selector.wakeup();
  }

  /**
   * Closes this instance.
   */
  @Override
  public final void close() throws IOException {
    channel.close();
    stopLoop();
    onClose();
  }
}
