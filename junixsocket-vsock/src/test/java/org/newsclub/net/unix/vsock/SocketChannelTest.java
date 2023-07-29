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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.newsclub.net.unix.InvalidSocketException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class SocketChannelTest extends
    org.newsclub.net.unix.SocketChannelTest<AFVSOCKSocketAddress> {

  public SocketChannelTest() {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  /**
   * Subclasses may override this to tell that there is a known issue with "accept".
   *
   * @param e The exception
   * @return An explanation iff this should not cause a test failure but trigger "With issues".
   */
  @Override
  protected String checkKnownBugAcceptFailure(SocketException e) {
    if (e instanceof InvalidSocketException) {
      return "Server accept failed. VSOCK may not be available";
    } else {
      return null;
    }
  }

  /**
   * Subclasses may override this to tell that there is a known issue with "accept".
   *
   * @param e The exception
   * @return An explanation iff this should not cause a test failure but trigger "With issues".
   */
  @Override
  protected String checkKnownBugAcceptFailure(SocketTimeoutException e) {
    return "Server accept failed. VSOCK may not be available";
  }

  @Override
  protected String checkKnownBugFirstAcceptCallNotTerminated() {
    // seen in virtualized Linux environments with CID=3
    return AFVSOCKAddressSpecifics.KERNEL_NOT_CONFIGURED;
  }

  @Override
  protected void handleBind(ServerSocketChannel ssc, SocketAddress sa) throws IOException {
    try {
      super.handleBind(ssc, sa);
    } catch (InvalidSocketException e) {
      String msg = "Could not bind AF_VSOCK server socket to CID=" + ((AFVSOCKSocketAddress) sa)
          .getVSOCKCID() + "; check kernel capabilities.";
      throw (TestAbortedWithImportantMessageException) new TestAbortedWithImportantMessageException(
          MessageType.TEST_ABORTED_SHORT_WITH_ISSUES, msg, summaryImportantMessage(msg)).initCause(
              e);
    }
  }

  @Override
  protected boolean handleConnect(SocketChannel sc, SocketAddress sa) throws IOException {
    try {
      return super.handleConnect(sc, sa);
    } catch (InvalidSocketException e) {
      String msg = "Could not connect AF_VSOCK socket to CID=" + ((AFVSOCKSocketAddress) sa)
          .getVSOCKCID() + "; check kernel capabilities.";
      throw (TestAbortedWithImportantMessageException) new TestAbortedWithImportantMessageException(
          MessageType.TEST_ABORTED_SHORT_WITH_ISSUES, msg, summaryImportantMessage(msg)).initCause(
              e);
    }
  }
}
