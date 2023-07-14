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

import java.io.File;
import java.net.UnixDomainSocketAddress;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.SocketTestBase;

import com.kohlschutter.testutil.AvailabilityRequirement;

public class UnixDomainSocketAddressTest extends SocketTestBase<AFUNIXSocketAddress> {
  public UnixDomainSocketAddressTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  @AvailabilityRequirement(classes = "java.net.UnixDomainSocketAddress", //
      message = "This test requires Java 16 or later")
  public void testConvertUnixDomainSocketAddress() throws Exception {
    File f = new File("/tmp/hello");
    UnixDomainSocketAddress addr = UnixDomainSocketAddress.of(f.getPath());
    AFUNIXSocketAddress usa = AFUNIXSocketAddress.of(addr);

    assertEquals(f, usa.getFile());
  }
}
