/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
 * The status of a connection with the remote peer, which can also be "unknown".
 * 
 * @see AFSocket#getConnectionStatus()
 */
public enum ConnectionStatus {
  /**
   * Not connected.
   */
  NOT_CONNECTED,

  /**
   * Unknown.
   */
  UNKNOWN,

  /**
   * Connected.
   */
  CONNECTED;

  /**
   * Checks if the status can be assumed "connected".
   * 
   * @return {@code true} if connected.
   */
  public boolean isConnected() {
    return this == CONNECTED;
  }

  /**
   * Checks if the status can be assumed "not connected".
   * 
   * @return {@code true} if not connected.
   */
  public boolean isNotConnected() {
    return this == NOT_CONNECTED;
  }

  /**
   * Checks if the status can not be determined.
   * 
   * @return {@code true} if status unknown.
   */
  public boolean isUnknown() {
    return this == UNKNOWN;
  }
}