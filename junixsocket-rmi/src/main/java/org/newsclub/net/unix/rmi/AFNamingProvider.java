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

import java.io.IOException;

/**
 * The key to accessing to {@link AFNaming} instances.
 *
 * Implementors must guarantee that {@link #hashCode()} and {@link #equals(Object)} correctly
 * identify duplicate providers.
 *
 * @param <T> The actual {@link AFNaming} subclass.
 * @author Christian Kohlschütter
 */
public interface AFNamingProvider<T extends AFNaming> {
  /**
   * Creates a new {@link AFNaming} instance using the given registry port.
   *
   * @param registryPort The registry port.
   * @return The {@link AFNaming} instance.
   * @throws IOException on error.
   */
  T newInstance(int registryPort) throws IOException;

  @Override
  int hashCode();

  @Override
  boolean equals(Object other);
}
