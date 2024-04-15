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
package org.newsclub.net.unix.pool;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

public final class ConcurrentQueueObjectPool<O> implements ObjectPool<O> {
  private final Queue<O> queue = new ConcurrentLinkedQueue<>();
  private final Supplier<O> supplier;

  public ConcurrentQueueObjectPool(Supplier<O> supplier) {
    this.supplier = supplier;
  }

  @Override
  public Lease<O> take() {
    O obj = queue.poll();
    if (obj == null) {
      obj = supplier.get();
    }
    return new LeaseImpl(obj);
  }

  private final class LeaseImpl implements Lease<O> {
    private @Nullable O obj;

    public LeaseImpl(O obj) {
      this.obj = obj;
    }

    @SuppressWarnings("null")
    @Override
    public O get() {
      return obj;
    }

    @Override
    public synchronized void close() {
      @Nullable
      O theObject = obj;
      obj = null;
      if (theObject != null) {
        queue.offer(theObject);
      }
    }
  }
}
