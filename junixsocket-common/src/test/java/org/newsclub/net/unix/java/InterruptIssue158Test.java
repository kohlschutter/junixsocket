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
package org.newsclub.net.unix.java;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Test interrupt-related behavior, as discussed in
 * <a href="https://github.com/kohlschutter/junixsocket/issues/158">issue 158</a>.
 *
 * @author https://github.com/cenodis
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public class InterruptIssue158Test extends
    org.newsclub.net.unix.InterruptIssue158Test<InetSocketAddress> {

  public InterruptIssue158Test() {
    super(JavaAddressSpecifics.INSTANCE);
  }

  @Override
  protected void deleteSocketFile(InetSocketAddress sa) {

  }

  @Override
  protected InetSocketAddress newTempAddress() throws IOException {
    BindException bindEx = null;
    for (int i = 0; i < 100; i++) {
      try (ServerSocket ss = newServerSocketBindOn(super.newTempAddress())) {
        return (InetSocketAddress) ss.getLocalSocketAddress();
      } catch (BindException e) {
        bindEx = e;
      }
    }
    if (bindEx == null) {
      throw new BindException("Cannot bind");
    } else {
      throw bindEx;
    }
  }
}
