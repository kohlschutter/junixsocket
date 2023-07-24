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
import java.util.Objects;

/**
 * AF_SYSTEM-specific code that resides in the native library. To be used by {@code AFSystemSocket}
 * and {@code AFSystemDatagramSocket} only.
 *
 * @author Christian Kohlschütter
 */
public final class AFSYSTEMSocketImplExtensions implements
    AFSocketImplExtensions<AFSYSTEMSocketAddress> {

  @SuppressWarnings("PMD.UnusedFormalParameter")
  AFSYSTEMSocketImplExtensions(AncillaryDataSupport ancillaryDataSupport) {
  }

  /**
   * Retrieves the kernel control ID given a kernel control name.
   *
   * @param fd The socket file descriptor.
   * @param name The control name
   * @return The control Id.
   * @throws IOException on error.
   */
  public int getKernelControlId(FileDescriptor fd, String name) throws IOException {
    return NativeUnixSocket.systemResolveCtlId(fd, Objects.requireNonNull(name));
  }
}
