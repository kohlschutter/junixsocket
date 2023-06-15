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

import org.eclipse.jdt.annotation.NonNull;

/**
 * A pair of sockets.
 *
 * @param <T> The socket type.
 * @author Christian Kohlschütter
 */
public abstract class AFSocketPair<T extends AFSomeSocket> extends CloseablePair<T> {
  /**
   * Creates a socket pair.
   *
   * @param a The first socket.
   * @param b The second socket.
   */
  protected AFSocketPair(T a, T b) {
    super(a, b);
  }

  /**
   * Creates a socket pair.
   *
   * @param a The first socket.
   * @param b The second socket.
   * @param alsoClose Some closeable that is also closed upon {@link #close()}, or {@code null}.
   */
  protected AFSocketPair(T a, T b, Closeable alsoClose) {
    super(a, b, alsoClose);
  }

  /**
   * Returns the first socket of the pair.
   *
   * @return The first socket.
   */
  public final @NonNull T getSocket1() {
    return getFirst();
  }

  /**
   * Returns the second socket of the pair.
   *
   * @return The second socket.
   */
  public final @NonNull T getSocket2() {
    return getSecond();
  }
}
