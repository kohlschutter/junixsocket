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

import static org.newsclub.net.unix.AFTIPCSocketAddress.AddressType.formatTIPCInt;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.WeakHashMap;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.newsclub.net.unix.NamedIntegerBitmask;

/**
 * An "event subscription" to be used with {@link AFTIPCTopologyWatcher}.
 *
 * @author Christian Kohlschütter
 */
@NonNullByDefault
public final class AFTIPCTopologySubscription {
  static final byte[] USR_EMPTY = new byte[8];

  private static final WeakHashMap<ByteBuffer, // NOPMD.LooseCoupling
      @Nullable AFTIPCTopologySubscription> SUBSCRIPTIONS = new WeakHashMap<>();

  /**
   * Special timeout value meaning "never timeout".
   */
  public static final int TIPC_WAIT_FOREVER = -1;

  /**
   * The "node state" cluster topology monitor mode.
   *
   * When TIPC establishes contact with another node, it does internally create a binding {type =
   * TIPC_NODE_STATE, instance = peer node hash number} in the binding table. This makes it possible
   * for applications on a node to keep track of reachable peer nodes at any time. *
   */
  public static final int TIPC_NODE_STATE = 0;

  /**
   * The "link state" cluster connectivity monitor mode.
   *
   * When TIPC establishes a new link to another node, it does internally create a binding {type =
   * TIPC_LINK_STATE, instance = peer node hash number} in the binding table. This makes it possible
   * for applications on a node to keep track of working links to peer nodes at any time.
   *
   * This type of binding differs from the topology subscription binding ({@link #TIPC_NODE_STATE})
   * in that there may be two links, and hence two bindings, to keep track of for each peer node.
   * Although this binding type only is published with node visibility, it is possible to combine it
   * with remote node topology subscriptions to obtain a full and continuous matrix view of the
   * connectivity in the cluster.
   */
  public static final int TIPC_LINK_STATE = 2;

  private static final int MESSAGE_LENGTH = 28;

  private final int type;
  private final int lower;
  private final int upper;
  private final Flags flags;
  private final int timeout;
  private final byte[] usrHandle;

  /**
   * Some flags used in the subscription request.
   */
  public static final class Flags extends NamedIntegerBitmask<Flags> {
    private static final long serialVersionUID = 1L;

    /**
     * No flags set.
     */
    public static final Flags NONE = new Flags("NONE", 0);

    /**
     * Event at each match.
     */
    public static final Flags TIPC_SUB_PORTS;

    /**
     * Event at first up/last down.
     */
    public static final Flags TIPC_SUB_SERVICE;

    /**
     * Cancel a subscription.
     */
    public static final Flags TIPC_SUB_CANCEL;

    private static final @NonNull Flags[] VALUES = {
        TIPC_SUB_PORTS = new Flags("TIPC_SUB_PORTS", 1), //
        TIPC_SUB_SERVICE = new Flags("TIPC_SUB_SERVICE", 2), //
        TIPC_SUB_CANCEL = new Flags("TIPC_SUB_CANCEL", 4), //
    };

    private Flags(@Nullable String name, int flags) {
      super(name, flags);
    }

    /**
     * Returns a {@link Flags} instance given an integer value.
     *
     * @param v The value.
     * @return The instance.
     */
    public static Flags ofValue(int v) {
      return resolve(VALUES, NONE, Flags::new, v);
    }

    /**
     * Returns a {@link Flags} instance representing the combination of the given list of
     * {@link Flags} flags.
     *
     * @param flags The flags (zero or more values).
     * @return The instance.
     */
    public static Flags withFlags(@NonNull Flags... flags) {
      return resolve(VALUES, NONE, Flags::new, flags);
    }

    /**
     * Combines the given {@link Flags} instance with another one.
     *
     * @param other The other instance.
     * @return The combined instance.
     */
    @Override
    public Flags combineWith(Flags other) {
      return combineWith(VALUES, NONE, Flags::new, other);
    }
  }

  /**
   * Creates a new subscription message that does not time out.
   *
   * @param type The service type (any service, particularly {@link #TIPC_NODE_STATE} and
   *          {@link #TIPC_LINK_STATE}.
   * @param lower The lower instance.
   * @param upper The upper instance.
   * @param flags Any flaas (use {@link Flags#NONE} if you don't have any).
   */
  public AFTIPCTopologySubscription(int type, int lower, int upper, Flags flags) {
    this(type, lower, upper, flags, TIPC_WAIT_FOREVER, USR_EMPTY);
  }

  /**
   * Creates a new subscription message.
   *
   * @param type The service type (any service, particularly {@link #TIPC_NODE_STATE} and
   *          {@link #TIPC_LINK_STATE}.
   * @param lower The lower instance.
   * @param upper The upper instance.
   * @param flags Any flaas (use {@link Flags#NONE} if you don't have any).
   * @param timeoutSeconds The timeout (in seconds), or {@value #TIPC_WAIT_FOREVER} if this should
   *          never time out.
   */
  public AFTIPCTopologySubscription(int type, int lower, int upper, Flags flags,
      int timeoutSeconds) {
    this(type, lower, upper, flags, timeoutSeconds, USR_EMPTY);
  }

  /**
   * Creates a new subscription message.
   *
   * @param type The service type (any service, particularly {@link #TIPC_NODE_STATE} and
   *          {@link #TIPC_LINK_STATE}.
   * @param lower The lower instance.
   * @param upper The upper instance.
   * @param flags Any flaas (use {@link Flags#NONE} if you don't have any).
   * @param timeoutSeconds The timeout (in seconds), or {@value #TIPC_WAIT_FOREVER} if this should
   *          never time out.
   * @param usrHandle A custom 8-byte message that is included in {@link AFTIPCTopologyEvent}
   *          messages.
   */
  public AFTIPCTopologySubscription(int type, int lower, int upper, Flags flags, int timeoutSeconds,
      byte[] usrHandle) {
    this.type = type;
    this.lower = lower;
    this.upper = upper;
    this.flags = flags == null ? Flags.NONE : flags;
    this.timeout = timeoutSeconds;
    this.usrHandle = new byte[8];
    if (usrHandle != null) {
      if (usrHandle.length > 8) {
        throw new IllegalArgumentException("User handle too long");
      } else {
        System.arraycopy(usrHandle, 0, this.usrHandle, 0, usrHandle.length);
      }
    }
  }

  @SuppressWarnings({"null", "cast"})
  static AFTIPCTopologySubscription readFromBuffer(ByteBuffer buf) {
    buf = (ByteBuffer) buf.slice().limit(MESSAGE_LENGTH);
    AFTIPCTopologySubscription sub = SUBSCRIPTIONS.get(buf);
    if (sub != null) {
      return sub;
    }
    int type = buf.getInt();
    int lower = buf.getInt();
    int upper = buf.getInt();
    int timeout = buf.getInt();
    Flags flags = Flags.ofValue(buf.getInt());
    byte[] usrHandle = new byte[8];
    buf.get(usrHandle, 0, 8);
    buf.flip();
    sub = new AFTIPCTopologySubscription(type, lower, upper, flags, timeout, usrHandle);
    SUBSCRIPTIONS.put(buf, sub);
    return sub;
  }

  @SuppressWarnings("null")
  ByteBuffer writeToBuffer(ByteBuffer buf) {
    buf = buf.order(ByteOrder.BIG_ENDIAN);
    buf.putInt(type);
    buf.putInt(lower);
    buf.putInt(upper);
    buf.putInt(timeout);
    buf.putInt(flags.value());
    buf.put(usrHandle);
    return buf;
  }

  /**
   * Converts this subscription message to a new {@link ByteBuffer}.
   *
   * @return The new buffer, ready to read from.
   */
  @SuppressWarnings({"null", "cast"})
  public ByteBuffer toBuffer() {
    return (ByteBuffer) writeToBuffer(ByteBuffer.allocate(MESSAGE_LENGTH)).flip();
  }

  @Override
  public String toString() {
    return super.toString() + "[" + type + "@" + formatTIPCInt(lower) + "-" + formatTIPCInt(upper)
        + ";flags=" + flags + ";timeout=" + timeout + ";usrHandle=" + String.format(Locale.ENGLISH,
            "%16s", new BigInteger(1, usrHandle).toString(16)).replace(' ', '0') + "]";
  }

  /**
   * Creates an {@link AFTIPCTopologySubscription} that cancels this subscription.
   *
   * Note that a cancellation cannot be cancelled again.
   *
   * @return The new {@link AFTIPCTopologySubscription}.
   */
  public AFTIPCTopologySubscription toCancellation() {
    return new AFTIPCTopologySubscription(type, lower, upper, Flags.TIPC_SUB_CANCEL, timeout,
        usrHandle);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(usrHandle);
    result = prime * result + Objects.hash(flags, lower, timeout, type, upper);
    return result;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AFTIPCTopologySubscription)) {
      return false;
    }
    AFTIPCTopologySubscription other = (AFTIPCTopologySubscription) obj;
    return Objects.equals(flags, other.flags) && lower == other.lower && timeout == other.timeout
        && type == other.type && upper == other.upper && Arrays.equals(usrHandle, other.usrHandle);
  }

  /**
   * Returns the service type.
   *
   * @return The type.
   */
  public int getType() {
    return type;
  }

  /**
   * Returns the lower instance value.
   *
   * @return The lower instance value.
   */
  public int getLower() {
    return lower;
  }

  /**
   * Returns the upper instance value.
   *
   * @return The upper instance value.
   */
  public int getUpper() {
    return upper;
  }

  /**
   * Returns the flags.
   *
   * @return The flags.
   */
  public Flags getFlags() {
    return flags;
  }

  /**
   * Returns the timeout, in seconds (or {@link #TIPC_WAIT_FOREVER} for "never timeout").
   *
   * @return The timeout.
   */
  public int getTimeout() {
    return timeout;
  }

  /**
   * Returns the 8-byte user handle.
   *
   * @return The user handle.
   */
  public byte[] getUsrHandle() {
    return usrHandle.clone();
  }

  /**
   * Returns {@code true} iff the subscription has the
   * {@link AFTIPCTopologySubscription.Flags#TIPC_SUB_PORTS} flag set.
   *
   * @return {@code true} if this is a "port" subscription.
   */
  public boolean isPort() {
    return flags.hasFlag(Flags.TIPC_SUB_PORTS);
  }

  /**
   * Returns {@code true} iff the subscription has the
   * {@link AFTIPCTopologySubscription.Flags#TIPC_SUB_SERVICE} flag set.
   *
   * @return {@code true} if this is a "service" subscription.
   */
  public boolean isService() {
    return flags.hasFlag(Flags.TIPC_SUB_SERVICE);
  }

  /**
   * Returns {@code true} iff the subscription has the
   * {@link AFTIPCTopologySubscription.Flags#TIPC_SUB_CANCEL} flag set.
   *
   * @return {@code true} if this is a "cancellation" subscription request.
   */
  public boolean isCancellation() {
    return flags.hasFlag(Flags.TIPC_SUB_CANCEL);
  }
}