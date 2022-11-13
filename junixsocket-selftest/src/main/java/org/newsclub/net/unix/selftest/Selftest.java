/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.jupiter.engine.discovery.DiscoverySelectorResolver;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFUNIXSocket;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;
import com.kohlschutter.util.ConsolePrintStream;
import com.kohlschutter.util.SystemPropertyUtil;

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
@SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.CognitiveComplexity"})
public class Selftest {
  private final ConsolePrintStream out = ConsolePrintStream.wrapSystemOut();
  private final Map<String, ModuleResult> results = new LinkedHashMap<>();
  private final List<AFSocketCapability> supportedCapabilites = new ArrayList<>();
  private final List<AFSocketCapability> unsupportedCapabilites = new ArrayList<>();
  private boolean withIssues = false;
  private boolean fail = false;
  private boolean modified = false;
  private boolean isSupportedAFUNIX = false;
  private final Set<String> important = new LinkedHashSet<>();
  private boolean inconclusive = false;

  private enum Result {
    SKIP, PASS, DONE, NONE, FAIL
  }

  private enum SkipMode {
    UNDECLARED(false), KEEP(false), SKIP(true), SKIP_FORCE(true);

    boolean skip;

    SkipMode(boolean skip) {
      this.skip = skip;
    }

    boolean isSkip() {
      return skip;
    }

    boolean isDeclared() {
      return this != UNDECLARED;
    }

    boolean isForce() {
      return this == SKIP_FORCE;
    }

    public static final SkipMode parse(String skipMode) {
      if (skipMode == null || skipMode.isEmpty()) {
        return SkipMode.UNDECLARED;
      } else if ("force".equalsIgnoreCase(skipMode)) {
        return SkipMode.SKIP_FORCE;
      } else {
        return Boolean.valueOf(skipMode) ? SkipMode.SKIP : SkipMode.KEEP;
      }
    }

  }

  /**
   * maven-shade-plugin's minimizeJar isn't perfect, so we give it a little hint by adding static
   * references to classes that are otherwise only found via reflection.
   *
   * @author Christian Kohlschütter
   */
  @SuppressFBWarnings("UUF_UNUSED_FIELD")
  static final class MinimizeJarDependencies {
    JupiterTestEngine jte;
    HierarchicalTestEngine<?> hte;
    EngineDescriptor ed;
    DiscoverySelectorResolver dsr;
    org.newsclub.lib.junixsocket.common.NarMetadata nmCommon;
    org.newsclub.lib.junixsocket.custom.NarMetadata nmCustom;
  }

  public Selftest() {
  }

  public void checkVM() {
    boolean isSubstrateVM = "Substrate VM".equals(System.getProperty("java.vm.name"));

    if (isSubstrateVM) {
      important.add("Substrate VM detected: Support for native-images is work in progress");

      if (!getSkipModeForModule("junixsocket-rmi").isDeclared()) {
        important.add("Auto-skipping junixsocket-rmi tests due to Substrate VM");
        System.setProperty("selftest.skip.junixsocket-rmi", "force");
        withIssues = true;
      }

      if (!getSkipModeForClass("org.newsclub.net.unix.FileDescriptorCastTest").isDeclared()) {
        important.add("Auto-skipping FileDescriptorCastTest tests due to Substrate VM");
        System.setProperty("selftest.skip.FileDescriptorCastTest", "force");
        withIssues = true;
      }
    }
  }

  /**
   * Run this from the command line to ensure junixsocket works correctly on the target system.
   *
   * A zero error code indicates success.
   *
   * @param args Ignored.
   * @throws IOException on error.
   */
  @SuppressFBWarnings({
      "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
  public static void main(String[] args) throws Exception {
    Selftest st = new Selftest();

    st.checkVM();
    st.printExplanation();
    st.dumpSystemProperties();
    st.dumpOSReleaseFiles();
    st.checkSupported();
    st.checkCapabilities();

    SelftestProvider sp = new SelftestProvider();
    Set<String> disabledModules = sp.modulesDisabledByDefault();

    List<String> messagesAtEnd = new ArrayList<>();
    for (Entry<String, Class<?>[]> en : sp.tests().entrySet()) {
      String module = en.getKey();
      if (disabledModules.contains(module)) {
        if (SystemPropertyUtil.getBooleanSystemProperty("selftest.enable-module." + module,
            false)) {
          // System.out.println("Enabled optional module: " + module);
          st.modified = true;
        } else {
          messagesAtEnd.add("Skipping optional module: " + module
              + "; enable by launching with -Dselftest.enable-module." + module + "=true");
          continue;
        }
      } else if (SystemPropertyUtil.getBooleanSystemProperty("selftest.disable-module." + module,
          false)) {
        messagesAtEnd.add("Skipping required module: " + module + "; this taints the test");
        st.withIssues = true;
      }
      st.runTests(module, en.getValue());
    }

    if (!messagesAtEnd.isEmpty()) {
      for (String m : messagesAtEnd) {
        System.out.println(m);
      }
    }

    st.checkInitError();
    st.dumpResults();

    int rc = st.isFail() ? 1 : 0;

    if (SystemPropertyUtil.getBooleanSystemProperty("selftest.wait.at-end", false)) {
      System.gc(); // NOPMD
      System.out.print("Press any key to end test. ");
      System.out.flush();
      System.in.read();
      System.out.println("RC=" + rc);
    }

    System.out.flush();
    System.exit(rc); // NOPMD
  }

  public void printExplanation() throws IOException {
    out.println(
        "This program determines whether junixsocket is supported on the current platform.");
    out.println("The final line should say whether the selftest passed or failed.");
    out.println();
    out.println(
        "If the selftest failed, please visit https://github.com/kohlschutter/junixsocket/issues");
    out.println("and file a new bug report with the output below.");
    out.println();
    out.println("junixsocket selftest version " + AFUNIXSocket.getVersion());
    try (InputStream in = getClass().getResourceAsStream(
        "/META-INF/maven/com.kohlschutter.junixsocket/junixsocket-selftest/git.properties")) {
      Properties props = new Properties();
      if (in != null) {
        props.load(in);
        out.println();
        out.println("Git properties:");
        out.println();
        for (String key : new TreeSet<>(props.stringPropertyNames())) {
          out.println(key + ": " + props.getProperty(key));
        }
      }
    }
    out.println();
  }

  public void dumpSystemProperties() {
    out.println("System properties:");
    out.println();
    for (Map.Entry<Object, Object> en : new TreeMap<>(System.getProperties()).entrySet()) {
      String key = String.valueOf(en.getKey());
      String value = String.valueOf(en.getValue());
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
    out.print("AFSocket.isSupported: ");
    out.flush();

    boolean isSupported = AFSocket.isSupported();
    out.println(isSupported);
    out.println();
    out.flush();

    if (!isSupported) {
      out.println("FAIL: junixsocket is not supported on this platform");
      out.println();
      fail = true;
    }

    out.print("AFUNIXSocket.isSupported: ");
    out.flush();

    isSupportedAFUNIX = AFUNIXSocket.isSupported();
    out.println(isSupportedAFUNIX);
    out.println();
    out.flush();

    if (!isSupportedAFUNIX) {
      out.println("WARNING: AF_UNIX sockets are not supported on this platform");
      out.println();
      withIssues = true;
    }
  }

  public void checkCapabilities() {
    for (AFSocketCapability cap : AFSocketCapability.values()) {
      boolean supported = AFSocket.supports(cap);
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

  private void checkInitError() {
    Throwable t = retrieveInitError();
    if (t == null) {
      return;
    }

    important.add("The native library failed to load.");

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    String ts = sw.toString();
    String tsLower = ts.toLowerCase(Locale.ENGLISH);

    if (tsLower.contains("not permitted") || ts.contains("permission")) {
      important.add("It looks like there were some permission errors.");
    }

    if (tsLower.contains("failed to map segment")) {
      important.add("Your temporary directory is probably mounted with \"noexec\", "
          + "which prevents the native library from loading.");
      important.add("see: https://github.com/kohlschutter/junixsocket/issues/99");
      Object tmpDir = retrieveTempDir();
      if (tmpDir == null) {
        tmpDir = System.getProperty("java.io.tmpdir");
      }
      if (tmpDir != null) {
        important.add("Temp dir: " + tmpDir);
      }
      important.add(
          "You can specify a different directory using -Dorg.newsclub.net.unix.library.tmpdir=");
    }
  }

  /**
   * Dumps the results of the selftest.
   *
   */
  public void dumpResults() { // NOPMD
    if (modified) {
      important.add("Selftest was modified, for example to exclude/include certain tests.");
      inconclusive = true;
    }
    if (!isSupportedAFUNIX) {
      important.add(
          "Environment does not support UNIX sockets, which is an important part of junixsocket.");
      // inconclusive = true;
    }
    if (inconclusive) {
      important.add("Selftest results may be inconclusive.");
    }

    if (withIssues) {
      important.add("\"With issues\": "
          + "Please carefully check the output above; the software may not be able to do what you want.");
    }

    out.println();
    out.println("Selftest results:");

    for (Map.Entry<String, ModuleResult> en : results.entrySet()) {
      ModuleResult res = en.getValue();

      String result = res == null ? null : res.result.name();
      String extra;
      if (res == null || (res.result == Result.SKIP && res.throwable == null)) {
        result = "SKIP";
        extra = "(skipped by user request)";
      } else if (res.summary == null) {
        extra = res.throwable == null ? "(unknown error)" : res.throwable.toString();
        fail = true;
      } else {
        TestExecutionSummary summary = res.summary;

        long nSucceeded = (summary.getTestsSucceededCount() + res.getNumAbortedNonIssues());
        extra = nSucceeded + "/" + summary.getTestsFoundCount();
        long nSkipped = summary.getTestsSkippedCount();
        if (nSkipped > 0) {
          extra += " (" + nSkipped + " skipped)";
        }
      }

      out.println(result + "\t" + en.getKey() + "\t" + extra);
    }
    out.println();

    if (!important.isEmpty()) {
      for (String l : important) {
        out.println("IMPORTANT: " + l);
      }
      out.println();
    }

    out.println("Supported capabilities:   " + supportedCapabilites);
    out.println("Unsupported capabilities: " + unsupportedCapabilites);
    out.println();

    if (fail || withIssues) {
      if (inconclusive || modified) {
        out.println("Selftest INCONCLUSIVE");
      } else if (fail) {
        out.println("Selftest FAILED");
      } else if (withIssues) {
        out.println("Selftest PASSED WITH ISSUES");
      }
    } else {
      out.println("Selftest PASSED");
    }
  }

  private SkipMode getSkipModeForModule(String moduleName) {
    return SkipMode.parse(System.getProperty("selftest.skip." + moduleName));
  }

  private SkipMode getSkipModeForClass(String className) {
    SkipMode skipMode = SkipMode.parse(System.getProperty("selftest.skip." + className));
    if (skipMode.isDeclared()) {
      return skipMode;
    }
    int i = className.lastIndexOf('.');
    if (i < 0) {
      return SkipMode.UNDECLARED;
    }

    className = className.substring(i + 1);
    return SkipMode.parse(System.getProperty("selftest.skip." + className));
  }

  /**
   * Runs the given test classes for the specified module.
   *
   * @param module The module name.
   * @param testClasses The test classes.
   */
  @SuppressWarnings({"PMD.ExcessiveMethodLength", "PMD.NcssCount", "PMD.NPathComplexity"})
  public void runTests(String module, Class<?>[] testClasses) {
    String prefix = "Testing \"" + module + "\"... ";
    out.markPosition();
    out.update(prefix);
    out.flush();

    String only = System.getProperty("selftest.only", "");
    if (!only.isEmpty()) {
      modified = true;
    }

    boolean skipped = false;

    final ModuleResult moduleResult;

    SkipMode skipMode;

    if ((skipMode = getSkipModeForModule(module)).isSkip()) {
      out.println("Skipping module " + module + "; skipped by request" + (skipMode.isForce()
          ? " (force)" : ""));
      if (!skipMode.isForce()) {
        withIssues = true;
        modified = true;
      }
      moduleResult = new ModuleResult(Result.SKIP, null, null);
    } else {
      List<Class<?>> list = new ArrayList<>(testClasses.length);
      for (Class<?> testClass : testClasses) {
        if (testClass == null) {
          // ignore
          continue;
        }
        String className = testClass.getName();
        String simpleName = testClass.getSimpleName();

        if (!only.isEmpty() && !only.equals(className) && !only.equals(simpleName)) {
          continue;
        }

        if ((skipMode = getSkipModeForClass(className)).isSkip()) {
          out.println("Skipping test class " + className + "; skipped by request" + (skipMode
              .isForce() ? " (force)" : ""));
          if (!skipMode.isForce()) {
            modified = true;
            withIssues = true;
          }
        } else {
          list.add(testClass);
        }
      }

      TestExecutionSummary summary = null;
      Exception exception = null;
      long numAbortedNonIssues = 0;
      try {
        SelftestExecutor ex = new SelftestExecutor(list, prefix);
        summary = ex.execute(out);

        for (Map.Entry<TestIdentifier, TestExecutionResult> en : ex.getTestsWithWarnings()
            .entrySet()) {
          TestIdentifier tid = en.getKey();
          TestExecutionResult res = en.getValue();
          Optional<Throwable> t = res.getThrowable();
          if (t.isPresent() && t.get() instanceof TestAbortedWithImportantMessageException) {
            String key = module + ": " + ex.getTestIdentifier(tid.getParentId().get())
                .getDisplayName() + "." + tid.getDisplayName();
            TestAbortedWithImportantMessageException ime =
                (TestAbortedWithImportantMessageException) t.get();

            MessageType messageType = ime.messageType();
            if (messageType.isIncludeTestInfo()) {
              important.add(ime.getMessage() + "; " + key);
            } else {
              important.add(ime.getMessage());
            }
            if (!messageType.isWithIssues()) {
              numAbortedNonIssues++;
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace(out);
        exception = e;
      }

      if (skipped) {
        moduleResult = new ModuleResult(Result.SKIP, null, exception);
      } else if (exception != null || summary == null) {
        moduleResult = new ModuleResult(Result.FAIL, null, exception);
        fail = true;
      } else {
        final Result result;
        if (summary.getTestsFailedCount() > 0) {
          result = Result.FAIL;
          fail = true;
        } else if (summary.getTestsFoundCount() == 0) {
          result = Result.NONE;
        } else if ((summary.getTestsSucceededCount() + summary.getTestsSkippedCount()
            + numAbortedNonIssues) == summary.getTestsFoundCount()) {
          result = Result.PASS;
        } else if (summary.getTestsAbortedCount() > 0) {
          result = Result.DONE;
          withIssues = true;
        } else {
          result = Result.DONE;
        }

        moduleResult = new ModuleResult(result, summary, null);
        moduleResult.numAbortedNonIssues = numAbortedNonIssues;
      }
    }
    results.put(module, moduleResult);
  }

  private void dumpContentsOfSystemConfigFile(File file) {
    if (!file.exists()) {
      return;
    }
    String p = file.getAbsolutePath();
    System.out.println("BEGIN contents of file: " + p);

    final int maxToRead = 4096;
    char[] buf = new char[4096];
    int numRead = 0;
    try (InputStreamReader isr = new InputStreamReader(new FileInputStream(file),
        StandardCharsets.UTF_8);) {

      OutputStreamWriter outWriter = new OutputStreamWriter(System.out, Charset.defaultCharset());
      int read = -1;
      boolean lastWasNewline = false;
      while (numRead < maxToRead && (read = isr.read(buf)) != -1) {
        numRead += read;
        outWriter.write(buf, 0, read);
        outWriter.flush();
        lastWasNewline = (read > 0 && buf[read - 1] == '\n');
      }
      if (!lastWasNewline) {
        System.out.println();
      }
      if (read != -1) {
        System.out.println("[...]");
      }
    } catch (Exception e) {
      System.out.println("ERROR while reading contents of file: " + p + ": " + e);
    }
    System.out.println("=END= contents of file: " + p);
    System.out.println();
  }

  public void dumpOSReleaseFiles() throws IOException {
    Set<Path> canonicalPaths = new HashSet<>();
    for (String f : new String[] {
        "/etc/os-release", "/etc/lsb-release", "/etc/lsb_release", "/etc/system-release",
        "/etc/system-release-cpe",
        //
        "/etc/debian_version", "/etc/fedora-release", "/etc/redhat-release", "/etc/centos-release",
        "/etc/centos-release-upstream", "/etc/SuSE-release", "/etc/arch-release",
        "/etc/gentoo-release", "/etc/ubuntu-release",}) {

      File file = new File(f);
      if (!file.exists() || file.isDirectory()) {
        continue;
      }
      Path p = file.toPath().toAbsolutePath();
      for (int i = 0; i < 2; i++) {
        if (Files.isSymbolicLink(p)) {
          Path p2 = Files.readSymbolicLink(p);
          if (!p2.isAbsolute()) {
            p = new File(p.toFile().getParentFile(), p2.toString()).toPath().toAbsolutePath();
          }
        }
      }

      if (!canonicalPaths.add(p)) {
        continue;
      }

      dumpContentsOfSystemConfigFile(file);
    }
  }

  private static Throwable retrieveInitError() {
    try {
      Class<?> clazz = Class.forName("org.newsclub.net.unix.SelftestDiagnosticsHelper");
      return (Throwable) clazz.getMethod("initError").invoke(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static File retrieveTempDir() {
    try {
      Class<?> clazz = Class.forName("org.newsclub.net.unix.SelftestDiagnosticsHelper");
      return (File) clazz.getMethod("tempDir").invoke(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static final class ModuleResult {
    private final Result result;
    private final TestExecutionSummary summary;
    private final Throwable throwable;
    private long numAbortedNonIssues = 0;

    ModuleResult(Result result, TestExecutionSummary summary, Throwable t) {
      Objects.requireNonNull(result);
      this.result = result;
      this.summary = summary;
      this.throwable = t;
    }

    long getNumAbortedNonIssues() {
      return numAbortedNonIssues;
    }
  }
}
