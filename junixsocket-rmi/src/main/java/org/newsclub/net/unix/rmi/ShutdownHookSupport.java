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
package org.newsclub.net.unix.rmi;

import java.lang.ref.WeakReference;

/**
 * Simplifies handling shutdown hooks.
 * 
 * @author Christian Kohlschütter
 */
final class ShutdownHookSupport {
  private ShutdownHookSupport() {
    throw new UnsupportedOperationException("No instances");
  }

  /**
   * Registers a shutdown hook to be executed upon Runtime shutdown.
   * 
   * NOTE: Only a weak reference to the hook is stored.
   * 
   * @param hook The hook to register.
   */
  public static void addWeakShutdownHook(ShutdownHook hook) {
    Runtime.getRuntime().addShutdownHook(new ShutdownThread(new WeakReference<>(hook)));
  }

  /**
   * Something that wants to be called upon Runtime shutdown.
   * 
   * @author Christian Kohlschütter
   */
  interface ShutdownHook {
    /**
     * Called upon Runtime shutdown.
     * 
     * When you implement this method, make sure to check that the given Thread matches the current
     * thread, e.g.: <code>
     * if (thread != Thread.currentThread() || !(thread instanceof ShutdownThread)) {
     * throw new IllegalStateException("Illegal caller"); }
     * </code>
     * 
     * @param thread The current Thread.
     */
    void onRuntimeShutdown(Thread thread);
  }

  /**
   * The Thread that will be called upon Runtime shutdown.
   * 
   * @author Christian Kohlschütter
   */
  static final class ShutdownThread extends Thread {
    private final WeakReference<ShutdownHook> ref;

    private ShutdownThread(WeakReference<ShutdownHook> ref) {
      super();
      this.ref = ref;
    }

    @Override
    public void run() {
      ShutdownHook hook = ref.get();
      if (hook != null) {
        hook.onRuntimeShutdown(this);
      }
    }
  }
}
