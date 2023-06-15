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
package org.newsclub.net.unix.selftest.nativeimage;

import org.newsclub.net.unix.selftest.Selftest;

/**
 * Main entry.
 *
 * @author Christian Kohlschütter
 */
public final class Main { // NOPMD
  private Main() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Starts junixsocket-selftest.
   *
   * @param args The args.
   * @throws Exception on error.
   */
  public static void main(String[] args) throws Exception {
    Selftest.main(args);
  }
}
