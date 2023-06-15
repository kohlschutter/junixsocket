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
package org.newsclub.net.unix;

import java.io.IOException;
import java.net.SocketAddress;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import com.kohlschutter.testutil.AvailabilityRequirement;

/**
 * Not supported on Java 8 yet, but you can manually verify that it works by running the following
 * commands from three different shells:
 *
 * <ol>
 * <li>(shell 1) {@code nc -l -U /tmp/testsocket}</li>
 * <li>(shell 2) {@code java -Dtest.junixsocket.socket=/tmp/testsocket
 * -cp junixsocket-selftest-jar-with-dependencies.jar org.newsclub.net.unix.FinalizeTestClient}</li>
 * <li>(shell 3) {@code lsof -U -a -p $(jps | grep FinalizeTestClient | cut -f 1 -d' ') | wc -l}
 * <li>(shell 1) type some text, followed by enter</li>
 * <li>(shell 3) {@code lsof -U -a -p $(jps | grep FinalizeTestClient | cut -f 1 -d' ') | wc -l}
 * </ol>
 *
 * The number printed in shell 3 after the second {@code lsof} commands should be one less than the
 * one of the first {@code lsof} command.
 *
 * See <a href="https://github.com/kohlschutter/junixsocket/pull/29">issue 29</a> for details.
 */
public abstract class FinalizeTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected FinalizeTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  @AvailabilityRequirement(classes = {"java.lang.ProcessHandle"}, //
      message = "This test requires Java 9 or later")
  public void testLeak() throws Exception {
    throw new TestAbortedException();
  }

  protected abstract String socketType();

  protected Object preRunCheck(Process process) throws TestAbortedException, IOException,
      InterruptedException {
    return null;
  }

  protected void postRunCheck(Process process, Object linesBeforeObj) throws TestAbortedException,
      IOException, InterruptedException {
  }
}
