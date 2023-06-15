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

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.AFTIPCSocketAddress.Scope;
import org.newsclub.net.unix.tipc.AFTIPCErrInfo.ErrorCode;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class AncillaryMessageTest extends
    org.newsclub.net.unix.AncillaryMessageTest<AFTIPCSocketAddress> {
  public AncillaryMessageTest() throws IOException {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Test
  public void testConnectionAbort() throws IOException {
    AFTIPCServerSocket serverSocket = (AFTIPCServerSocket) newServerSocket();
    serverSocket.bind(AFTIPCSocketAddress.ofService(Scope.SCOPE_NODE, 1234, 0));

    AFTIPCSocketChannel socket = (AFTIPCSocketChannel) newSocket().getChannel();
    socket.setAncillaryReceiveBufferSize(8192);

    socket.configureBlocking(false);
    socket.connect(serverSocket.getLocalSocketAddress());
    Socket clientSocket = serverSocket.accept();

    assertEquals(clientSocket.getRemoteSocketAddress(), socket.getLocalAddress());
    socket.configureBlocking(true);
    new Thread(() -> {
      try {
        clientSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }).start();

    ByteBuffer buf = ByteBuffer.allocate(4096);
    assertEquals(-1, socket.read(buf));
    assertEquals(0, buf.position());

    // get and clear the error info
    assertEquals(new AFTIPCErrInfo(ErrorCode.TIPC_ERR_CONN_SHUTDOWN, 0), socket.getErrInfo());
    // subsequent calls return null
    assertEquals(null, socket.getErrInfo());
  }

  @Test
  public void testDestName() throws IOException, InterruptedException, ExecutionException {
    AFTIPCServerSocket serverSocket = (AFTIPCServerSocket) newServerSocket();

    AFTIPCSocketAddress bindAddress = AFTIPCSocketAddress.ofServiceRange(Scope.SCOPE_NODE, 1234, 56,
        910);

    serverSocket.bind(bindAddress);
    AFTIPCSocketChannel socket = (AFTIPCSocketChannel) newSocket().getChannel();

    socket.configureBlocking(false);
    // socket.connect(serverSocket.getLocalSocketAddress());

    AFTIPCSocketAddress connectAddress = AFTIPCSocketAddress.ofService(Scope.SCOPE_NODE, 1234, 78);
    socket.connect(connectAddress);
    AFTIPCSocket clientSocket = serverSocket.accept();
    clientSocket.setAncillaryReceiveBufferSize(8192);

    assertEquals(clientSocket.getRemoteSocketAddress(), socket.getLocalAddress());
    socket.configureBlocking(true);

    CompletableFuture<Integer> bytesSent = new CompletableFuture<>();
    new Thread(() -> {
      try {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        bytesSent.complete(clientSocket.getChannel().read(buf));
      } catch (IOException e) {
        bytesSent.completeExceptionally(e);
      }
    }).start();

    ByteBuffer buf = ByteBuffer.allocate(64);
    int written = socket.write(buf);
    assertEquals(bytesSent.get(), written);
    assertEquals(64, bytesSent.get());

    // This feature allows us to read the "connect address" from the server-side
    assertEquals(connectAddress, clientSocket.getDestName().toSocketAddress(connectAddress
        .getScope(), false));
  }
}
