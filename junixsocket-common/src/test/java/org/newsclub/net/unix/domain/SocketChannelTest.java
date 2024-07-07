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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFDatagramChannel;
import org.newsclub.net.unix.AFServerSocketChannel;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFSocketChannel;
import org.newsclub.net.unix.AFUNIXDatagramChannel;
import org.newsclub.net.unix.AFUNIXServerSocketChannel;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.jep380.JEP380AddressSpecifics;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class SocketChannelTest extends
    org.newsclub.net.unix.SocketChannelTest<AFUNIXSocketAddress> {

  public SocketChannelTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  public void testUnixDomainProtocolFamily() throws Exception {
    ProtocolFamily unix = JEP380AddressSpecifics.unixProtocolFamilyIfAvailable();
    if (unix == null) {
      throw new TestAbortedNotAnIssueException(
          "StandardProtocolFamily.UNIX is not supported by this VM");
    }

    // Java 16+: We can create JVM-specific AF_UNIX channels
    // (omitted here for compatibilty with Java 14 and lower)
    // try (SocketChannel ch = SocketChannel.open(unix)) {
    // assertNotEquals(AFUNIXSocketChannel.class, ch.getClass());
    // }

    // When we call AF*Channel#open with StandardProtocolFamily.UNIX,
    // we will get our implementations

    try (SocketChannel ch = AFSocketChannel.open(unix)) {
      assertEquals(AFUNIXSocketChannel.class, ch.getClass());
    }
    try (ServerSocketChannel ch = AFServerSocketChannel.open(unix)) {
      assertEquals(AFUNIXServerSocketChannel.class, ch.getClass());
    }
    try (DatagramChannel ch = AFDatagramChannel.open(unix)) {
      assertEquals(AFUNIXDatagramChannel.class, ch.getClass());
    }
  }

  @Override
  protected boolean mayTestBindNullThrowUnsupportedOperationException() {
    return false;
  }

  @Override
  protected boolean mayTestBindNullHaveNullLocalSocketAddress() {
    return false;
  }

  @Override
  protected void cleanupTestBindNull(ServerSocketChannel sc, SocketAddress addr) throws Exception {
    // nothing to do, as -- unlike JEP380 -- junixsocket cleans up its mess
  }
}
