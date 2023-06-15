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

import java.net.SocketOption;
import java.net.StandardSocketOptions;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.newsclub.net.unix.AFSocketOption;
import org.newsclub.net.unix.NamedInteger;

/**
 * TIPC-specific socket options.
 *
 * @author Christian Kohlschütter
 */
@NonNullByDefault
public final class AFTIPCSocketOptions {
  /**
   * Use as value for {@link SocketOption}s of type {@link Void}.
   *
   * @see #TIPC_GROUP_LEAVE
   */
  @SuppressWarnings({"null"})
  public static final Void VOID = (@NonNull Void) null;

  /**
   * This option governs how likely a message sent by the socket is to be affected by congestion. A
   * message with higher importance is less likely to be delayed due to link congestion and less
   * likely to be rejected due to receiver congestion.
   *
   * @see MessageImportance
   */
  public static final AFSocketOption<MessageImportance> TIPC_IMPORTANCE = new AFSocketOption<>(
      "TIPC_IMPORTANCE", MessageImportance.class, 271, 127);

  /**
   * Controls whether a message should be discarded when link congestion occurs.
   */
  public static final AFSocketOption<Boolean> TIPC_SRC_DROPPABLE = new AFSocketOption<>(
      "TIPC_SRC_DROPPABLE", Boolean.class, 271, 128);

  /**
   * This option governs the handling of a sent message if it cannot be delivered to its
   * destination. If set, the message is discarded; otherwise it is returned to the sender.
   *
   * By default, this option is enabled for SOCK_RDM and SOCK_DGRAM sockets, and disabled otherwise.
   */
  public static final AFSocketOption<Boolean> TIPC_DEST_DROPPABLE = new AFSocketOption<>(
      "TIPC_DEST_DROPPABLE", Boolean.class, 271, 129);

  /**
   * Specifies the number of milliseconds connect() will wait before giving up because of lack of
   * response. The default value is 8000 ms.
   */
  public static final AFSocketOption<Integer> TIPC_CONN_TIMEOUT = new AFSocketOption<>(
      "TIPC_CONN_TIMEOUT", Integer.class, 271, 130);

  /**
   * Returns the number of messages in the node's receive queue (get only).
   */
  public static final AFSocketOption<Integer> TIPC_NODE_RECVQ_DEPTH = new AFSocketOption<>(
      "TIPC_NODE_RECVQ_DEPTH", Integer.class, 271, 131);

  /**
   * Returns the number of messages in the socket's receive queue (get only).
   */
  public static final AFSocketOption<Integer> TIPC_SOCK_RECVQ_DEPTH = new AFSocketOption<>(
      "TIPC_SOCK_RECVQ_DEPTH", Integer.class, 271, 132);

  /**
   * Force datagram multicasts from this socket to be transmitted as bearer broadcast/multicast
   * (instead of replicated unicast) whenever possible..
   */
  public static final AFSocketOption<Boolean> TIPC_MCAST_BROADCAST = new AFSocketOption<>(
      "TIPC_MCAST_BROADCAST", Boolean.class, 271, 133);

  /**
   * Force datagram multicasts from this socket to be transmitted as replicated unicast instead of
   * bearer broadcast/multicast..
   */
  public static final AFSocketOption<Boolean> TIPC_MCAST_REPLICAST = new AFSocketOption<>(
      "TIPC_MCAST_REPLICAST", Boolean.class, 271, 134);

  /**
   * Join a communication group.
   */
  public static final AFSocketOption<AFTIPCGroupRequest> TIPC_GROUP_JOIN = new AFSocketOption<>(
      "TIPC_GROUP_JOIN", AFTIPCGroupRequest.class, 271, 135);

  /**
   * Leave the previously joined communication group.
   *
   * Only valid for setOption. The value is ignored. Use {@link #VOID}.
   */
  public static final AFSocketOption<Void> TIPC_GROUP_LEAVE = new AFSocketOption<>(
      "TIPC_GROUP_LEAVE", Void.class, 271, 136);

  /**
   * When using TIPC_SOCK_RECVQ_DEPTH for getsockopt(), it returns the number of buffers in the
   * receive socket buffer which is not so helpful for user space applications.
   *
   * TIPC_SOCK_RECVQ_USED returns the current allocated bytes of the receive socket buffer. This
   * helps user space applications dimension its buffer usage to avoid buffer overload issue.
   */
  public static final AFSocketOption<Integer> TIPC_SOCK_RECVQ_USED = new AFSocketOption<>(
      "TIPC_SOCK_RECVQ_USED", Integer.class, 271, 137);

  /**
   * If enabled, the Nagle algorithm is disabled. Similar to
   * {@link StandardSocketOptions#TCP_NODELAY}.
   */
  public static final AFSocketOption<Boolean> TIPC_NODELAY = new AFSocketOption<>("TIPC_NODELAY",
      Boolean.class, 271, 138);

  private AFTIPCSocketOptions() {
    throw new IllegalStateException();
  }

  /**
   * The TIPC message importance.
   *
   * Messages with a higher importance have a lower chance of being dropped when congestion occurs.
   *
   * @author Christian Kohlschütter
   */
  public static final class MessageImportance extends NamedInteger implements
      NamedInteger.HasOfValue {
    private static final long serialVersionUID = 1L;

    /**
     * Low importance (the default).
     */
    public static final MessageImportance LOW;

    /**
     * Medium importance.
     */
    public static final MessageImportance MEDIUM;

    /**
     * High importance.
     */
    public static final MessageImportance HIGH;

    /**
     * Critical importance.
     */
    public static final MessageImportance CRITICAL;

    private static final @NonNull MessageImportance[] VALUES = init(
        new @NonNull MessageImportance[] {
            LOW = new MessageImportance("LOW", 0), //
            MEDIUM = new MessageImportance("MEDIUM", 1), //
            HIGH = new MessageImportance("HIGH", 2), //
            CRITICAL = new MessageImportance("CRITICAL", 3) //
        });

    private MessageImportance(int id) {
      super(id);
    }

    private MessageImportance(String name, int id) {
      super(name, id);
    }

    /**
     * Returns a {@link MessageImportance} instance for the given value.
     *
     * @param v The value.
     * @return The instance.
     */
    public static MessageImportance ofValue(int v) {
      return ofValue(VALUES, MessageImportance::new, v);
    }
  }
}
