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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.opentest4j.TestAbortedException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public final class FinalizeTest extends org.newsclub.net.unix.FinalizeTest<AFUNIXSocketAddress> {

  public FinalizeTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Override
  protected String socketType() {
    return "UNIX";
  }

  @Override
  protected Object preRunCheck(Process process) throws TestAbortedException, IOException,
      InterruptedException {
    // not supported in Java 8
    return null;
  }

  @Override
  protected void postRunCheck(Process process, Object linesBeforeObj) throws TestAbortedException,
      IOException, InterruptedException {
    // not supported in Java 8
  }
}
