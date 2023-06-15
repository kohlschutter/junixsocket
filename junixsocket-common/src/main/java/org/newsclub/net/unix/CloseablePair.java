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

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;

/**
 * A pair of two closeable items.
 *
 * @param <T> The type of the items.
 * @author Christian Kohlschütter
 */
public class CloseablePair<T extends Closeable> implements Closeable {
  private final @NonNull T first;
  private final @NonNull T second;
  private final Closeable alsoClose;

  /**
   * Creates a pair of two items.
   *
   * @param a The first item.
   * @param b The second item.
   */
  public CloseablePair(T a, T b) {
    this(a, b, null);
  }

  /**
   * Creates a pair of two items.
   *
   * @param a The first item.
   * @param b The second item.
   * @param alsoClose Some closeable that is also closed upon {@link #close()}, or {@code null}.
   */
  public CloseablePair(T a, T b, Closeable alsoClose) {
    Objects.requireNonNull(a);
    Objects.requireNonNull(b);
    this.first = a;
    this.second = b;
    this.alsoClose = alsoClose;
  }

  @Override
  public final void close() throws IOException {
    first.close();
    second.close();
    if (alsoClose != null) {
      alsoClose.close();
    }
  }

  /**
   * Returns the pair's first item.
   *
   * @return The first item.
   */
  public final @NonNull T getFirst() {
    return first;
  }

  /**
   * Returns the pair's second item.
   *
   * @return The second item.
   */
  public final @NonNull T getSecond() {
    return second;
  }
}
