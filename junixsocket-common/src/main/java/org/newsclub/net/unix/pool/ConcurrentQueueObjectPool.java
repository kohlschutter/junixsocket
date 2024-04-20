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

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An {@link ObjectPool} implemented using {@link ConcurrentLinkedQueue}.
 *
 * @param <O> The object type.
 * @author Christian Kohlschütter
 */
public final class ConcurrentQueueObjectPool<O> implements ObjectPool<O> {
  private final AtomicInteger count = new AtomicInteger(0);
  private final Queue<O> queue = new ConcurrentLinkedQueue<>();
  private final ObjectSupplier<O> supplier;
  private final int maxCapacity;
  private final ObjectSanitizer<O> sanitizer;

  /**
   * Constructs a {@link ConcurrentQueueObjectPool} with the given capacity, supplier and sanitizer.
   *
   * @param supplier The supplier.
   * @param sanitizer The sanitizer.
   * @param maxCapacity The maximum capacity.
   */
  public ConcurrentQueueObjectPool(ObjectSupplier<@NonNull O> supplier,
      ObjectSanitizer<@NonNull O> sanitizer, final int maxCapacity) {
    if (maxCapacity < 0) {
      throw new IllegalArgumentException("maxCapacity");
    }
    this.supplier = Objects.requireNonNull(supplier);
    this.sanitizer = Objects.requireNonNull(sanitizer);
    this.maxCapacity = maxCapacity;
  }

  @Override
  public Lease<O> take() {
    O obj = queue.poll();
    if (obj == null) {
      obj = Objects.requireNonNull(supplier.get());
    } else {
      count.decrementAndGet();
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
        if (count.get() >= maxCapacity) {
          // enough objects
        } else if (!sanitizer.sanitize(theObject)) {
          // decided not to reuse
        } else {
          if (queue.offer(theObject)) {
            count.incrementAndGet();
          }
        }
      }
    }

    @Override
    public void discard() {
      obj = null;
    }
  }
}
