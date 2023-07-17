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

import java.io.File;
import java.util.Map;

/**
 * Some bridging code that allows junixsocket-selftest to do some in-depth diagnostics.
 *
 * @author Christian Kohlschütter
 */
public final class SelftestDiagnosticsHelper {
  private SelftestDiagnosticsHelper() {
  }

  /**
   * Returns the error that prevented the native library from loading, or {@code null}.
   *
   * @return The error, or {@code null}.
   */
  public static Throwable initError() {
    return NativeUnixSocket.retrieveInitError();
  }

  /**
   * Returns the temporary directory used for storing the native library, or {@code null}.
   *
   * @return The directory, or {@code null}.
   */
  public static File tempDir() {
    return NativeLibraryLoader.tempDir();
  }

  /**
   * Returns properties determined upon Maven build time.
   *
   * For performance reasons, these will not be correctly resolves when developing in Eclipse.
   *
   * @return The properties.
   */
  public static Map<String, String> buildProperties() {
    return BuildProperties.getBuildProperties();
  }
}
