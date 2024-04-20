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
import org.newsclub.net.unix.ThreadUtil;

final class VirtualAwareThreadLocalObjectPool<O> implements ObjectPool<O> {
  private final ThreadLocalObjectPool<O> tlPool;
  private final ConcurrentQueueObjectPool<O> cqPool;

  public VirtualAwareThreadLocalObjectPool(ObjectSupplier<@NonNull O> supplier,
      ObjectSanitizer<@NonNull O> sanitizer) {
    this.tlPool = new ThreadLocalObjectPool<>(supplier, sanitizer);
    this.cqPool = new ConcurrentQueueObjectPool<>(supplier, sanitizer, Runtime.getRuntime()
        .availableProcessors() * 2);
  }

  @Override
  public Lease<O> take() {
    if (ThreadUtil.isVirtualThread()) {
      return cqPool.take();
    } else {
      return tlPool.take();
    }
  }
}
