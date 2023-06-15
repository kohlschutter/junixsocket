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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Describes a 32-bit bitmask that supports named flags.
 *
 * @param <T> The subclass's type itself.
 * @author Christian Kohlschütter
 */
@NonNullByDefault
public abstract class NamedIntegerBitmask<T extends NamedIntegerBitmask<T>> implements
    Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Name of the flag/flag set.
   */
  private final String name; // NOPMD.AvoidFieldNameMatchingMethodName

  /**
   * Flag value.
   */
  private final int flags;

  /**
   * Creates a new named flag.
   *
   * @param name The name of the flag / flag set.
   * @param flags The flag value.
   */
  protected NamedIntegerBitmask(@Nullable String name, int flags) {
    this.name = name == null ? "UNDEFINED" : name;
    this.flags = flags;
  }

  /**
   * Returns the name of the flag / flag set.
   *
   * @return The name.
   */
  public final String name() {
    return name;
  }

  /**
   * Returns the value of the flag / flag set.
   *
   * @return The value.
   */
  public final int value() {
    return flags;
  }

  /**
   * Checks if the given flag is set.
   *
   * @param flag The flag to check.
   * @return {@code true} iff set.
   */
  public final boolean hasFlag(T flag) {
    int v = flag.value();
    return (this.flags & v) == v;
  }

  @Override
  public final String toString() {
    return getClass().getName() + "(" + name() + ":" + value() + ")";
  }

  /**
   * Combines two flags / flag sets (use this to implement
   * {@link #combineWith(NamedIntegerBitmask)}).
   *
   * @param allFlags The array of all defined flags, expected "none".
   * @param flagsNone The "none" flag set.
   * @param constr The constructor.
   * @param other The other flag / flag set to merge.
   * @return An instance combining both.
   */
  @SuppressWarnings("PMD.ShortMethodName")
  protected final T combineWith(T[] allFlags, T flagsNone, Constructor<T> constr, T other) {
    return resolve(allFlags, flagsNone, constr, value() | other.value());
  }

  /**
   * Combines two flags / flag sets.
   *
   * @param other The other flag / flag set.
   * @return An instance combining both.
   */
  @SuppressWarnings("PMD.ShortMethodName")
  public abstract T combineWith(T other);

  /**
   * Creates a new instance.
   *
   * @param <T> This type.
   */
  @FunctionalInterface
  protected interface Constructor<T extends NamedIntegerBitmask<T>> {
    /**
     * Creates a new instance.
     *
     * @param name The name.
     * @param flags The flag value.
     * @return The instance.
     */
    T newInstance(@Nullable String name, int flags);
  }

  /**
   * Returns a {@link NamedIntegerBitmask} instance given a flag value.
   *
   * @param <T> The subclass's type itself.
   * @param allFlags The array of all defined flags, expected "none".
   * @param flagsNone The "none" flag set.
   * @param constr The constructor.
   * @param v The flag value.
   * @return An instance representing the flag value.
   */
  @SuppressWarnings({"null", "unchecked"})
  protected static final <T extends NamedIntegerBitmask<T>> T resolve(T[] allFlags, T flagsNone,
      Constructor<T> constr, int v) {
    if (v == 0) {
      return flagsNone;
    }

    List<T> flags = new ArrayList<>();
    for (T flag : allFlags) {
      int val = flag.value();
      if (val == v) {
        return flag;
      }
      if ((v & val) == val) {
        flags.add(flag);
      }
    }

    return resolve(allFlags, flagsNone, constr, flags.toArray((T[]) Array.newInstance(flagsNone
        .getClass(), flags.size())));
  }

  /**
   * Returns a {@link NamedIntegerBitmask} instance given a series of flags.
   *
   * @param <T> The subclass's type itself.
   * @param allFlags The array of all defined flags, expected "none".
   * @param flagsNone The "none" flag set.
   * @param constr The constructor.
   * @param setFlags The flags to set, potentially empty.
   * @return An instance representing the flag values.
   */
  protected static final <T extends NamedIntegerBitmask<T>> T resolve(T[] allFlags, T flagsNone,
      Constructor<T> constr, @NonNull T[] setFlags) {
    int flags = 0;
    int numFlagsSet = 0;
    @Nullable
    T lastFlagSet = null;
    if (setFlags != null) {
      for (T flag : setFlags) {
        flags |= flag.value();
        lastFlagSet = flag;
        numFlagsSet++;
      }
    }
    if (flags == 0) {
      return flagsNone;
    } else if (numFlagsSet == 1 && lastFlagSet != null) {
      return lastFlagSet;
    }

    StringBuilder sb = new StringBuilder();
    for (T flag : setFlags) {
      sb.append(flag.name());
      sb.append(',');
    }
    sb.setLength(sb.length() - 1);

    return constr.newInstance(sb.toString(), flags);
  }
}
