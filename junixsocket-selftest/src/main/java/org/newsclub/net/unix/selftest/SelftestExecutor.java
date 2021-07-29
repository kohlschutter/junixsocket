/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.platform.console.options.Theme;
import org.junit.platform.console.tasks.JuxPackageAccess;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import com.kohlschutter.util.ConsolePrintStream;

class SelftestExecutor {
  private final List<Class<?>> testClasses;
  private final String prefix;

  SelftestExecutor(final List<Class<?>> testClasses, String prefix) {
    this.testClasses = testClasses;
    this.prefix = prefix;
  }

  TestExecutionSummary execute(ConsolePrintStream out0) throws Exception {
    PrintWriter out = new PrintWriter(new OutputStreamWriter(out0, Charset.defaultCharset()));
    try {
      Launcher launcher = LauncherFactory.create();
      SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
      launcher.registerTestExecutionListeners(summaryListener, JuxPackageAccess
          .newTreePrintingListener(out, true, Theme.ASCII));

      LauncherDiscoveryRequestBuilder requestBuilder = request();

      // set a hard limit for test executions
      // https://junit.org/junit5/docs/5.7.1/api/org.junit.jupiter.api/org/junit/jupiter/api/Timeout.html
      requestBuilder.configurationParameter(
          "junit.jupiter.execution.timeout.testable.method.default", System.getProperty(
              "selftest.test-timeout", "10 s"));

      requestBuilder.selectors(testClasses.stream().map(DiscoverySelectors::selectClass).collect(
          Collectors.toList()));
      LauncherDiscoveryRequest discoveryRequest = requestBuilder.build();

      TestPlan testPlan = launcher.discover(discoveryRequest);

      launcher.execute(testPlan, new TestExecutionListener() {
        final Map<String, TestIdentifier> tids = new HashMap<>();

        @Override
        public void executionStarted(TestIdentifier tid) {
          tids.put(tid.getUniqueId(), tid);
          Optional<String> pid = tid.getParentId();
          if (tid.getType().isTest() && pid.isPresent()) {
            out0.update(prefix + tids.get(pid.get()).getDisplayName() + "." + tid.getDisplayName()
                + "... ");
          }
        }

        @Override
        public void executionFinished(TestIdentifier tid, TestExecutionResult testExecutionResult) {
          tids.remove(tid.getUniqueId());

          if (!tid.getParentId().isPresent()) {
            out0.updateln(prefix + "done");
          }
        }
      });

      TestExecutionSummary summary = summaryListener.getSummary();
      summary.printFailuresTo(out);
      summary.printTo(out);

      return summary;
    } finally {
      out.flush();
    }
  }
}
