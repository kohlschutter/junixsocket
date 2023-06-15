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
package org.newsclub.net.unix.tipc;

import org.newsclub.net.unix.AFSocketExtensions;

/**
 * Defines certain methods that all junixsocket AF_TIPC socket implementations share and extend
 * beyond the standard socket API.
 *
 * @author Christian Kohlschütter
 */
public interface AFTIPCSocketExtensions extends AFSocketExtensions {
  /**
   * Returns the TIPC "ErrInfo" information from the ancillary receive buffer (if any was set), or
   * {@code null} if no error was retrieved.
   *
   * @return The ErrInfo.
   */
  AFTIPCErrInfo getErrInfo();

  /**
   * Returns the TIPC "DestName" information from the ancillary receive buffer (if any was set), or
   * {@code null} if no DestName was retrieved.
   *
   * @return The service address or range (without scope) that was specified by the sender of the
   *         message.
   */
  AFTIPCDestName getDestName();
}
