/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
package org.newsclub.net.unix.ssl;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;

/**
 * A wrapping {@link SNIMatcher} that calls a callback with the checked {@link SNIServerName} and
 * match status ({@code true} or {@code false}).
 *
 * @author Christian Kohlschütter
 */
public final class CallbackSNIMatcher extends SNIMatcher {
  private final SNIMatcher wrapped;
  private final Callback callback;

  /**
   * Callback that is called upon {@link SNIMatcher#matches(SNIServerName)}.
   *
   * @author Christian Kohlschütter
   */
  @FunctionalInterface
  public interface Callback {
    /**
     * Called upon {@link SNIMatcher#matches(SNIServerName)}, along with the result.
     *
     * @param name The name.
     * @param matches The match result.
     */
    void onMatch(SNIServerName name, boolean matches);
  }

  /**
   * Constructs a new {@link CallbackSNIMatcher}, using the given wrapped {@link SNIMatcher} and
   * callback.
   *
   * @param wrapped The wrapped {@link SNIMatcher}.
   * @param callback The callback to be called upon {@link #matches(SNIServerName)}.
   */
  public CallbackSNIMatcher(SNIMatcher wrapped, Callback callback) {
    super(wrapped.getType());
    this.wrapped = wrapped;
    this.callback = callback;
  }

  @Override
  public boolean matches(SNIServerName serverName) {
    boolean matches = wrapped.matches(serverName);
    callback.onMatch(serverName, matches);
    return matches;
  }
}
