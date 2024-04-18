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

import org.eclipse.jdt.annotation.NonNull;

final class ThreadLocalObjectPool<O> implements ObjectPool<O> {
  private final ThreadLocal<O> tl;
  private final ObjectSanitizer<O> sanitizer;

  private final Lease<O> leaseImpl = new Lease<O>() {
    private boolean discarded = false;

    @Override
    public void close() {
      if (!discarded && !sanitizer.sanitize(tl.get())) {
        tl.remove();
      }
    }

    @Override
    public O get() {
      return tl.get();
    }

    @Override
    public void discard() {
      discarded = true;
      tl.remove();
    }
  };

  ThreadLocalObjectPool(ObjectSupplier<@NonNull O> supplier,
      ObjectSanitizer<@NonNull O> sanitizer) {
    this.sanitizer = sanitizer;
    tl = new ThreadLocal<O>() {

      @Override
      protected O initialValue() {
        return supplier.get();
      }
    };
  }

  @Override
  public Lease<O> take() {
    return leaseImpl;
  }
}
