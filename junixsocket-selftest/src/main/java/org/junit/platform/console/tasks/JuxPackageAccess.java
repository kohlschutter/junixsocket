package org.junit.platform.console.tasks;

import java.io.PrintWriter;

import org.junit.platform.console.options.Theme;
import org.junit.platform.launcher.TestExecutionListener;

public final class JuxPackageAccess {
  public static TestExecutionListener newTreePrintingListener(PrintWriter out,
      boolean disableAnsiColors, Theme theme) {
    return new TreePrintingListener(out, disableAnsiColors, theme);
  }
}
