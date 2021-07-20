/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides references to all "junixsocket-common" tests that should be included in
 * junixsocket-selftest.
 * 
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class SelftestProvider {
  public Map<String, Class<?>[]> tests() {
    Map<String, Class<?>[]> tests = new LinkedHashMap<>();
    tests.put("junixsocket-common", new Class<?>[] {
        AcceptTimeoutTest.class, //
        AFUNIXDatagramSocketTest.class, //
        AFUNIXInetAddressTest.class, //
        AFUNIXPipeTest.class, //
        AFUNIXSelectorTest.class, //
        AFUNIXServerSocketTest.class, //
        AFUNIXSocketAddressTest.class, //
        AFUNIXSocketChannelTest.class, //
        AFUNIXSocketFactoryTest.class, //
        AFUNIXSocketPairTest.class, //
        AFUNIXSocketTest.class, //
        AvailableTest.class, //
        BufferOverflowTest.class, //
        CancelAcceptTest.class, //
        EndOfFileJavaTest.class, //
        EndOfFileTest.class, //
        // FinalizeTest.class, //
        FileDescriptorCastTest.class, //
        FileDescriptorsTest.class, //
        PeerCredentialsTest.class, //
        ReadWriteTest.class, //
        ServerSocketCloseTest.class, //
        SoTimeoutTest.class, //
        StandardSocketOptionsTest.class, //
        TcpNoDelayTest.class, //
        ThroughputTest.class, //
    });

    return tests;
  }
}