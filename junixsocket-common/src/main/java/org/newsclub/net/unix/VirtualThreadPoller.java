/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;

/**
 * Helper class to support polling "virtually blocked" file descriptors for virtual threads.
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
interface VirtualThreadPoller {
  /**
   * Returns the default instance best suited for the current system.
   */
  VirtualThreadPoller INSTANCE = new VirtualThreadPollerNaive();

  /**
   * Parks the current thread until the given file descriptor is ready, with respect to the given
   * readiness mode.
   *
   * @param fd The file descriptor to check.
   * @param mode The mode (bitmask of {@link SelectionKey#OP_READ}, {@link SelectionKey#OP_WRITE},
   *          {@link SelectionKey#OP_ACCEPT}, {@link SelectionKey#OP_CONNECT})
   * @param now The refence time (in millis) for the timeout
   * @param timeout (in seconds), or 0 for infinite
   * @param closeOnInterrupt Callback to call upon interrupt (usually to close the resource working
   *          on the file descriptor)
   * @throws SocketTimeoutException on timeout
   * @throws SocketClosedByInterruptException on interrupt.
   * @throws IOException on other error
   */
  void parkThreadUntilReady(FileDescriptor fd, /* SelectionKey.OP_ */ int mode, long now,
      AFSupplier<Integer> timeout, Closeable closeOnInterrupt) throws IOException;
}
