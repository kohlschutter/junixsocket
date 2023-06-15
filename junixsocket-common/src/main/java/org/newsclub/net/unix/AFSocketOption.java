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
package org.newsclub.net.unix;

import java.net.SocketOption;

/**
 * A special socket option supported by some junixsocket-based implementation.
 *
 * @param <T> The option's value type.
 * @author Christian Kohlschütter
 */
public final class AFSocketOption<T> implements SocketOption<T> {
  private final String name; // NOPMD.AvoidFieldNameMatchingMethodName
  private final Class<T> type; // NOPMD.AvoidFieldNameMatchingMethodName
  private final int level; // NOPMD.AvoidFieldNameMatchingMethodName
  private final int optionName; // NOPMD.AvoidFieldNameMatchingMethodName

  /**
   * Creates a new socket option. This should only be called by {@link AFSocket} implementations.
   *
   * @param name The name of the option.
   * @param type The value type.
   * @param level The socket level (as defined in junixsocket-native).
   * @param optionName The option name (as defined in junixsocket-native).
   */
  public AFSocketOption(String name, Class<T> type, int level, int optionName) {
    this.name = name;
    this.type = type;
    this.level = level;
    this.optionName = optionName;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Class<T> type() {
    return type;
  }

  int level() {
    return level;
  }

  int optionName() {
    return optionName;
  }

  @Override
  public String toString() {
    return getClass() + ":" + name;
  }
}
