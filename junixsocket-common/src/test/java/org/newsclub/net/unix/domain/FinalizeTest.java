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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

  @SuppressFBWarnings({"RV_DONT_JUST_NULL_CHECK_READLINE"})
  private static List<String> lsofUnixSockets(long pid) throws IOException, TestAbortedException,
      InterruptedException {
    assertTrue(pid > 0);

    List<String> lines = new ArrayList<>();

    Process p;
    try {
      p = Runtime.getRuntime().exec(new String[] {"lsof", "-U", "-a", "-p", String.valueOf(pid)});
    } catch (Exception e) {
      assumeTrue(false, e.getMessage());
      return Collections.emptyList();
    }
    try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset
        .defaultCharset()))) {
      String l;
      while ((l = in.readLine()) != null) {
        lines.add(l);
        if (l.contains("busybox")) {
          assumeTrue(false, "incompatible lsof binary");
        }
      }
    }
    assumeTrue(p.waitFor() == 0, "lsof should terminate with RC=0");
    return lines;
  }

  @Override
  protected Object preRunCheck(Process process) throws TestAbortedException, IOException,
      InterruptedException {
    List<String> linesBefore = lsofUnixSockets(process.pid());
    // If that's not true, we need to skip the test
    assumeTrue(!linesBefore.isEmpty());
    return linesBefore;
  }

  @Override
  protected void postRunCheck(Process process, Object linesBeforeObj) throws TestAbortedException,
      IOException, InterruptedException {
    assertNotNull(linesBeforeObj);
    @SuppressWarnings("unchecked")
    List<String> linesBefore = (List<String>) linesBeforeObj;
    try {
      List<String> linesAfter = null;
      for (int i = 0; i < 10; i++) {
        Thread.sleep(100);
        linesAfter = lsofUnixSockets(process.pid());
        if (linesAfter.size() < linesBefore.size()) {
          break;
        }
        if (!process.isAlive()) {
          break;
        }
      }

      assumeTrue(Objects.requireNonNull(linesAfter).size() > 0, "lsof may fail to return anything");

      if (linesAfter.size() >= linesBefore.size()) {
        System.err.println("lsof: Unexpected output");
        System.err.println("lsof: Output before: " + linesBefore);
        System.err.println("lsof: Output after: " + linesAfter);
      }
      assertTrue(linesAfter.size() < linesBefore.size(),
          "Our unix socket file handle should have been cleared out");
    } finally {
      process.destroy();
      process.waitFor();
    }
  }
}
