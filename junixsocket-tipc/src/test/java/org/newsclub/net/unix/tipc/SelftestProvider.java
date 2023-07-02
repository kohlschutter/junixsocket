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

import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.SocketTestBase;

/**
 * Provides references to all "junixsocket-tipc" tests that should be included in
 * junixsocket-selftest.
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class SelftestProvider {
  final Map<String, LinkedHashSet<Class<?>>> testMap = new LinkedHashMap<>(); // NOPMD.LooseCoupling

  // CPD-OFF

  @SuppressWarnings({"PMD.ExcessiveMethodLength", "PMD.UnnecessaryFullyQualifiedName"})
  public SelftestProvider() {
    registerTest(AcceptTimeoutTest.class);

    registerTest(AFTIPCTopologyWatcherTest.class);

    registerTest(AncillaryMessageTest.class);

    registerTest(AvailableTest.class);

    registerTest(BufferOverflowTest.class);

    registerTest(CancelAcceptTest.class);

    registerTest(DatagramSocketTest.class);

    registerTest(EndOfFileTest.class);

    // disabled: FinalizeTest

    registerTest(ReadWriteTest.class);

    registerTest(SelectorTest.class);

    registerTest(ServerSocketCloseTest.class);

    registerTest(ServerSocketTest.class);

    registerTest(SocketChannelTest.class);

    registerTest(SocketOptionsTest.class);

    registerTest(SocketPairTest.class);

    registerTest(SocketTest.class);

    registerTest(SoTimeoutTest.class);

    registerTest(StandardSocketOptionsTest.class);

    registerTest(TcpNoDelayTest.class);

    registerTest(ThroughputTest.class);
  }

  public Set<String> modulesDisabledByDefault() {
    return Collections.singleton("junixsocket-common.JavaInet");
  }

  private void registerTest(Class<? extends SocketTestBase<AFTIPCSocketAddress>> testTIPC) {
    registerTest("junixsocket-tipc", testTIPC);
  }

  private void registerTest(String group, Class<?> test) {
    if (test != null) {
      testMap.computeIfAbsent(group, (key) -> new LinkedHashSet<>()).add(test);
    }
  }

  public Map<String, Class<?>[]> tests() {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    testMap.forEach((key, set) -> {
      tests.put(key, set.toArray(new Class<?>[0]));
    });

    return tests;
  }

  public void printAdditionalProperties(PrintWriter out) {
  }
}
