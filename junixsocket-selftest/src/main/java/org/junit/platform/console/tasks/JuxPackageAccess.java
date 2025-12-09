package org.junit.platform.console.tasks;

import java.io.PrintWriter;

import org.junit.platform.console.output.ColorPalette;
import org.junit.platform.console.output.Theme;
import org.junit.platform.console.output.TreePrintingListener;
import org.junit.platform.launcher.TestExecutionListener;

/**
 * Helper class to access {@link TreePrintingListener}.
 * 
 * @author Christian Kohlschütter
 */
public final class JuxPackageAccess {
  public static TestExecutionListener newTreePrintingListener(PrintWriter out,
      boolean disableAnsiColors, Theme theme) {
    return new TreePrintingListener(out, disableAnsiColors ? ColorPalette.NONE
        : ColorPalette.DEFAULT, theme);
  }
}
