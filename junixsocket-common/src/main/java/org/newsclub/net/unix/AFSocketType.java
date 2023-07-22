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

/**
 * Describes the "type" of a socket.
 *
 * @author Christian Kohlschütter
 */
public enum AFSocketType {
  /**
   * Stream-oriented socket.
   */
  SOCK_STREAM(NativeUnixSocket.SOCK_STREAM), //

  /**
   * Datagram-oriented socket. Usually permitting data loss (but not with {@link AFUNIXSocket}).
   */
  SOCK_DGRAM(NativeUnixSocket.SOCK_DGRAM), //

  /**
   * Raw mode.
   */
  SOCK_RAW(NativeUnixSocket.SOCK_RAW), //

  /**
   * Reliably-delivered datagram messages.
   *
   * Used by {@code AFTIPCDatagramSocket} to differentiate between datagram connects that may or may
   * not permit package loss.
   */
  SOCK_RDM(NativeUnixSocket.SOCK_RDM), //

  /**
   * Sequential packet socket.
   */
  SOCK_SEQPACKET(NativeUnixSocket.SOCK_SEQPACKET), //
  ;

  private final int id;

  AFSocketType(int id) {
    this.id = id;
  }

  int getId() {
    return id;
  }
}
