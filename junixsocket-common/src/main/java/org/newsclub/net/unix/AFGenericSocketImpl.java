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
import java.net.SocketException;

final class AFGenericSocketImpl extends AFSocketImpl<AFGenericSocketAddress> {
  AFGenericSocketImpl(FileDescriptor fdObj) {
    super(AFGenericSelectorProvider.AF_GENERIC, fdObj);
  }

  @Override
  public Object getOption(int optID) throws SocketException {
    return getOptionLenient(optID);
  }

  @Override
  public void setOption(int optID, Object value) throws SocketException {
    setOptionLenient(optID, value);
  }
}
