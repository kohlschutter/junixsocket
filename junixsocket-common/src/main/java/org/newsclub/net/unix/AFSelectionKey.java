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

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class AFSelectionKey extends SelectionKey {
  private static final int OP_INVALID = 1 << 7; // custom
  private final AFSelector sel;
  private final AFSocketCore core;
  private final AtomicInteger ops = new AtomicInteger();
  private final AtomicInteger opsReady = new AtomicInteger(0);
  private final SelectableChannel chann;
  private final AtomicBoolean cancelled = new AtomicBoolean();

  AFSelectionKey(AFSelector selector, AbstractSelectableChannel ch, int ops, Object att) {
    super();
    this.chann = ch;
    this.sel = selector;
    this.ops.set(ops); // FIXME check

    if (ch instanceof AFDatagramChannel<?>) {
      this.core = ((AFDatagramChannel<?>) ch).getAFCore();
    } else if (ch instanceof AFSocketChannel<?>) {
      this.core = ((AFSocketChannel<?>) ch).getAFCore();
    } else if (ch instanceof AFServerSocketChannel<?>) {
      this.core = ((AFServerSocketChannel<?>) ch).getAFCore();
    } else {
      throw new UnsupportedOperationException("Unsupported channel: " + ch);
    }

    attach(att);
  }

  @Override
  public SelectableChannel channel() {
    return chann;
  }

  @Override
  public Selector selector() {
    return sel;
  }

  @Override
  public boolean isValid() {
    return !hasOpInvalid() && !cancelled.get() && chann.isOpen() && sel.isOpen();
  }

  boolean isCancelled() {
    return cancelled.get();
  }

  boolean hasOpInvalid() {
    return (opsReady.get() & OP_INVALID) != 0;
  }

  boolean isSelected() {
    return readyOps() != 0;
  }

  @Override
  public void cancel() {
    sel.remove(this);
    cancelNoRemove();
  }

  void cancelNoRemove() {
    if (!cancelled.compareAndSet(false, true) || !chann.isOpen()) {
      return;
    }

    cancel1();
  }

  private void cancel1() {
    // FIXME
  }

  @Override
  public int interestOps() {
    return ops.get();
  }

  @Override
  public SelectionKey interestOps(int interestOps) {
    this.ops.set(interestOps); // FIXME check
    return this;
  }

  @Override
  public int readyOps() {
    return opsReady.get() & ~OP_INVALID;
  }

  AFSocketCore getAFCore() {
    return core;
  }

  void setOpsReady(int opsReady) {
    this.opsReady.set(opsReady);
  }

  @Override
  public String toString() {
    return super.toString() + "[" + readyOps() + ";valid=" + isValid() + ";channel=" + channel()
        + "]";
  }
}