/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * A {@link Set} that won't allow adding elements.
 *
 * @param <T> The element type.
 * @author Christian Kohlschütter
 */
final class UngrowableSet<T> implements Set<T> {
  private final Set<T> set;

  UngrowableSet(Set<T> set) {
    this.set = set;
  }

  @Override
  public int size() {
    return set.size();
  }

  @Override
  public boolean isEmpty() {
    return set.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return set.contains(o);
  }

  @Override
  public Iterator<T> iterator() {
    return set.iterator();
  }

  @Override
  public Object[] toArray() {
    return set.toArray();
  }

  @Override
  public <E> E[] toArray(E[] a) {
    return set.toArray(a);
  }

  @Override
  public boolean add(T e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o) {
    return set.remove(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return set.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return set.retainAll(c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return set.removeAll(c);
  }

  @Override
  public void clear() {
    set.clear();
  }
}
