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
package org.newsclub.net.unix.vsock;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFVSOCKSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_VSOCK)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class CancelAcceptTest extends
    org.newsclub.net.unix.CancelAcceptTest<AFVSOCKSocketAddress> {

  public CancelAcceptTest() {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  @Test
  @Override
  public void issue6test1() throws Exception {
    try {
      super.issue6test1();
    } catch (SocketTimeoutException e) {
      throw new TestAbortedWithImportantMessageException(
          MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
          "Environment may not be configured for VSOCK. More information at https://kohlschutter.github.io/junixsocket/junixsocket-vsock/",
          e);
    }
  }
}
