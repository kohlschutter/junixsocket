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
package org.newsclub.net.unix.vsock;

import java.io.File;
import java.io.IOException;

import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFServerSocketConnector;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.newsclub.net.unix.AddressUnavailableSocketException;

/**
 * Provides access to AF_VSOCK connections that aren't directly accessible but exposed via a
 * proxying/multiplexing Unix domain socket.
 *
 * @author Christian Kohlschütter
 * @see #openFirecrackerStyleConnector(File, int)
 * @see #openDirectConnector()
 */
public final class AFVSOCKProxyServerSocketConnector implements
    AFServerSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> {
  private static final AFServerSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> DIRECT_CONNECTOR =
      new AFServerSocketConnector<AFVSOCKSocketAddress, AFSocketAddress>() {

        @Override
        public AFServerSocket<? extends AFSocketAddress> bind(AFVSOCKSocketAddress addr)
            throws IOException {
          return addr.newForceBoundServerSocket();
        }
      };

  private final String listenAddressPrefix;
  private final int allowedCID;

  private AFVSOCKProxyServerSocketConnector(String listenAddressPrefix, int allowedCID) {
    this.listenAddressPrefix = listenAddressPrefix;
    this.allowedCID = allowedCID;
  }

  /**
   * Returns an instance that is configured to support
   * [Firecracker-style](https://github.com/firecracker-microvm/firecracker/blob/main/docs/vsock.md)
   * Unix domain sockets.
   *
   * @param listenAddressPrefix The prefix of any listening socket. The actual socket will have
   *          <code>_<em>vsockPort</em></code> appended to it (with {@code vsockPort} being replaced
   *          by the corresponding port number).
   * @param allowedCID The permitted CID, or {@link AFVSOCKSocketAddress#VMADDR_CID_ANY} for "any".
   * @return The instance.
   */
  public static AFServerSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> openFirecrackerStyleConnector(
      File listenAddressPrefix, int allowedCID) {
    return new AFVSOCKProxyServerSocketConnector(listenAddressPrefix.getAbsolutePath(), allowedCID);
  }

  /**
   * Returns an instance that is configured to connect directly to the given address.
   *
   * @return The direct instance.
   */
  public static AFServerSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> openDirectConnector() {
    return DIRECT_CONNECTOR;
  }

  @Override
  public AFServerSocket<?> bind(AFVSOCKSocketAddress addr) throws IOException {
    int cid = addr.getVSOCKCID();
    if (cid != allowedCID && cid != AFVSOCKSocketAddress.VMADDR_CID_ANY
        && allowedCID != AFVSOCKSocketAddress.VMADDR_CID_ANY) {
      throw new AddressUnavailableSocketException("Factory does not cover CID " + cid);
    }

    return AFUNIXSocketAddress.of(new File(listenAddressPrefix + "_" + addr.getVSOCKPort()))
        .newForceBoundServerSocket();
  }
}
