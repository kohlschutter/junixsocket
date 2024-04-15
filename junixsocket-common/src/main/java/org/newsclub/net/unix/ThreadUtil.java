/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class to support certain Thread-specific features.
 * 
 * @author Christian Kohlschütter
 */
public final class ThreadUtil {
  private static final boolean VIRTUAL_THREADS_SUPPORTED = Boolean.parseBoolean(System.getProperty(
      "org.newsclub.net.unix.virtual-threads", "true"));

  private ThreadUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Checks if the current {@link Thread} is to be considered a virtual thread.
   * 
   * @return {@code true} if so.
   */
  public static boolean isVirtualThread() {
    return VIRTUAL_THREADS_SUPPORTED && Thread.currentThread().isVirtual();
  }

  /**
   * Checks if virtual threads are considered to be supported (and therefore if special support
   * should be enabled).
   * 
   * @return {@code true} if so.
   */
  public static boolean isVirtualThreadSupported() {
    return VIRTUAL_THREADS_SUPPORTED;
  }

  /**
   * Returns a new "virtual thread per task executor" if possible, otherwise a new "cached thread
   * pool".
   * 
   * @return The new executor service.
   */
  public static ExecutorService newVirtualThreadOrCachedThreadPoolExecutor() {
    if (isVirtualThreadSupported()) {
      return Executors.newVirtualThreadPerTaskExecutor();
    } else {
      return Executors.newCachedThreadPool();
    }
  }
}
