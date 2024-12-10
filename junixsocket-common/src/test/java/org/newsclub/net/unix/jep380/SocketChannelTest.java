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
package org.newsclub.net.unix.jep380;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.AvailabilityRequirement;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@AvailabilityRequirement(classes = "java.net.UnixDomainSocketAddress", //
    message = "This test requires Java 16 or later")
@SuppressFBWarnings({"NM_SAME_SIMPLE_NAME_AS_SUPERCLASS", "PATH_TRAVERSAL_IN"})
public final class SocketChannelTest extends
    org.newsclub.net.unix.SocketChannelTest<SocketAddress> {

  public SocketChannelTest() {
    super(JEP380AddressSpecifics.INSTANCE);
  }

  @Override
  protected boolean mayTestBindNullThrowUnsupportedOperationException() {
    return false;
  }

  @Override
  protected boolean mayTestBindNullHaveNullLocalSocketAddress() {
    return false;
  }

  @Override
  protected void cleanupTestBindNull(ServerSocketChannel sc, SocketAddress addr)
      throws ClassNotFoundException, IOException {
    if (!Class.forName("java.net.UnixDomainSocketAddress").isAssignableFrom(addr.getClass())) {
      return;
    }

    // JEP380 doesn't clean up socket files
    Path p = Paths.get(addr.toString());
    Files.delete(p);
  }
}
