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

import org.eclipse.jdt.annotation.NonNull;
import org.newsclub.net.unix.ThreadUtil;

/**
 * A pool of objects.
 *
 * @param <O> The object type.
 * @author Christian Kohlschütter
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ObjectPool<O> {

  /**
   * Creates a new {@link ObjectPool} that is used within a single thread; this may or may not be
   * implemented using {@link ThreadLocal}, however the behavior should be comparable.
   *
   * @param <O> The object type.
   * @param supplier The object supplier.
   * @param sanitizer The object sanitizer.
   * @return The object pool.
   */
  static <O> ObjectPool<O> newThreadLocalPool(ObjectSupplier<@NonNull O> supplier,
      ObjectSanitizer<@NonNull O> sanitizer) {
    if (ThreadUtil.isVirtualThreadSupported()) {
      return new VirtualAwareThreadLocalObjectPool<>(supplier, sanitizer);
    } else {
      return new ThreadLocalObjectPool<>(supplier, sanitizer);
    }
  }

  /**
   * Returns a {@link Lease} that is not backed by any object pool.
   *
   * @param <O> The object type.
   * @param obj The object.
   * @return The lease; closing/discarding has no effect.
   */
  static <O> Lease<O> unpooledLease(O obj) {
    return new Lease<O>() {

      @Override
      public O get() {
        return obj;
      }

      @Override
      public void close() {
      }

      @Override
      public void discard() {
      }
    };
  }

  /**
   * Takes an exclusive lease of an object from the pool. If no existing object is available from
   * the pool, a new one may be provided.
   *
   * @return The object.
   */
  Lease<O> take();

  /**
   * Supplies a leased object.
   *
   * @param <T> The object type.
   */
  @FunctionalInterface
  interface ObjectSupplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();
  }

  /**
   * Sanitizes a previously leased object so it can be reused by the pool.
   *
   * @param <T> The object type.
   */
  @FunctionalInterface
  interface ObjectSanitizer<T> {
    /**
     * Sanitizes a previously leased object so it can be reused by the pool; if the object should
     * not be reused, {@code false} is returned.
     *
     * @param obj The object to sanitize.
     * @return {@code true} if sanitization was successful, {@code false} if the object should not
     *         be reused.
     */
    boolean sanitize(T obj);
  }

  /**
   * A lease for an object (obtained via {@link #get()}); working with the object is only permitted
   * before {@link #close()}.
   *
   * @param <O> The object type.
   */
  interface Lease<O> extends Closeable {
    /**
     * Returns the leased object, potentially {@code null} when discarded/closed.
     *
     * @return The object, or {@code null}.
     */
    O get();

    /**
     * Terminates the validity of this lease. Unless discarded via {@link #discard()}, the object
     * may end up back in the object pool it was leased from; however that is decided by the pool.
     */
    @Override
    void close();

    /**
     * Marks the leased object as discarded, potentially preventing it from being reused in the
     * object pool.
     */
    void discard();
  }
}
