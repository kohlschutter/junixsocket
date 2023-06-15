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
package org.newsclub.net.unix.tipc;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class SocketChannelTest extends
    org.newsclub.net.unix.SocketChannelTest<AFTIPCSocketAddress> {

  public SocketChannelTest() {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Override
  protected SocketAddress resolveAddressForSecondBind(SocketAddress originalAddress,
      ServerSocketChannel ssc) throws IOException {
    // TIPC: Can't bind on local socket address
    return originalAddress;
  }

  @Override
  protected boolean socketDomainPermitsDoubleBind() {
    // TIPC: Binding on service address/range is generally allowed
    return true;
  }
}
