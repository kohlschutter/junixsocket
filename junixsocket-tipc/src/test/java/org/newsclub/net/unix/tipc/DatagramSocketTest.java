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

import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement({
    AFSocketCapability.CAPABILITY_TIPC, AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS})
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class DatagramSocketTest extends
    org.newsclub.net.unix.DatagramSocketTest<AFTIPCSocketAddress> {

  public DatagramSocketTest() {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Override
  protected void assertUnconnectedUnbound(DatagramSocket ds) {
    // with TIPC, datagram sockets get autobound upon calling getsockopt (?)
    // so let's not check this here
  }

  @Override
  protected void assertBoundAddrIdenticalToLocalAddress(DatagramSocket ds, SocketAddress boundAddr)
      throws SocketException {
    // with TIPC, when we bind on a Service address, the local address will still be a different
    // one, of type Socket
  }

  @Override
  protected void assertRemoteAddress(DatagramSocket ds, SocketAddress remoteAddr)
      throws SocketException {
    // with TIPC, we may not get a remote address
  }
}
