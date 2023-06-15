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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.newsclub.net.unix.AFTIPCSocketAddress.Scope;
import org.newsclub.net.unix.NamedIntegerBitmask;

/**
 * A TIPC group request.
 *
 * @author Christian Kohlschütter
 */
@NonNullByDefault
public final class AFTIPCGroupRequest {
  /**
   * "No group".
   */
  public static final AFTIPCGroupRequest NONE = new AFTIPCGroupRequest(0, 0,
      Scope.SCOPE_NOT_SPECIFIED, GroupRequestFlags.NONE);

  private final int type;
  private final int instance;
  private final Scope scope;
  private final GroupRequestFlags flags;

  /**
   * Some flags used in the group request.
   *
   * @author Christian Kohlschütter
   */
  public static final class GroupRequestFlags extends NamedIntegerBitmask<GroupRequestFlags> {
    private static final long serialVersionUID = 1L;

    /**
     * No flags set.
     */
    public static final GroupRequestFlags NONE = new GroupRequestFlags("NONE", 0);

    /**
     * Receive copies of sent messages.
     */
    public static final GroupRequestFlags GROUP_LOOPBACK;

    /**
     * Receive membership events.
     */
    public static final GroupRequestFlags GROUP_MEMBER_EVTS;

    private static final @NonNull GroupRequestFlags[] VALUES = {
        GROUP_LOOPBACK = new GroupRequestFlags("GROUP_LOOPBACK", 1), //
        GROUP_MEMBER_EVTS = new GroupRequestFlags("GROUP_MEMBER_EVTS", 2), //
    };

    private GroupRequestFlags(@Nullable String name, int flags) {
      super(name, flags);
    }

    /**
     * Returns a {@link GroupRequestFlags} instance given an integer value.
     *
     * @param v The value.
     * @return The instance.
     */
    public static GroupRequestFlags ofValue(int v) {
      return resolve(VALUES, NONE, GroupRequestFlags::new, v);
    }

    /**
     * Returns a {@link GroupRequestFlags} instance representing the combination of the given list
     * of {@link GroupRequestFlags} flags.
     *
     * @param flags The flags (zero or more values).
     * @return The instance.
     */
    public static GroupRequestFlags withFlags(@NonNull GroupRequestFlags... flags) {
      return resolve(VALUES, NONE, GroupRequestFlags::new, flags);
    }

    /**
     * Combines the given {@link GroupRequestFlags} instance with another one.
     *
     * @param other The other instance.
     * @return The combined instance.
     */
    @Override
    public GroupRequestFlags combineWith(GroupRequestFlags other) {
      return combineWith(VALUES, NONE, GroupRequestFlags::new, other);
    }
  }

  private AFTIPCGroupRequest(int type, int instance, Scope scope, GroupRequestFlags flags) {
    this.type = type;
    this.instance = instance;
    this.scope = scope;
    this.flags = flags;
  }

  /**
   * Returns an {@link AFTIPCGroupRequest} instance using the given parameters.
   *
   * @param type The group type.
   * @param instance The group instance.
   * @param scope The group scope.
   * @param flags The request flags.
   * @return The instance.
   */
  public static AFTIPCGroupRequest with(int type, int instance, Scope scope,
      GroupRequestFlags flags) {
    if (type == 0 && instance == 0 && scope.value() == 0 && flags.value() == 0) {
      return NONE;
    } else {
      return new AFTIPCGroupRequest(type, instance, scope, flags);
    }
  }

  /**
   * Returns an {@link AFTIPCGroupRequest} instance using the given parameters, implying cluster
   * scope.
   *
   * @param type The group type.
   * @param instance The group instance.
   * @param flags The request flags.
   * @return The instance.
   */
  public static AFTIPCGroupRequest with(int type, int instance, GroupRequestFlags flags) {
    return with(type, instance, Scope.SCOPE_CLUSTER, flags);
  }

  static AFTIPCGroupRequest fromNative(int type, int instance, int scopeId, int flags) {
    return with(type, instance, Scope.ofValue(scopeId), GroupRequestFlags.ofValue(flags));
  }

  /**
   * Returns the group type.
   *
   * @return The group type.
   */
  public int getType() {
    return type;
  }

  /**
   * Returns the group instance.
   *
   * @return The group instance.
   */
  public int getInstance() {
    return instance;
  }

  /**
   * Returns the group scope.
   *
   * @return The group scope.
   */
  public Scope getScope() {
    return scope;
  }

  int getScopeId() {
    return scope.value();
  }

  int getFlagsValue() {
    return flags.value();
  }

  /**
   * Returns the group request flags.
   *
   * @return The group request flags.
   */
  public GroupRequestFlags getFlags() {
    return flags;
  }

  @Override
  public String toString() {
    return getClass().getName() + ((this == NONE) ? "(no group)" : "(type=" + type + ";instance="
        + instance + ";scope=" + scope + ";flags=" + flags + ")");
  }
}
