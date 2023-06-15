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
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

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
public final class AcceptTimeoutTest extends
    org.newsclub.net.unix.AcceptTimeoutTest<AFVSOCKSocketAddress> {

  public AcceptTimeoutTest() {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  @Override
  @Test
  public void testTimeoutAfterDelay() throws Exception {
    try {
      super.testTimeoutAfterDelay();
    } catch (SocketTimeoutException e) {
      e.printStackTrace();
      try {
        newInterconnectedSockets();
      } catch (TestAbortedException e2) {
        TestAbortedWithImportantMessageException e3 = new TestAbortedWithImportantMessageException(
            MessageType.TEST_ABORTED_SHORT_WITH_ISSUES, AFVSOCKAddressSpecifics.KERNEL_TOO_OLD, e);
        e3.addSuppressed(e2);
        throw e3; // NOPMD.PreserveStackTrace
      }
    }
  }

  @Override
  protected String checkKnownBugAcceptTimeout(SocketAddress serverAddress) {
    boolean vsockNotAvailable = false;

    Integer cid = null;
    try {
      if ((cid = AFVSOCKSocket.getLocalCID()) == AFVSOCKSocketAddress.VMADDR_CID_ANY) {
        vsockNotAvailable = true;
      }
    } catch (IOException e) {
      e.printStackTrace();
      vsockNotAvailable = true;
    }

    if (vsockNotAvailable) {
      return "Server accept timed out. " + (cid != null ? "Local CID=" + cid
          : "Local CID could not be retrieved") + ". VSOCK may not be available";
    } else if ((serverAddress instanceof AFVSOCKSocketAddress)) {
      AFVSOCKSocketAddress sa = (AFVSOCKSocketAddress) serverAddress;
      if (sa.getVSOCKCID() == AFVSOCKSocketAddress.VMADDR_CID_LOCAL) {
        // seen on Amazon EC2
        return "Server accept timed out. Requested VMADDR_CID_LOCAL with local CID=" + cid
            + ". VSOCK may not be available";
      }
    }
    return null;
  }

}
