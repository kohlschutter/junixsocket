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
 * Defines certain methods that all junixsocket socket implementations share and extend beyond the
 * standard socket API.
 *
 * The set of features include methods to support working with ancillary messages (such as file
 * descriptors) as well as socket credentials.
 *
 * Keep in mind that the platform this code runs on may not support these features, and exceptions
 * may be thrown when not checking for the corresponding {@link AFSocketCapability} first.
 *
 * @author Christian Kohlschütter
 */
public interface AFSocketExtensions {
  /**
   * Returns the size of the receive buffer for ancillary messages (in bytes).
   *
   * @return The size.
   */
  int getAncillaryReceiveBufferSize();

  /**
   * Sets the size of the receive buffer for ancillary messages (in bytes).
   *
   * To disable handling ancillary messages, set it to 0 (default).
   *
   * @param size The size.
   */
  void setAncillaryReceiveBufferSize(int size);

  /**
   * Ensures a minimum ancillary receive buffer size.
   *
   * @param minSize The minimum size (in bytes).
   */
  void ensureAncillaryReceiveBufferSize(int minSize);
}
