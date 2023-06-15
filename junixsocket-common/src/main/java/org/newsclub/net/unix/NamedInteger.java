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

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A "named integer", usually used for constants.
 *
 * See the concrete implementations for usage.
 *
 * @author Christian Kohlschütter
 */
@NonNullByDefault
public class NamedInteger implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * The name.
   */
  private final String name; // NOPMD

  /**
   * The ID value.
   */
  private final int id;

  /**
   * Marks a subclass that provides a method {@code "public static T ofValue(int v)"} that allows
   * casting an integer to this type via reflection.
   */
  public interface HasOfValue {
    /* static <T extends NamedInteger> ofValue(int v); */
  }

  /**
   * Creates a new {@link NamedInteger} instance, without actually naming it. A name of "UNDEFINED"
   * is used.
   *
   * @param id The value.
   */
  protected NamedInteger(int id) {
    this("UNDEFINED", id);
  }

  /**
   * Creates a new {@link NamedInteger} instance.
   *
   * @param name The name.
   * @param id The value.
   */
  protected NamedInteger(String name, int id) {
    this.name = name;
    this.id = id;
  }

  /**
   * Returns the name.
   *
   * @return The name.
   */
  public final String name() {
    return name;
  }

  /**
   * Returns the value.
   *
   * @return The value.
   */
  public final int value() {
    return id;
  }

  @Override
  public final String toString() {
    return name() + "(" + id + ")";
  }

  @Override
  public final int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public final boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    } else if (obj == null) {
      return false;
    } else if (getClass() != obj.getClass()) {
      return false;
    }
    NamedInteger other = (NamedInteger) obj;
    return id == other.value();
  }

  /**
   * Ensures that the {@code VALUES} array is configured correctly.
   *
   * @param <T> The instance type.
   * @param values The {@code VALUES} array.
   * @return The verified {@code VALUES} array.
   */
  protected static final <T extends NamedInteger> T[] init(T[] values) {
    Set<Integer> seenValues = new HashSet<>();
    for (T val : values) {
      if (!seenValues.add(val.value())) {
        throw new IllegalStateException("Duplicate value: " + val.value());
      }
    }
    return values;
  }

  /**
   * Constructor for "undefined" values.
   *
   * @param <T> The instance type.
   */
  @FunctionalInterface
  protected interface UndefinedValueConstructor<T extends NamedInteger> {
    /**
     * Creates a new "undefined" value instance.
     *
     * @param id The value.
     * @return The instance.
     */
    T newInstance(int id);
  }

  /**
   * Returns an instance given an integer value.
   *
   * @param <T> The instance type.
   * @param values The {@code VALUES} array.
   * @param constr The constructor for undefined values.
   * @param v The value.
   * @return The instance.
   */
  protected static final <T extends NamedInteger> T ofValue(T[] values,
      UndefinedValueConstructor<T> constr, int v) {
    for (T e : values) {
      if (e.value() == v) {
        return e;
      }
    }
    return constr.newInstance(v);
  }
}
