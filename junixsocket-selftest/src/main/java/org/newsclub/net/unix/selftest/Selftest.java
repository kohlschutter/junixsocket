/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
package org.newsclub.net.unix.selftest;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.platform.console.options.CommandLineOptions;
import org.junit.platform.console.options.Theme;
import org.junit.platform.console.tasks.ConsoleTestExecutor;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Performs a series of self-tests.
 * 
 * Specifically, we run all unit tests of junixsocket-core and junixsocket-rmi.
 * 
 * @author Christian Kohlschütter
 */
public class Selftest {
  private final Map<String, Object> results = new LinkedHashMap<>();
  private final PrintWriter out;
  private boolean fail = false;

  public Selftest(PrintWriter out) {
    this.out = out;
  }

  /**
   * Run this from the command line to ensure junixsocket works correctly on the target system.
   * 
   * A zero error code indicates success.
   * 
   * @param args Ignored.
   * @throws Exception on error.
   */
  public static void main(String[] args) throws Exception {
    Selftest st = new Selftest(new PrintWriter(new OutputStreamWriter(System.out, Charset
        .defaultCharset()), true));

    st.runTests("junixsocket-common", new String[] {
        "org.newsclub.net.unix.AcceptTimeoutTest", //
        "org.newsclub.net.unix.AFUNIXSocketAddressTest", //
        "org.newsclub.net.unix.AvailableTest", //
        "org.newsclub.net.unix.BufferOverflowTest", //
        "org.newsclub.net.unix.CancelAcceptTest", //
        "org.newsclub.net.unix.EndOfFileJavaTest", //
        "org.newsclub.net.unix.EndOfFileTest", //
        "org.newsclub.net.unix.FileDescriptorsTest", //
        "org.newsclub.net.unix.PeerCredentialsTest", //
        "org.newsclub.net.unix.ServerSocketCloseTest", //
        "org.newsclub.net.unix.SoTimeoutTest", //
        "org.newsclub.net.unix.TcpNoDelayTest",//
    });

    st.runTests("junixsocket-rmi", new String[] {
        "org.newsclub.net.unix.rmi.RemoteFileDescriptorTest", //
    });

    st.dumpResults();
    System.exit(st.isFail() ? 1 : 0); // NOPMD
  }

  /**
   * Checks if any test has failed so far.
   * 
   * @return {@code true} if failed.
   */
  public boolean isFail() {
    return fail;
  }

  /**
   * Dumps the results of the selftest.
   * 
   */
  public void dumpResults() {
    System.out.println();
    out.println("Selftest results:");

    for (Map.Entry<String, Object> en : results.entrySet()) {
      Object res = en.getValue();
      String result = "DONE";
      String extra = "";
      if (res == null) {
        result = "SKIP";
      } else if (res instanceof Throwable) {
        result = "FAIL";
        extra = res.toString();
      } else {
        TestExecutionSummary summary = (TestExecutionSummary) en.getValue();

        extra = summary.getTestsSucceededCount() + "/" + summary.getTestsFoundCount();
        if (summary.getTestsFailedCount() > 0) {
          result = "FAIL";
          fail = true;
        } else if (summary.getTestsFoundCount() == 0) {
          result = "NONE";
          fail = true;
        } else if (summary.getTestsStartedCount() == summary.getTestsSucceededCount()) {
          result = "PASS";
        }
      }
      out.println(result + "\t" + en.getKey() + "\t" + extra);
    }
    out.println();

    if (fail) {
      out.println("Selftest FAILED");
    } else {
      out.println("Selftest PASSED");
    }
  }

  /**
   * Runs the given test classes for the specified module.
   * 
   * @param module The module name.
   * @param classesToTest The classes to test.
   */
  public void runTests(String module, String[] classesToTest) {
    out.println("Testing \"" + module + "\"...");

    Object summary;
    if (Boolean.valueOf(System.getProperty("selftest.skip." + module, "false"))) {
      out.println("Skipping; selftest disabled");
      summary = null;
    } else {
      CommandLineOptions options = new CommandLineOptions();
      options.setAnsiColorOutputDisabled(true);
      options.setTheme(Theme.ASCII);
      options.setSelectedClasses(Arrays.asList(classesToTest));

      try {
        summary = new ConsoleTestExecutor(options).execute(out);
      } catch (Exception e) {
        e.printStackTrace(out);
        summary = e;
      }
    }
    results.put(module, summary);
  }
}
