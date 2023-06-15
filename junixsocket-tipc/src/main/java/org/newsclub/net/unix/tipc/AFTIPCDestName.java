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

import java.io.Serializable;
import java.net.SocketException;

import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketAddress.Scope;

/**
 * The TIPC-specific DestName response that may be included as ancillary data.
 *
 * @author Christian Kohlschütter
 */
public final class AFTIPCDestName implements Serializable {
  private static final long serialVersionUID = 1L;
  /**
   * Type value.
   */
  private final int type;

  /**
   * Lower service range value.
   */
  private final int lower;

  /**
   * Upper service range value.
   */
  private final int upper;

  /**
   * Creates a new instance.
   *
   * @param type The "type" value.
   * @param lower The "lower" service range value (or the service "instance" if {@code lower} and
   *          {@code upper} are the same).
   * @param upper The "upper" service range value (or the service "instance" if {@code lower} and
   *          {@code upper} are the same).
   */
  public AFTIPCDestName(int type, int lower, int upper) {
    this.type = type;
    this.lower = lower;
    this.upper = upper;
  }

  /**
   * Returns the "type" value.
   *
   * @return The type.
   */
  public int getType() {
    return type;
  }

  /**
   * Returns the "lower" value of the service range (or the service "instance" if identical to the
   * upper value).
   *
   * @return The lower value.
   */
  public int getLower() {
    return lower;
  }

  /**
   * Returns the "upper" value of the service range (or the service "instance" if identical to the
   * lower value).
   *
   * @return The upper value.
   */
  public int getUpper() {
    return upper;
  }

  /**
   * Checks if this DestName describes a service range (as opposed to a service) address.
   *
   * @return {@code true} if the {@link #getLower()} value is different from the {@link #getUpper()}
   *         value.
   */
  public boolean isServiceRange() {
    return lower != upper;
  }

  /**
   * Converts this DestName to a proper {@link AFTIPCSocketAddress}, by using the given
   * {@link Scope} (which is otherwise not included).
   *
   * @param scope The scope to use.
   * @param alwaysRange If {@code true}, a service range address is even returned when a service
   *          address would suffice.
   * @return The address.
   */
  public AFTIPCSocketAddress toSocketAddress(Scope scope, boolean alwaysRange) {
    try {
      if (alwaysRange || isServiceRange()) {
        return AFTIPCSocketAddress.ofServiceRange(scope, type, lower, upper);
      } else {
        return AFTIPCSocketAddress.ofService(scope, type, lower);
      }
    } catch (SocketException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("PMD.ShortMethodName")
  static AFTIPCDestName of(int[] tipcDestName) {
    if (tipcDestName == null) {
      return null;
    }
    if (tipcDestName.length != 3) {
      throw new IllegalArgumentException();
    }
    return new AFTIPCDestName(tipcDestName[0], tipcDestName[1], tipcDestName[2]);
  }
}
