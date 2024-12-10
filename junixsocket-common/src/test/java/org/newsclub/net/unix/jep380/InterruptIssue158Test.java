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

import java.io.FileNotFoundException;
import java.net.SocketAddress;
import java.net.SocketException;

import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.TestUtil;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.AvailabilityRequirement;

/**
 * Test interrupt-related behavior, as discussed in
 * <a href="https://github.com/kohlschutter/junixsocket/issues/158">issue 158</a>.
 *
 * @author https://github.com/cenodis
 * @author Christian Kohlschütter
 */
@AvailabilityRequirement(classes = "java.net.UnixDomainSocketAddress", //
    message = "This test requires Java 16 or later")
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public class InterruptIssue158Test extends
    org.newsclub.net.unix.InterruptIssue158Test<SocketAddress> {

  public InterruptIssue158Test() {
    super(JEP380AddressSpecifics.INSTANCE);
  }

  @Override
  protected void deleteSocketFile(SocketAddress sa) {
    try {
      AFUNIXSocketAddress.unwrap(sa).getFile().delete();
    } catch (FileNotFoundException ignore) {
      // ignore
    } catch (SocketException e) {
      TestUtil.printStackTrace(e);
    }
  }
}