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
package org.newsclub.net.unix.rmi;

import java.io.Closeable;
import java.io.IOException;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;

/**
 * A resource that can be exposed remotely, and closed locally as well as remotely.
 *
 * @author Christian Kohlschütter
 * @param <T> The resource type.
 */
public interface RemoteCloseable<T> extends Remote, Closeable {
  /**
   * Returns the resource (or the Remote instance of it).
   *
   * If the returned resource is {@link Closeable}, then closing via {@code get().close()}} will
   * affect the client-side (local), but not necessarily the server-side as well (the exact behavior
   * depends on the resource).
   *
   * @return The wrapped resource.
   * @throws NoSuchObjectException if this instance has been closed already.
   * @throws IOException if there was a problem.
   */
  T get() throws NoSuchObjectException, IOException;

  /**
   * Closes the resource on the server-side (i.e., where it was created), and — as long as the
   * wrapped resource returned by {@link #get()} supports it — locally as well.
   *
   * @throws IOException if there was a problem.
   */
  @Override
  void close() throws IOException;
}
