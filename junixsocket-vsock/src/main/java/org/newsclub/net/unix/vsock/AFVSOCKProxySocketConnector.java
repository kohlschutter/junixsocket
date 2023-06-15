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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFSocketConnector;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.newsclub.net.unix.AddressUnavailableSocketException;

/**
 * Provides access to AF_VSOCK connections that aren't directly accessible but exposed via a
 * proxying/multiplexing Unix domain socket.
 *
 * @author Christian Kohlschütter
 * @see #openFirecrackerStyleConnector(AFUNIXSocketAddress, int)
 * @see #openDirectConnector()
 */
public final class AFVSOCKProxySocketConnector implements
    AFSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> {
  private static final AFSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> DIRECT_CONNECTOR =
      new AFSocketConnector<AFVSOCKSocketAddress, AFSocketAddress>() {

        @Override
        public AFSocket<? extends AFSocketAddress> connect(AFVSOCKSocketAddress addr)
            throws IOException {
          return addr.newConnectedSocket();
        }
      };

  private static final Pattern PAT_OK = Pattern.compile("OK ([0-9]+)");
  private static final byte[] OK = {'O', 'K', ' '};
  private final AFUNIXSocketAddress connectorAddress;
  private final int allowedCID;

  private AFVSOCKProxySocketConnector(AFUNIXSocketAddress connectorAddress, int allowedCID) {
    this.connectorAddress = connectorAddress;
    this.allowedCID = allowedCID;
  }

  /**
   * Returns an instance that is configured to support
   * [Firecracker-style](https://github.com/firecracker-microvm/firecracker/blob/main/docs/vsock.md)
   * Unix domain sockets.
   *
   * @param connectorAddress The unix socket address pointing at the Firecracker-style multiplexing
   *          domain socket.
   * @param allowedCID The permitted CID, or {@link AFVSOCKSocketAddress#VMADDR_CID_ANY} for "any".
   * @return The instance.
   */
  public static AFSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> openFirecrackerStyleConnector(
      AFUNIXSocketAddress connectorAddress, int allowedCID) {
    return new AFVSOCKProxySocketConnector(connectorAddress, allowedCID);
  }

  /**
   * Returns an instance that is configured to connect directly to the given address.
   *
   * @return The direct instance.
   */
  public static AFSocketConnector<AFVSOCKSocketAddress, AFSocketAddress> openDirectConnector() {
    return DIRECT_CONNECTOR;
  }

  /**
   * Connects to the given AF_VSOCK address.
   *
   * @param vsockAddress The address to connect to.
   * @return The connected socket.
   * @throws IOException on error.
   * @throws AddressUnavailableSocketException if the CID is not covered by this connector.
   */
  @Override
  public AFSocket<?> connect(AFVSOCKSocketAddress vsockAddress) throws IOException {
    int cid = vsockAddress.getVSOCKCID();
    if (cid != allowedCID && cid != AFVSOCKSocketAddress.VMADDR_CID_ANY
        && allowedCID != AFVSOCKSocketAddress.VMADDR_CID_ANY) {
      throw new AddressUnavailableSocketException("Connector does not cover CID " + cid);
    }

    @SuppressWarnings("resource")
    AFUNIXSocket sock = connectorAddress.newConnectedSocket();
    InputStream in = sock.getInputStream();
    OutputStream out = sock.getOutputStream();

    boolean success = false;

    try { // NOPMD.UseTryWithResources
      String connectLine = "CONNECT " + vsockAddress.getVSOCKPort() + "\n";
      out.write(connectLine.getBytes(StandardCharsets.ISO_8859_1));

      byte[] buf = new byte[16];
      int b;
      int i = 0;
      while ((b = in.read()) != -1 && b != '\n' && i < buf.length) {
        buf[i] = (byte) b;
        if (i < 3) {
          if (OK[i] != b) {
            break;
          }
        }
        i++;
      }
      if (b == '\n' && i > 3) {
        Matcher m = PAT_OK.matcher(new String(buf, 0, i, StandardCharsets.ISO_8859_1));
        if (m.matches()) {
          /* int hostPort = */ Integer.parseInt(m.group(1));
          success = true;
        }
      }
    } finally { // NOPMD.DoNotThrowExceptionInFinally
      if (!success) {
        sock.close();
        throw new SocketException("Unexpected response from proxy socket");
      }
    }

    return sock;
  }
}
