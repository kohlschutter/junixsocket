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
package org.newsclub.net.unix.vsock;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.opentest4j.TestAbortedException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class SelectorTest extends org.newsclub.net.unix.SelectorTest<AFVSOCKSocketAddress> {

  public SelectorTest() throws IOException {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  @Override
  @Test
  public void testNonBlockingAccept() throws IOException, InterruptedException {
    try {
      newInterconnectedSockets();
    } catch (TestAbortedException e) {
      TestAbortedWithImportantMessageException e2 = new TestAbortedWithImportantMessageException(
          MessageType.TEST_ABORTED_SHORT_INFORMATIONAL, AFVSOCKAddressSpecifics.KERNEL_TOO_OLD);
      e2.addSuppressed(e);
      throw e2; // NOPMD.PreserveStackTrace
    }
    super.testNonBlockingAccept();
  }

}
