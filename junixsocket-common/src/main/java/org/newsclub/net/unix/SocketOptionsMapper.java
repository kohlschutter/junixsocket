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
import java.net.SocketOptions;
import java.net.StandardSocketOptions;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps new SocketOption classes to the old integer-based scheme.
 *
 * @author Christian Kohlschütter
 */
final class SocketOptionsMapper {
  private static final Map<SocketOption<?>, SocketOptionRef> SOCKET_OPTIONS = new HashMap<>();
  static final Set<SocketOption<?>> SUPPORTED_SOCKET_OPTIONS;

  static {
    // Interesting fact of the day:
    //
    // The old int-optID API expects SocketException to be thrown for unknown values,
    // and the new Java 9+ SocketOption API expects UnsupportedOperationException to be
    // thrown.
    //
    // However, at least up to Java 11, the new API calls the old API under the hood
    // (see SocketImpl), so in reality, SocketException is thrown then.
    //
    // In Java 15 (?), they refactored the class, and the automatic mapping no longer occurs
    // for custom socket implementations, which means we have to roll our own mapping.
    //
    // As you see below, unlike Java 11's SocketImpl, our getOption/setOption implementation
    // is not a series of chained if-statements. Instead, we have a central place where
    // the mapping is defined using generics, maps and some Java boilerplate. I don't think
    // it's necessarily faster but since we now have a single, central place where these mappings
    // are defined, it feels cleaner and better to not repeat yourself.

    registerSocketOption(StandardSocketOptions.SO_KEEPALIVE, SocketOptions.SO_KEEPALIVE, false);
    registerSocketOption(StandardSocketOptions.SO_SNDBUF, SocketOptions.SO_SNDBUF, true);
    registerSocketOption(StandardSocketOptions.SO_RCVBUF, SocketOptions.SO_RCVBUF, true);
    registerSocketOption(StandardSocketOptions.SO_REUSEADDR, SocketOptions.SO_REUSEADDR, true);
    registerSocketOption(StandardSocketOptions.SO_LINGER, SocketOptions.SO_LINGER, true);
    registerSocketOption(StandardSocketOptions.IP_TOS, SocketOptions.IP_TOS, false);
    registerSocketOption(StandardSocketOptions.TCP_NODELAY, SocketOptions.TCP_NODELAY, false);

    Set<SocketOption<?>> supportedOptions = new HashSet<>();

    for (Map.Entry<SocketOption<?>, SocketOptionRef> en : SOCKET_OPTIONS.entrySet()) {
      if (en.getValue().supported) {
        supportedOptions.add(en.getKey());
      }
    }
    SUPPORTED_SOCKET_OPTIONS = Collections.unmodifiableSet(supportedOptions);
  }

  private static <T> void registerSocketOption(SocketOption<T> option, int socketOptionsId,
      boolean supported) {
    SOCKET_OPTIONS.put(option, new SocketOptionRef(socketOptionsId, supported));
  }

  static Integer resolve(SocketOption<?> option) {
    SocketOptionRef ref = SOCKET_OPTIONS.get(option);
    if (ref == null) {
      return null;
    } else {
      return ref.optionId;
    }
  }

  private static final class SocketOptionRef {
    private final int optionId;
    private final boolean supported;

    SocketOptionRef(int optionId, boolean supported) {
      this.optionId = optionId;
      this.supported = supported;
    }
  }
}
