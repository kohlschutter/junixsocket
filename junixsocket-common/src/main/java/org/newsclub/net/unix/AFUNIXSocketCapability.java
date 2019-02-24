/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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

/**
 * Describes junixsocket capabilities the current environment (system platform, native library,
 * etc.) may or may not support.
 * 
 * You can check whether your environment supports a given capability by calling
 * {@link AFUNIXSocket#supports(AFUNIXSocketCapability)}.
 */
public enum AFUNIXSocketCapability {
  // see org_newsclub_net_unix_NativeUnixSocket.c in junixsocket-native

  /** Socket supports retrieving peer credentials. */
  CAPABILITY_PEER_CREDENTIALS(0), //

  /** Socket supports sending and receiving ancillary messages. */
  CAPABILITY_ANCILLARY_MESSAGES(1), //

  /** Socket supports passing file descriptors via ancillary messages. */
  CAPABILITY_FILE_DESCRIPTORS(2), //

  /** Socket addressing supports the abstract namespace (Linux/Windows). */
  CAPABILITY_ABSTRACT_NAMESPACE(3);

  private final int bitmask;

  AFUNIXSocketCapability(int bit) {
    this.bitmask = 1 << bit;
  }

  int getBitmask() {
    return bitmask;
  }
}
