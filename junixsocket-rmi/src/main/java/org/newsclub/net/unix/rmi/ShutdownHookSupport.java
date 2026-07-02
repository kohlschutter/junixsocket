/*
 * junixsocket
 *
 * Copyright 2009-2026 Christian Kohlschütter
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
package org.newsclub.net.unix.rmi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Simplifies handling shutdown hooks.
 *
 * @author Christian Kohlschütter
 */
final class ShutdownHookSupport {
  private static final List<Thread> HOOKS = ("true".equals(System.getProperty(
      "org.newsclub.net.unix.rmi.collect-shutdown-hooks", "false"))) ? new ArrayList<>() : null;

  /**
   * Registers a shutdown hook to be executed upon Runtime shutdown.
   *
   * NOTE: Only a weak reference to the hook is stored.
   *
   * @param hook The hook to register.
   * @return The thread, to be used with #removeShutdownHook
   */
  public static Thread addWeakShutdownHook(ShutdownHook hook) {
    Thread t = newShutdownThread(new WeakReference<>(hook));
    Runtime.getRuntime().addShutdownHook(t);
    if (HOOKS != null) {
      synchronized (HOOKS) {
        HOOKS.add(t);
      }
    }
    return t;
  }

  // only for unit testing
  @SuppressFBWarnings({"RU_INVOKE_RUN"})
  @SuppressWarnings("DoNotCall" /* errorprone */)
  static void runHooks() {
    if (HOOKS != null) {
      List<Thread> list;
      synchronized (HOOKS) {
        list = new ArrayList<>(HOOKS);
        HOOKS.clear();
      }
      for (Thread t : list) {
        t.run(); // NOPMD -- code coverage fails if we call .start()
      }
    }
  }

  /**
   * Something that wants to be called upon Runtime shutdown.
   *
   * @author Christian Kohlschütter
   */
  @FunctionalInterface
  interface ShutdownHook {
    /**
     * Called upon Runtime shutdown.
     *
     * When you implement this method, make sure to check that the given Thread matches the current
     * thread, e.g.: <code>
     * if (thread != Thread.currentThread()) {
     * throw new IllegalStateException("Illegal caller"); }
     * </code>
     *
     * @param thread The current Thread.
     * @throws Exception Most likely ignored
     */
    @SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
    void onRuntimeShutdown(Thread thread) throws Exception;
  }

  /**
   * Create a Thread that will be called upon Runtime shutdown.
   *
   * @param ref The shutdown hook (weak reference).
   */
  public static Thread newShutdownThread(WeakReference<ShutdownHook> ref) {
    return new Thread(() -> {
      ShutdownHook hook = ref.get();
      ref.clear();
      try {
        if (hook != null) {
          hook.onRuntimeShutdown(Thread.currentThread());
        }
      } catch (Exception e) {
        // ignore
      }
    }, "ShutdownHook");
  }
}
