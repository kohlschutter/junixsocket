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

import java.io.FileDescriptor;
import java.io.IOException;

import org.newsclub.net.unix.AFDatagramSocketImpl;
import org.newsclub.net.unix.AFSocketType;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

final class AFVSOCKDatagramSocketImpl extends AFDatagramSocketImpl<AFVSOCKSocketAddress> {
  protected AFVSOCKDatagramSocketImpl(FileDescriptor fd, AFSocketType socketType)
      throws IOException {
    super(AFVSOCKSelectorProvider.AF_VSOCK, fd, socketType);
  }
}
