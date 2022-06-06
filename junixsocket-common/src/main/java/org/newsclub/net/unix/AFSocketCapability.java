/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import java.lang.ProcessBuilder.Redirect;

/**
 * Describes junixsocket capabilities the current environment (system platform, native library,
 * etc.) may or may not support.
 * 
 * You can check whether your environment supports a given capability by calling
 * {@link AFSocket#supports(AFSocketCapability)}.
 */
public enum AFSocketCapability {
  // see org_newsclub_net_unix_NativeUnixSocket.c in junixsocket-native

  /** Socket supports retrieving peer credentials. */
  CAPABILITY_PEER_CREDENTIALS(0),

  /** Socket supports sending and receiving ancillary messages. */
  CAPABILITY_ANCILLARY_MESSAGES(1),

  /** Socket supports passing file descriptors via ancillary messages. */
  CAPABILITY_FILE_DESCRIPTORS(2),

  /** Socket addressing supports the abstract namespace (Linux). */
  CAPABILITY_ABSTRACT_NAMESPACE(3),

  /** Support for AF_UNIX datagrams (not on Windows yet). */
  CAPABILITY_UNIX_DATAGRAMS(4),

  /**
   * A pair of interconnected sockets can be created natively as AF_UNIX sockets.
   * 
   * This currently not possible on Windows, but instead emulated via anonymous AF_INET ports when
   * you use {@link AFSocketPair}. Other systems, such as OSv, may provide partial implementations
   * of pipe-based (i.e., non-socket) pairs.
   */
  CAPABILITY_NATIVE_SOCKETPAIR(5),

  /**
   * A file descriptor can be converted to {@link Redirect}.
   * 
   * This feature currently uses Java SDK internals that may change/disappear.
   */
  CAPABILITY_FD_AS_REDIRECT(6),

  /**
   * Support for AF_TIPC.
   * 
   * Availability of this feature is checked upon launch and therefore loading the "tipc" kernel
   * module at a later point may not be properly reflected.
   */
  CAPABILITY_TIPC(7),

  /**
   * Support for AF_UNIX.
   * 
   * Availability of this feature is checked upon launch and therefore, on systems adding support at
   * a later point, may not be properly reflected when checking at a later point.
   * 
   * NOTE: While this capability is typically supported on most systems that can actually load a
   * junixsocket JNI library, it is unavailable for older Windows versions (such as 8.1, 10 before
   * AFUNIX.SYS was included, etc.) and on systems where support for UNIX domain sockets is actively
   * disabled. Therefore, it is still recommended to check for this capability.
   */
  CAPABILITY_UNIX_DOMAIN(8),

  ; // end of list

  private final int bitmask;

  AFSocketCapability(int bit) {
    this.bitmask = 1 << bit;
  }

  int getBitmask() {
    return bitmask;
  }
}
