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

import java.io.Closeable;
import java.util.function.Supplier;

public interface ObjectPool<O> {
  Lease<O> take();

  static <O> ObjectPool<O> newThreadLocalPool(Supplier<O> supplier) {
    // return new ThreadLocalObjectPool<>(supplier);
    // return new ConcurrentQueueObjectPool<>(supplier);
    return new VirtualAwareThreadLocalObjectPool<>(supplier);
  }

  interface Lease<O> extends Closeable {
    O get();

    @Override
    void close();
  }

  static <O> Lease<O> unpooledLease(O obj) {
    return new Lease<O>() {

      @Override
      public O get() {
        return obj;
      }

      @Override
      public void close() {
      }
    };
  }
}
