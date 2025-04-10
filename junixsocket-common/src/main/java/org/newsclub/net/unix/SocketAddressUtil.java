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

import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnixDomainSocketAddress;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/**
 * {@link SocketAddress}-related helper methods.
 *
 * @author Christian Kohlschütter
 */
@IgnoreJRERequirement // see src/main/java15
final class SocketAddressUtil {
  private SocketAddressUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Try to convert a {@link SocketAddress} that is not a {@link AFSocketAddress} to one that is.
   *
   * @param address The address.
   * @return A supplier for the given address, or {@code null}.
   */
  static AFSupplier<? extends AFSocketAddress> supplyAFSocketAddress(SocketAddress address) {
    return supplyAFUNIXSocketAddress(address);
  }

  /**
   * Try to convert a {@link SocketAddress} that is not a {@link AFUNIXSocketAddress} to one that
   * is.
   *
   * @param address The address.
   * @return A supplier for the given address, or {@code null}.
   */
  static AFSupplier<AFUNIXSocketAddress> supplyAFUNIXSocketAddress(SocketAddress address) {
    if (address instanceof UnixDomainSocketAddress) {
      UnixDomainSocketAddress udsa = (UnixDomainSocketAddress) address;

      return () -> {
        try {
          return AFUNIXSocketAddress.of(udsa.getPath());
        } catch (SocketException e) {
          e.printStackTrace();
          return null;
        }
      };
    } else {
      return null;
    }
  }
}
