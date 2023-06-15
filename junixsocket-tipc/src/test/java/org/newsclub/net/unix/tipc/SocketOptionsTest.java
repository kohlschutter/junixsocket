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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketAddress.Scope;
import org.newsclub.net.unix.tipc.AFTIPCGroupRequest.GroupRequestFlags;
import org.newsclub.net.unix.tipc.AFTIPCSocketOptions.MessageImportance;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION",
    "NM_SAME_SIMPLE_NAME_AS_SUPERCLASS"})
public final class SocketOptionsTest extends
    org.newsclub.net.unix.SocketOptionsTest<AFTIPCSocketAddress> {
  public SocketOptionsTest() throws IOException {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Test
  public void testTIPCConnTimeout() throws Exception {
    Socket socket = newSocket();
    @SuppressWarnings("null")
    int timeout = socket.getOption(AFTIPCSocketOptions.TIPC_CONN_TIMEOUT);

    int newTimeout = getRandom().nextInt(8000) + 1;
    if (newTimeout == timeout) {
      newTimeout++;
    }
    socket.setOption(AFTIPCSocketOptions.TIPC_CONN_TIMEOUT, newTimeout);
    assertEquals(newTimeout, socket.getOption(AFTIPCSocketOptions.TIPC_CONN_TIMEOUT));
    socket.setOption(AFTIPCSocketOptions.TIPC_CONN_TIMEOUT, timeout);
    assertEquals(timeout, socket.getOption(AFTIPCSocketOptions.TIPC_CONN_TIMEOUT));
  }

  @Test
  public void testTIPCImportance() throws Exception {
    Socket socket = newSocket();

    @SuppressWarnings("null")
    MessageImportance imp = socket.getOption(AFTIPCSocketOptions.TIPC_IMPORTANCE);

    socket.setOption(AFTIPCSocketOptions.TIPC_IMPORTANCE, MessageImportance.HIGH);
    assertEquals(MessageImportance.HIGH, socket.getOption(AFTIPCSocketOptions.TIPC_IMPORTANCE));

    socket.setOption(AFTIPCSocketOptions.TIPC_IMPORTANCE, imp);

    // unsupported importance should throw an exception
    assertThrows(SocketException.class, () -> socket.setOption(AFTIPCSocketOptions.TIPC_IMPORTANCE,
        MessageImportance.ofValue(129)));

    assertEquals(imp, socket.getOption(AFTIPCSocketOptions.TIPC_IMPORTANCE));
  }

  @Test
  public void testTIPCSourceDroppable() throws Exception {
    // NOTE: casting to work around GraalVM issue
    AFTIPCDatagramSocket socket = (AFTIPCDatagramSocket) newDatagramSocket();

    boolean droppable = socket.getOption(AFTIPCSocketOptions.TIPC_SRC_DROPPABLE);
    assertTrue(droppable, "Datagram messages should be droppable by default");

    socket.setOption(AFTIPCSocketOptions.TIPC_SRC_DROPPABLE, !droppable);
    assertEquals(!droppable, socket.getOption(AFTIPCSocketOptions.TIPC_SRC_DROPPABLE));
  }

  @Test
  public void testTIPCDestDroppable() throws Exception {
    // NOTE: casting to work around GraalVM issue
    AFTIPCDatagramSocket socket = (AFTIPCDatagramSocket) newDatagramSocket();

    boolean droppable = socket.getOption(AFTIPCSocketOptions.TIPC_DEST_DROPPABLE);
    assertTrue(droppable, "Datagram messages should be droppable by default");

    socket.setOption(AFTIPCSocketOptions.TIPC_DEST_DROPPABLE, !droppable);
    assertEquals(!droppable, socket.getOption(AFTIPCSocketOptions.TIPC_DEST_DROPPABLE));
  }

  @Test
  public void testTIPCNodelay() throws Exception {
    Socket socket = newSocket();
    try {
      socket.getOption(AFTIPCSocketOptions.TIPC_NODELAY);
    } catch (SocketException e) {
      // expected on older kernels
    }
  }

  @SuppressWarnings("null")
  @Test
  public void testGroupJoinLeave() throws Exception {
    try (DatagramChannel socket = newDatagramChannel()) {
      try {
        assertEquals(AFTIPCGroupRequest.NONE, socket.getOption(
            AFTIPCSocketOptions.TIPC_GROUP_JOIN));
      } catch (SocketException e) {
        // on older kernels that don't support group requests
        assumeTrue(false, "Not supported by kernel");
        return;
      }

      socket.setOption(AFTIPCSocketOptions.TIPC_GROUP_JOIN, AFTIPCGroupRequest.with(4711, 0,
          Scope.SCOPE_NODE, GroupRequestFlags.NONE));
      assertEquals(4711, socket.getOption(AFTIPCSocketOptions.TIPC_GROUP_JOIN).getType());

      try {
        socket.write(ByteBuffer.allocate(64));
      } catch (NoRouteToHostException e) {
        // expected as long as there's no other member in the group
      }

      socket.setOption(AFTIPCSocketOptions.TIPC_GROUP_LEAVE, AFTIPCSocketOptions.VOID);
    }
  }

  @Test
  public void testGroupLoopback() throws Exception {
    try (AFTIPCDatagramChannel socket = (AFTIPCDatagramChannel) newDatagramChannel()) {
      try {
        assertEquals(AFTIPCGroupRequest.NONE, socket.getOption(
            AFTIPCSocketOptions.TIPC_GROUP_JOIN));
      } catch (SocketException e) {
        // on older kernels that don't support group requests
        assumeTrue(false, "Not supported by kernel");
        return;
      }

      socket.setOption(AFTIPCSocketOptions.TIPC_GROUP_JOIN, AFTIPCGroupRequest.with(4712, 0,
          Scope.SCOPE_NODE, GroupRequestFlags.GROUP_LOOPBACK));
      assertEquals(4712, socket.getOption(AFTIPCSocketOptions.TIPC_GROUP_JOIN).getType());

      ByteBuffer sendBuffer = ByteBuffer.allocate(8192);
      socket.write(sendBuffer); // should not throw thanks to
                                // GroupRequestFlags.GROUP_LOOPBACK

      ByteBuffer recvBuffer = ByteBuffer.allocate(16384);
      int len = socket.read(recvBuffer);
      if (len > 0) {
        // We've received the undeliverable message as loopback
        assertEquals(sendBuffer.position(), len);
        assertEquals(recvBuffer.position(), len);
      }
      sendBuffer.clear();
      recvBuffer.clear();

      socket.send(sendBuffer, null); // should not throw thanks to
                                     // GroupRequestFlags.GROUP_LOOPBACK

      AFTIPCSocketAddress sa = socket.receive(recvBuffer);
      assertEquals(socket.getLocalAddress(), sa); // loopback message from self

      if (len > 0) {
        // We've received the undeliverable message as loopback
        assertEquals(sendBuffer.position(), len);
        assertEquals(recvBuffer.position(), len);
      }

      socket.setOption(AFTIPCSocketOptions.TIPC_GROUP_LEAVE, AFTIPCSocketOptions.VOID);
    }
  }

  @Test
  public void testCommunication() throws Exception {
    try (DatagramChannel socket1 = newDatagramChannel();
        DatagramChannel socket2 = newDatagramChannel();) {

      // enforce reliable communication
      socket1.setOption(AFTIPCSocketOptions.TIPC_DEST_DROPPABLE, false);
      socket2.setOption(AFTIPCSocketOptions.TIPC_DEST_DROPPABLE, false);

      ByteBuffer sendBuffer = ByteBuffer.allocate(8192);
      ByteBuffer recvBuffer = ByteBuffer.allocate(16384);
      socket1.send(sendBuffer, socket2.getLocalAddress());

      assertEquals(socket1.getLocalAddress(), socket2.receive(recvBuffer));
    }
  }

  @Test
  public void testGroupCommunication() throws Exception {
    try (DatagramChannel socket1 = newDatagramChannel();
        DatagramChannel socket2 = newDatagramChannel();) {

      // enforce reliable communication
      socket1.setOption(AFTIPCSocketOptions.TIPC_DEST_DROPPABLE, false);
      socket2.setOption(AFTIPCSocketOptions.TIPC_DEST_DROPPABLE, false);

      socket1.setOption(AFTIPCSocketOptions.TIPC_GROUP_JOIN, AFTIPCGroupRequest.with(4713, 0,
          Scope.SCOPE_NODE, GroupRequestFlags.NONE));
      socket2.setOption(AFTIPCSocketOptions.TIPC_GROUP_JOIN, AFTIPCGroupRequest.with(4713, 0,
          Scope.SCOPE_NODE, GroupRequestFlags.NONE));

      ByteBuffer sendBuffer = ByteBuffer.allocate(8192);
      ByteBuffer recvBuffer = ByteBuffer.allocate(16384);
      int sent = socket1.write(sendBuffer);

      assertEquals(socket1.getLocalAddress(), socket2.receive(recvBuffer));
      assertEquals(sent, sendBuffer.position());
      assertEquals(sent, recvBuffer.position());
    }
  }
}
