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
package org.newsclub.net.unix;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provides references to all "junixsocket-common" tests that should be included in
 * junixsocket-selftest.
 *
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class SelftestProvider {
  private static final String COMMON = "junixsocket-common";
  private static final String COMMON_JAVA_INET = "junixsocket-common.JavaInet";

  final Map<String, LinkedHashSet<Class<?>>> testMap = new LinkedHashMap<>(); // NOPMD.LooseCoupling

  // CPD-OFF

  @SuppressWarnings({"PMD.ExcessiveMethodLength", "PMD.UnnecessaryFullyQualifiedName"})
  public SelftestProvider() {
    registerTest(COMMON, org.newsclub.net.unix.AFTIPCSocketAddressTest.class);

    registerTest(COMMON, org.newsclub.net.unix.AFUNIXSocketAddressTest.class);

    registerTest(org.newsclub.net.unix.domain.AbstractNamespaceTest.class);

    registerTest(org.newsclub.net.unix.domain.AcceptTimeoutTest.class);

    // AncillaryMesageTest: currently only in junixsocket-tipc

    registerTest(org.newsclub.net.unix.domain.AvailableTest.class);

    registerTest(COMMON, BuildPropertiesTest.class);

    registerTest(org.newsclub.net.unix.domain.BufferOverflowTest.class);

    registerTest(org.newsclub.net.unix.domain.CancelAcceptTest.class);

    registerTest(org.newsclub.net.unix.domain.DatagramSocketTest.class);

    registerTest(org.newsclub.net.unix.domain.EndOfFileTest.class);

    registerTest(COMMON, org.newsclub.net.unix.FileDescriptorCastTest.class);
    registerTest(COMMON, org.newsclub.net.unix.domain.FileDescriptorCastTest.class);

    // file-descriptor passing is AF_UNIX-specific
    registerTest(org.newsclub.net.unix.domain.FileDescriptorsTest.class);

    // disabled: FinalizeTest

    registerTest(InetAddressTest.class);

    // peer credential passing is AF_UNIX specific
    registerTest(org.newsclub.net.unix.domain.PeerCredentialsTest.class);

    registerTest(COMMON, PipeTest.class);

    registerTest(org.newsclub.net.unix.domain.ReadWriteTest.class);

    registerTest(org.newsclub.net.unix.domain.SelectorTest.class);
    registerTestJavaInet(org.newsclub.net.unix.java.SelectorTest.class);

    registerTest(org.newsclub.net.unix.domain.ServerSocketCloseTest.class);
    registerTest(org.newsclub.net.unix.domain.ServerSocketTest.class);

    registerTest(org.newsclub.net.unix.domain.SocketAddressTest.class);

    registerTest(org.newsclub.net.unix.domain.SocketChannelTest.class);

    registerTest(org.newsclub.net.unix.domain.SocketFactoryTest.class);

    // SocketOptionsTest: currently only in junixsocket-tipc

    registerTest(org.newsclub.net.unix.domain.SocketPairTest.class);

    registerTest(org.newsclub.net.unix.domain.SocketTest.class);

    registerTest(org.newsclub.net.unix.domain.SoTimeoutTest.class);

    registerTest(org.newsclub.net.unix.domain.StandardSocketOptionsTest.class);

    registerTest(org.newsclub.net.unix.domain.TcpNoDelayTest.class);

    registerTest(org.newsclub.net.unix.domain.ThroughputTest.class);
    registerTestJavaInet(org.newsclub.net.unix.java.ThroughputTest.class);

    registerTest(org.newsclub.net.unix.domain.UnixDomainSocketAddressTest.class);
  }

  public Set<String> modulesDisabledByDefault() {
    return Collections.singleton(COMMON_JAVA_INET);
  }

  private void registerTest( //
      Class<? extends SocketTestBase<AFUNIXSocketAddress>> testUnixDomain) {
    registerTest(COMMON, testUnixDomain);
  }

  private void registerTestJavaInet( //
      Class<? extends SocketTestBase<InetSocketAddress>> testJava) {
    registerTest(COMMON_JAVA_INET, testJava);
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
    out.println("Native architecture: " + NativeLibraryLoader.getArchitectureAndOS());
  }

  public static void main(String[] args) {
    new SelftestProvider().printAdditionalProperties(new PrintWriter(new OutputStreamWriter(
        System.out, Charset.defaultCharset()), true));
  }
}