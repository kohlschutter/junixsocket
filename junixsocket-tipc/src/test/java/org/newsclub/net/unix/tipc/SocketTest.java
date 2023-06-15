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

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Tests some otherwise uncovered methods of {@link AFTIPCSocket}.
 *
 * @author Christian Kohlschütter
 */
@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION",
    "NM_SAME_SIMPLE_NAME_AS_SUPERCLASS"})
public final class SocketTest extends org.newsclub.net.unix.SocketTest<AFTIPCSocketAddress> {

  public SocketTest() {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Test
  public void testMain() throws Exception {
    AFTIPCSocket.main(new String[0]);
  }

  @Test
  public void testVersion() throws Exception {
    assertNotEquals("", AFTIPCSocket.getVersion());
    // see junixsocket-rmi JunixSocketVersionTest
  }

  @Test
  public void testSupported() throws Exception {
    AFTIPCSocket.isSupported();
  }

  @Test
  public void testLoadedLibrary() throws Exception {
    assertNotEquals("", AFTIPCSocket.getLoadedLibrary());
  }
}
