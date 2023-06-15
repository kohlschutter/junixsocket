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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileDescriptor;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Tests some otherwise uncovered methods of {@link AFUNIXSocket}.
 *
 * @author Christian Kohlschütter
 */
@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION",
    "NM_SAME_SIMPLE_NAME_AS_SUPERCLASS"})
public final class SocketTest extends org.newsclub.net.unix.SocketTest<AFUNIXSocketAddress> {

  public SocketTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  public void testMain() throws Exception {
    AFUNIXSocket.main(new String[0]);
  }

  @Test
  public void testVersion() throws Exception {
    assertNotEquals("", AFUNIXSocket.getVersion());
    // see junixsocket-rmi JunixSocketVersionTest
  }

  @Test
  public void testSupported() throws Exception {
    assertTrue(AFUNIXSocket.isSupported());
  }

  @Test
  public void testLoadedLibrary() throws Exception {
    assertNotEquals("", AFUNIXSocket.getLoadedLibrary());
  }

  @Test
  public void testSupports() throws Exception {
    for (AFSocketCapability cap : AFSocketCapability.values()) {
      AFSocket.supports(cap);
    }
  }

  @Test
  public void testReceivedFileDescriptorsUnconnected() throws Exception {
    try (AFUNIXSocket sock = AFUNIXSocket.newInstance()) {
      // We don't check socket status, so these calls are perfectly fine for unconnected sockets.
      assertArrayEquals(new FileDescriptor[0], sock.getReceivedFileDescriptors());
      sock.clearReceivedFileDescriptors();
    }
  }
}
