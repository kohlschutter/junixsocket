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

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Some {@link Path}-related helper methods.
 *
 * @author Christian Kohlschütter
 */
final class PathUtil {
  private PathUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Checks if the given path is in the default file system.
   *
   * @param p The path.
   * @return {@code true} if so.
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  static boolean isPathInDefaultFileSystem(Path p) {
    FileSystem fs = p.getFileSystem();
    if (fs != FileSystems.getDefault() || fs.getClass().getModule() != Object.class.getModule()) {
      return false;
    }
    return true;
  }
}
