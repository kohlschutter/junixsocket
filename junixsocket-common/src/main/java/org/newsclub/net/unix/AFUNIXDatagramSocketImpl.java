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

final class AFUNIXDatagramSocketImpl extends AFDatagramSocketImpl<AFUNIXSocketAddress> {
  AFUNIXDatagramSocketImpl(FileDescriptor fd) throws IOException {
    this(fd, AFSocketType.SOCK_DGRAM);
  }

  AFUNIXDatagramSocketImpl(FileDescriptor fd, AFSocketType socketType) throws IOException {
    super(AFUNIXSocketAddress.AF_UNIX, fd, socketType);
  }

  AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    return NativeUnixSocket.peerCredentials(fd, new AFUNIXSocketCredentials());
  }
}
