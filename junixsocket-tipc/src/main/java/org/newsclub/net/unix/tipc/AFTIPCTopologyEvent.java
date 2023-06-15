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

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.NamedInteger;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A TIPC topology event received by the {@link AFTIPCTopologyWatcher} as a result of an
 * {@link AFTIPCTopologySubscription}.
 *
 * @author Christian Kohlschütter
 */
@NonNullByDefault
public final class AFTIPCTopologyEvent {
  private static final int MESSAGE_LENGTH = 48;
  private final Type type;
  private final int foundLower;
  private final int foundUpper;
  private final AFTIPCSocketAddress address;
  private final AFTIPCTopologySubscription subscription;

  /**
   * Some TIPC error code.
   *
   * @author Christian Kohlschütter
   */
  @SuppressWarnings("PMD.ShortClassName")
  public static final class Type extends NamedInteger {
    private static final long serialVersionUID = 1L;

    /**
     * Publication event.
     */
    public static final Type TIPC_PUBLISHED;

    /**
     * Withdrawal event.
     */
    public static final Type TIPC_WITHDRAWN;

    /**
     * Subscription timeout event.
     */
    public static final Type TIPC_SUBSCR_TIMEOUT;

    /**
     * Undefined.
     */
    public static final Type UNDEFINED;

    private static final @NonNull Type[] VALUES = init(new @NonNull Type[] {
        UNDEFINED = new Type("UNDEFINED", 0), //
        TIPC_PUBLISHED = new Type("TIPC_PUBLISHED", 1), //
        TIPC_WITHDRAWN = new Type("TIPC_WITHDRAWN", 2), //
        TIPC_SUBSCR_TIMEOUT = new Type("TIPC_SUBSCR_TIMEOUT", 3), //
    });

    private Type(int id) {
      super(id);
    }

    private Type(String name, int id) {
      super(name, id);
    }

    /**
     * Returns an {@link Type} instance given an integer.
     *
     * @param v The value.
     * @return The instance.
     */
    public static Type ofValue(int v) {
      return ofValue(VALUES, Type::new, v);
    }
  }

  private AFTIPCTopologyEvent(Type type, int foundLower, int foundUpper,
      AFTIPCSocketAddress address, AFTIPCTopologySubscription subscription) {
    this.type = type;
    this.foundLower = foundLower;
    this.foundUpper = foundUpper;
    this.address = address;
    this.subscription = subscription;
  }

  @SuppressWarnings("null")
  static AFTIPCTopologyEvent readFromBuffer(ByteBuffer buf) throws SocketException {
    buf = buf.order(ByteOrder.BIG_ENDIAN);
    Type type = Type.ofValue(buf.getInt());
    int foundLower = buf.getInt();
    int foundUpper = buf.getInt();
    AFTIPCSocketAddress address = AFTIPCSocketAddress.ofSocket(buf.getInt(), buf.getInt());
    AFTIPCTopologySubscription sub = AFTIPCTopologySubscription.readFromBuffer(buf);

    return new AFTIPCTopologyEvent(type, foundLower, foundUpper, address, sub);
  }

  @Override
  public String toString() {
    int lower = getFoundLower();
    int upper = getFoundUpper();
    String found;
    if (lower == upper) {
      found = formatTIPCInt(lower);
    } else {
      found = formatTIPCInt(lower) + "-" + formatTIPCInt(upper);
    }
    return super.toString() + "[" + getType() + ";found:" + found + ";addr=" + getAddress()
        + ";sub=" + getSubscription() + "]";
  }

  @SuppressWarnings("null")
  ByteBuffer writeToBuffer(ByteBuffer buf) throws IOException {
    buf = buf.order(ByteOrder.BIG_ENDIAN);
    buf.putInt(getType().value());
    buf.putInt(getFoundLower());
    buf.putInt(getFoundUpper());
    getAddress().writeNativeAddressTo(buf);
    getSubscription().writeToBuffer(buf);
    return buf;
  }

  /**
   * Converts this event message to a new {@link ByteBuffer}.
   *
   * @return The new buffer, ready to read from.
   * @throws IOException on error.
   */
  @SuppressWarnings({"null", "cast"})
  public ByteBuffer toBuffer() throws IOException {
    return (ByteBuffer) writeToBuffer(ByteBuffer.allocate(MESSAGE_LENGTH)).flip();
  }

  /**
   * The event type.
   *
   * @return The type.
   */
  public Type getType() {
    return type;
  }

  /**
   * The found range's lower value.
   *
   * @return The lower value.
   */
  public int getFoundLower() {
    return foundLower;
  }

  /**
   * The found range's upper value.
   *
   * @return The upper value.
   */
  public int getFoundUpper() {
    return foundUpper;
  }

  /**
   * The corresponding socket address.
   *
   * @return The socket address.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public AFTIPCSocketAddress getAddress() {
    return address;
  }

  /**
   * The corresponding subscription that found this event.
   *
   * @return The subscription.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public AFTIPCTopologySubscription getSubscription() {
    return subscription;
  }

  /**
   * Returns {@code true} iff the event type is {@link Type#TIPC_PUBLISHED}.
   *
   * @return {@code true} if this a "published" event.
   */
  public boolean isPublished() {
    return type == Type.TIPC_PUBLISHED; // NOPMD.CompareObjectsWithEquals
  }

  /**
   * Returns {@code true} iff the event type is {@link Type#TIPC_WITHDRAWN}.
   *
   * @return {@code true} if this a "withdrawn" event.
   */
  public boolean isWithdrawn() {
    return type == Type.TIPC_WITHDRAWN; // NOPMD.CompareObjectsWithEquals
  }

  /**
   * Returns {@code true} iff the event type is {@link Type#TIPC_SUBSCR_TIMEOUT}.
   *
   * @return {@code true} if this a "timeout" event.
   */
  public boolean isTimeout() {
    return type == Type.TIPC_SUBSCR_TIMEOUT; // NOPMD.CompareObjectsWithEquals
  }

  /**
   * Returns {@code true} iff the corresponding subscription has the
   * {@link AFTIPCTopologySubscription.Flags#TIPC_SUB_PORTS} flag set.
   *
   * @return {@code true} if this a event referring to a "port" subscription.
   */
  public boolean isPort() {
    return subscription.isPort();
  }

  /**
   * Returns {@code true} iff the corresponding subscription has the
   * {@link AFTIPCTopologySubscription.Flags#TIPC_SUB_SERVICE} flag set.
   *
   * @return {@code true} if this a event referring to a "service" subscription.
   */
  public boolean isService() {
    return subscription.isService();
  }

  /**
   * Returns {@code true} iff the corresponding subscription has the
   * {@link AFTIPCTopologySubscription.Flags#TIPC_SUB_CANCEL} flag set.
   *
   * @return {@code true} if this a event referring to a "cancellation" subscription request.
   */
  public boolean isCancellationRequest() {
    return subscription.isCancellation();
  }

  @Override
  public int hashCode() {
    return Objects.hash(address, foundLower, foundUpper, subscription, type);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AFTIPCTopologyEvent)) {
      return false;
    }
    AFTIPCTopologyEvent other = (AFTIPCTopologyEvent) obj;
    return Objects.equals(address, other.address) && foundLower == other.foundLower
        && foundUpper == other.foundUpper && Objects.equals(subscription, other.subscription)
        && Objects.equals(type, other.type);
  }

  /**
   * Returns the link name for a link state event requested by
   * {@link AFTIPCTopologySubscription#TIPC_LINK_STATE} or
   * {@link AFTIPCTopologyWatcher#addLinkStateSubscription()}.
   *
   * A link name is something like "f875a40e707d:eth0-8c1645f2ce27:eth0"
   *
   * @return The link name, or {@code  null} if unsupported.
   * @throws IOException on error.
   */
  public @Nullable String getLinkName() throws IOException {
    // this only works if getSubscription().getType() == AFTIPCSubscription.TIPC_LINK_STATE
    int node = getFoundLower();
    int ref = getAddress().getTIPCRef();
    return AFTIPCSocket.getLinkName(node, ref & 0xFFFF);
  }
}
