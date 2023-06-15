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

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class CancelAcceptTest extends
    org.newsclub.net.unix.CancelAcceptTest<AFTIPCSocketAddress> {

  public CancelAcceptTest() {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Override
  protected String checkKnownConditionDidNotThrowSocketException() {
    int[] mm = getLinuxMajorMinorVersion();

    boolean oldKernel = false;
    if (mm != null && (mm[0] < 5 || (mm[0] == 5 && mm[1] < 10 /* 5.10 */))) {
      oldKernel = true;
    }

    if (oldKernel) {
      return "Kernel may be too old for full TIPC support: " + NO_SOCKETEXCEPTION_CLOSED_SERVER;
    } else {
      return null;
    }
  }
}
