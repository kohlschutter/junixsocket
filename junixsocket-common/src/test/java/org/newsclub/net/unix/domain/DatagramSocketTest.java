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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFSocketType;
import org.newsclub.net.unix.AFUNIXDatagramChannel;
import org.newsclub.net.unix.AFUNIXDatagramSocket;
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
  public void testSeqPacketPair() throws Exception {
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

  @Test
  public void testSeqPacket() throws Exception {
    boolean gotInstance = false;
    try (AFUNIXDatagramSocket s1 = AFUNIXDatagramSocket.newInstance(AFSocketType.SOCK_SEQPACKET)) {
      gotInstance = true;
      AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();
      s1.bind(addr);
      s1.listen(0);

      CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
        try {
          AFDatagramSocket<AFUNIXSocketAddress> s;
          s = s1.accept();

          ByteBuffer bb = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
          s.getChannel().receive(bb);
          bb.flip();
          assertEquals(4, bb.remaining());
          assertEquals(0x04030201, bb.getInt());
        } catch (IOException e) {
          fail(e);
        }
      });

      try (AFUNIXDatagramSocket s2 = AFUNIXDatagramSocket.newInstance(
          AFSocketType.SOCK_SEQPACKET)) {
        s2.connect(addr);

        s2.getChannel().send(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}), addr);
      }

      cf.get(5, TimeUnit.SECONDS);

    } catch (OperationNotSupportedSocketException e) {
      if (!gotInstance) {
        throw new TestAbortedNotAnIssueException("SEQPACKET not supported", e);
      }
    }
  }
}
