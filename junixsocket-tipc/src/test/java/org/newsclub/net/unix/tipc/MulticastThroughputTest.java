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
package org.newsclub.net.unix.tipc;

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
public class MulticastThroughputTest extends
    org.newsclub.net.unix.ThroughputTest<AFTIPCSocketAddress> {

  public MulticastThroughputTest() {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Override
  protected String stbTestType() {
    return "AF_TIPC";
  }
}
