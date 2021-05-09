package org.newsclub.net.unix;

import java.util.concurrent.atomic.AtomicBoolean;

abstract class CleanableState {
  private final AtomicBoolean clean = new AtomicBoolean(false);

  @SuppressWarnings("PMD.UnusedFormalParameter")
  protected CleanableState(Object observed) {
  }

  public final void runCleaner() {
    if (clean.compareAndSet(false, true)) {
      doClean();
    }
  }

  @SuppressWarnings("all")
  @Override
  protected final void finalize() {
    try {
      runCleaner();
    } catch (Exception e) {
      // nothing that can be done here
    }
  }

  protected abstract void doClean();
}
