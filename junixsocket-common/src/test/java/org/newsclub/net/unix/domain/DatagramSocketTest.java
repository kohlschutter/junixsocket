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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFSocketType;
import org.newsclub.net.unix.AFUNIXDatagramChannel;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketPair;
import org.newsclub.net.unix.OperationNotSupportedSocketException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

@AFSocketCapabilityRequirement({
    AFSocketCapability.CAPABILITY_UNIX_DOMAIN, AFSocketCapability.CAPABILITY_UNIX_DATAGRAMS})
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class DatagramSocketTest extends
    org.newsclub.net.unix.DatagramSocketTest<AFUNIXSocketAddress> {

  public DatagramSocketTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  public void testSeqPacket() throws Exception {
    AFUNIXSocketPair<AFUNIXDatagramChannel> pair;
    try {
      pair = AFUNIXSocketPair.openDatagram(AFSocketType.SOCK_SEQPACKET);
    } catch (OperationNotSupportedSocketException e) {
      throw new TestAbortedNotAnIssueException("SEQPACKET not supported", e);
    }

    String msg = "Hello World";

    ByteBuffer buf = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
    ByteBuffer dst = ByteBuffer.allocate(64);
    pair.getSocket1().send(buf, null);
    int r = pair.getSocket2().read(dst);
    assertEquals(buf.limit(), r);
    dst.flip();
    assertEquals(buf.limit(), dst.limit());

    assertEquals(msg, StandardCharsets.UTF_8.decode(dst).toString());
  }
}
