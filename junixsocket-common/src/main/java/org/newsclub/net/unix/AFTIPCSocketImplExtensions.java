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
import java.nio.charset.StandardCharsets;

/**
 * TIPC-specific code that resides in the native library. To be used by {@code AFTIPCSocket} and
 * {@code AFTIPCDatagramSocket} only.
 *
 * @author Christian Kohlschütter
 */
public final class AFTIPCSocketImplExtensions implements
    AFSocketImplExtensions<AFTIPCSocketAddress> {
  private final AncillaryDataSupport ancillaryDataSupport;

  AFTIPCSocketImplExtensions(AncillaryDataSupport ancillaryDataSupport) {
    this.ancillaryDataSupport = ancillaryDataSupport;
  }

  /**
   * Returns the TIPC "ErrInfo" data from the ancillary receive buffer.
   *
   * Invalid for any other use.
   *
   * @return The errinfo.
   */
  public int[] getTIPCErrInfo() {
    return ancillaryDataSupport.getTIPCErrorInfo();
  }

  /**
   * Returns the TIPC "DestName" data from the ancillary receive buffer.
   *
   * Invalid for any other use.
   *
   * @return The DestName.
   */
  public int[] getTIPCDestName() {
    return ancillaryDataSupport.getTIPCDestName();
  }

  /**
   * Retrieves the 16-byte TIPC node identity given a node hash.
   *
   * @param peer The node hash.
   * @return The node identity, or {@code  null} if unsupported.
   * @throws IOException on error.
   */
  public byte[] getTIPCNodeId(int peer) throws IOException {
    return NativeUnixSocket.tipcGetNodeId(peer);
  }

  /**
   * Retrieves the TIPC link name given a node hash and bearer Id.
   *
   * @param peer The node hash.
   * @param bearerId The bearer Id.
   * @return The link name, or {@code  null} if unsupported.
   * @throws IOException on error.
   */
  public String getTIPCLinkName(int peer, int bearerId) throws IOException {
    byte[] name = NativeUnixSocket.tipcGetLinkName(peer, bearerId);
    return name == null ? null : new String(name, StandardCharsets.UTF_8);
  }
}
