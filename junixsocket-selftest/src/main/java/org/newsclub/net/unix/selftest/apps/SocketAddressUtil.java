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
package org.newsclub.net.unix.selftest.apps;

import java.io.File;
import java.net.SocketException;
import java.net.URI;

import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class SocketAddressUtil {
  public static AFSocketAddress parseAddress(String addr) throws SocketException {
    if (addr.startsWith("/")) {
      return AFUNIXSocketAddress.of(new File(addr));
    } else if (addr.startsWith("file:")) {
      return AFUNIXSocketAddress.of(URI.create(addr));
    } else {
      return AFSocketAddress.of(URI.create(addr));
    }
  }
}
