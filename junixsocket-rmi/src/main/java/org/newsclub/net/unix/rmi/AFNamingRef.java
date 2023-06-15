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

import java.rmi.RemoteException;
import java.util.Objects;

/**
 * A reference to a {@link AFNaming} instance.
 *
 * @author Christian Kohlschütter
 */
final class AFNamingRef {
  private final AFNamingProvider<?> provider;
  private final int port;

  AFNamingRef(AFNamingProvider<?> provider, int port) throws RemoteException {
    this.provider = provider;
    this.port = port;
  }

  @Override
  public int hashCode() {
    return Objects.hash(port, provider);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AFNamingRef)) {
      return false;
    }
    AFNamingRef other = (AFNamingRef) obj;
    return port == other.port && Objects.equals(provider, other.provider);
  }
}