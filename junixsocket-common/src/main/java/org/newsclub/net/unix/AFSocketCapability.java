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

import java.lang.ProcessBuilder.Redirect;

/**
 * Describes junixsocket capabilities the current environment (system platform, native library,
 * etc.) may or may not support.
 *
 * You can check whether your environment supports a given capability by calling
 * {@link AFSocket#supports(AFSocketCapability)}.
 *
 * You can also manually disable a given capability by specifying a System property of the form
 * <code>org.newsclub.net.unix.library.disable.<em>CAPABILITY_SOMETHING_SOMETHING</em>=true</code>
 * when invoking the JVM (make sure this property is set before junixsocket is accessed).
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
   * you use {@link AFSocketPair}. Other systems may provide partial implementations of pipe-based
   * (i.e., non-socket) pairs.
   *
   * This capability is specific to AF_UNIX sockets. Other sockets, such as AF_VSOCK, may not
   * implement socketpair natively even if this capability is set, but would work-around that
   * limitation in a similar fashion but maybe without resorting to AF_INET.
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

  /**
   * Support for AF_VSOCK.
   *
   * Availability of this feature is checked upon launch and therefore enabling vsock at a later
   * point may not be properly reflected.
   *
   * @see #CAPABILITY_VSOCK_DGRAM
   */
  CAPABILITY_VSOCK(9),

  /**
   * Support for AF_VSOCK datagrams (not all platforms/kernel versions or configurations support
   * this).
   *
   * Availability of this feature is checked upon launch and therefore enabling vsock at a later
   * point may not be properly reflected.
   */
  CAPABILITY_VSOCK_DGRAM(10),

  /**
   * Support for zero-length send(2).
   *
   * This can be used to perform a connection check, but not all operating systems support this or
   * behave correctly (particularly, IBM AIX, IBM i, and IBM z/OS) at the moment.
   *
   * If not supported, junixsocket will simply ignore writes of zero-length, and connection checking
   * with {@link AFSocket#checkConnectionClosed()} may return {@code false} regardless of the actual
   * condition.
   */
  CAPABILITY_ZERO_LENGTH_SEND(11),

  /**
   * Support for "unsafe" operations.
   *
   * Trading-in safety for speed or simplicity may be justified sometimes.
   *
   * @see Unsafe
   * @see AFSocket#ensureUnsafeSupported()
   */
  CAPABILITY_UNSAFE(12),

  /**
   * Support for port numbers larger than 65535 (0xffff).
   *
   * Not all systems allow setting port numbers beyond the default TCP range (we use JNI tricks for
   * that). This capability is required for RMI support.
   */
  CAPABILITY_LARGE_PORTS(13),

  /**
   * Support for certain Darwin (macOS Kernel)-specific features, such as the AF_SYSTEM domain.
   */
  CAPABILITY_DARWIN(14),

  ; // end of list

  private final int bitmask;

  AFSocketCapability(int bit) {
    this.bitmask = 1 << bit;
  }

  int getBitmask() {
    return bitmask;
  }
}
