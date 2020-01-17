/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.junit.platform.console.options.CommandLineOptions;
import org.junit.platform.console.options.Theme;
import org.junit.platform.console.tasks.ConsoleTestExecutor;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketCapability;

/**
 * Performs a series of self-tests.
 * 
 * Specifically, we run all unit tests of junixsocket-core and junixsocket-rmi.
 * 
 * NOTE: The Selftest will fail when run from within Eclipse due to test classes not being present.
 * Invoke via <code>java -jar junixsocket-selftest-...-jar-with-dependencies.jar</code>.
 * 
 * @author Christian Kohlschütter
 */
public class Selftest {
  private static final Class<? extends Annotation> CAP_ANNOTATION_CLASS =
      getAFUNIXSocketCapabilityRequirementClass();

  private final Map<String, Object> results = new LinkedHashMap<>();
  private final PrintWriter out;
  private final List<AFUNIXSocketCapability> supportedCapabilites = new ArrayList<>();
  private final List<AFUNIXSocketCapability> unsupportedCapabilites = new ArrayList<>();
  private boolean withIssues = false;
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

    st.printExplanation();
    st.dumpSystemProperties();
    st.checkSupported();
    st.checkCapabilities();

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

  public void printExplanation() throws IOException {
    out.println("junixsocket selftest");
    out.println();
    out.println(
        "This program determines whether junixsocket is supported on the current platform.");
    out.println("The final line should say whether the selftest passed or failed.");
    out.println(
        "If the selftest failed, please visit https://github.com/kohlschutter/junixsocket/issues");
    out.println("and file a new bug report with the output below.");
    out.println();
    out.println("selftest version " + AFUNIXSocket.getVersion());
    out.println();
  }

  public void dumpSystemProperties() {
    out.println("System properties:");
    out.println();
    for (Object key : new TreeSet<>(System.getProperties().keySet())) {
      String value = System.getProperty(key.toString());
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        switch (c) {
          case '\n':
            sb.append("\\n");
            break;
          case '\r':
            sb.append("\\r");
            break;
          case '\t':
            sb.append("\\r");
            break;
          default:
            if (c < 32 || c >= 127) {
              sb.append(String.format(Locale.ENGLISH, "\\u%04x", (int) c));
            }
            sb.append(c);
            break;
        }
      }
      out.println(key + ": " + sb.toString());
    }
    out.println();
  }

  public void checkSupported() {
    out.print("AFUNIXSocket.isSupported: ");
    out.flush();

    boolean isSupported = AFUNIXSocket.isSupported();
    out.println(isSupported);
    out.println();
    out.flush();

    if (!isSupported) {
      out.println("FAIL: junixsocket is not supported on this platform");
      out.println();
      fail = true;
    }
  }

  public void checkCapabilities() {
    for (AFUNIXSocketCapability cap : AFUNIXSocketCapability.values()) {
      boolean supported = AFUNIXSocket.supports(cap);
      (supported ? supportedCapabilites : unsupportedCapabilites).add(cap);
    }
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
    out.println();
    out.println("Selftest results:");

    for (Map.Entry<String, Object> en : results.entrySet()) {
      Object res = en.getValue();
      String result = "DONE";
      String extra = "";
      if (res == null) {
        result = "SKIP";
        extra = "(skipped by user request)";
      } else if (res instanceof Throwable) {
        result = "FAIL";
        extra = res.toString();
        fail = true;
      } else {
        TestExecutionSummary summary = (TestExecutionSummary) en.getValue();

        extra = summary.getTestsSucceededCount() + "/" + summary.getTestsFoundCount();
        long nSkipped = summary.getTestsSkippedCount();
        if (nSkipped > 0) {
          extra += " (" + nSkipped + " skipped)";
        }

        if (summary.getTestsFailedCount() > 0) {
          result = "FAIL";
          fail = true;
        } else if (summary.getTestsFoundCount() == 0) {
          result = "NONE";
          fail = true;
        } else if (summary.getTestsSucceededCount() == summary.getTestsFoundCount()) {
          result = "PASS";
        } else if (summary.getTestsSkippedCount() > 0) {
          withIssues = true;
        }
      }
      out.println(result + "\t" + en.getKey() + "\t" + extra);
    }
    out.println();

    out.println("Supported capabilities:   " + supportedCapabilites);
    out.println("Unsupported capabilities: " + unsupportedCapabilites);
    out.println();

    if (fail) {
      out.println("Selftest FAILED");
    } else if (withIssues) {
      out.println("Selftest PASSED WITH ISSUES");
    } else {
      out.println("Selftest PASSED");
    }
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends Annotation> getAFUNIXSocketCapabilityRequirementClass() {
    try {
      return (Class<? extends Annotation>) Class.forName(
          "org.newsclub.net.unix.AFUNIXSocketCapabilityRequirement");
    } catch (ClassNotFoundException e1) {
      return null;
    }
  }

  private boolean checkIfCapabilitiesSupported(String className) {
    if (CAP_ANNOTATION_CLASS != null) {
      try {
        Class<?> klass = Class.forName(className);
        Annotation annotation = klass.getAnnotation(CAP_ANNOTATION_CLASS);
        if (annotation != null) {
          try {
            AFUNIXSocketCapability[] caps = (AFUNIXSocketCapability[]) annotation.getClass()
                .getMethod("value").invoke(annotation);
            if (caps != null) {
              for (AFUNIXSocketCapability cap : caps) {
                if (!AFUNIXSocket.supports(cap)) {
                  out.println("Skipping class " + className + "; unsupported capability: " + cap);
                  return false;
                }
              }
            }
          } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
              | NoSuchMethodException | SecurityException e) {
            // ignore
          }
        }
      } catch (ClassNotFoundException e) {
        out.println("Class not found: " + className);
        withIssues = true;
      }
    }

    return true;
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
      out.println("Skipping module " + module + "; skipped by request");
      withIssues = true;
      summary = null;
    } else {
      CommandLineOptions options = new CommandLineOptions();
      options.setAnsiColorOutputDisabled(true);
      options.setTheme(Theme.ASCII);

      List<String> list = new ArrayList<>(classesToTest.length);
      for (String className : classesToTest) {
        if (classesToTest == null || classesToTest.length == 0) {
          // ignore
          continue;
        }
        if (Boolean.valueOf(System.getProperty("selftest.skip." + className, "false"))) {
          out.println("Skipping test class " + className + "; skipped by request");
          withIssues = true;
        } else if (checkIfCapabilitiesSupported(className)) {
          list.add(className);
        }
      }

      options.setSelectedClasses(list);

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
