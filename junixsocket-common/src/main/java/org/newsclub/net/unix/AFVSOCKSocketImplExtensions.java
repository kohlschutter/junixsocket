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

import java.io.IOException;

/**
 * VSOCK-specific code that resides in the native library. To be used by {@code AFVSOCKSocket} and
 * {@code AFVSOCKDatagramSocket} only.
 *
 * @author Christian Kohlschütter
 */
public final class AFVSOCKSocketImplExtensions implements
    AFSocketImplExtensions<AFVSOCKSocketAddress> {
  @SuppressWarnings("unused")
  private final AncillaryDataSupport ancillaryDataSupport; // NOPMD

  AFVSOCKSocketImplExtensions(AncillaryDataSupport ancillaryDataSupport) {
    this.ancillaryDataSupport = ancillaryDataSupport;
  }

  /**
   * Returns the local CID.
   *
   * If the system does not support vsock, or status about support cannot be retrieved, -1
   * ({@link AFVSOCKSocketAddress#VMADDR_CID_ANY}) is returned.
   *
   * The value may be cached upon initialization of the library.
   *
   * @return The CID, or -1.
   * @throws IOException on error.
   */
  public int getLocalCID() throws IOException {
    return NativeUnixSocket.vsockGetLocalCID();
  }
}
