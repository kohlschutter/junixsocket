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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

/**
 * Defines certain methods that all junixsocket AF_UNIX socket implementations share and extend
 * beyond the standard socket API.
 *
 * The set of features include methods to support working with ancillary messages (such as file
 * descriptors) as well as socket credentials.
 *
 * Keep in mind that the platform this code runs on may not support these features, and exceptions
 * may be thrown when not checking for the corresponding {@link AFUNIXSocketCapability} first.
 *
 * @author Christian Kohlschütter
 */
public interface AFUNIXSocketExtensions extends AFSocketExtensions {
  /**
   * Retrieves an array of incoming {@link FileDescriptor}s that were sent as ancillary messages,
   * along with a call to {@link InputStream#read()}, etc.
   *
   * NOTE: Another call to this method will not return the same file descriptors again (most likely,
   * an empty array will be returned).
   *
   * @return The file descriptors, or an empty array if none were available.
   * @throws IOException if the operation fails.
   */
  FileDescriptor[] getReceivedFileDescriptors() throws IOException;

  /**
   * Clears the queue of incoming {@link FileDescriptor}s that were sent as ancillary messages.
   */
  void clearReceivedFileDescriptors();

  /**
   * Sets a list of {@link FileDescriptor}s that should be sent as an ancillary message along with
   * the next write.
   *
   * Important: There can only be one set of file descriptors active until the write completes. The
   * socket also needs to be connected for this operation to succeed.
   *
   * It is also important to know that there may be an upper limit imposed by the operation system
   * as to how many file descriptors can be sent at once. Linux, for example, may support up to 253.
   * If the number of file descriptors exceeds the limit, an exception may be thrown when sending
   * data along with the ancillary message containing the file descriptors.
   *
   * @param fdescs The file descriptors, or {@code null} if none.
   * @throws IOException if the operation fails.
   */
  void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException;

  /**
   * Returns {@code true} if there are pending file descriptors to be sent as part of an ancillary
   * message.
   *
   * @return {@code true} if there are file descriptors pending.
   */
  boolean hasOutboundFileDescriptors();

  /**
   * Retrieves the "peer credentials" for this connection.
   *
   * These credentials may be useful to authenticate the other end of the socket (client or server).
   *
   * Depending on the socket/connection/environment, you may not receive any or all credentials. For
   * example, on Linux, {@link AFUNIXDatagramSocket} and {@link AFUNIXDatagramChannel} may not be
   * able to retrieve credentials at all.
   *
   * @return The peer's credentials, or {@code null} if they couldn't be retrieved.
   * @throws IOException If there was an error returning these credentials.
   */
  AFUNIXSocketCredentials getPeerCredentials() throws IOException;
}
