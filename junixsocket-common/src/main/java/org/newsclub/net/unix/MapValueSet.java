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

import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

/**
 * A {@link Set} that is a view on the keys of a {@link Map} that have a certain value.
 * <p>
 * The value is controlled by the concrete subclass ({@link #getValue()}). It can, for example, be a
 * boolean or a counter, depending on the use case. If the value is equal to a "removed" sentinel
 * value.
 *
 * @param <T> The element type.
 * @author Christian Kohlschütter
 */
final class MapValueSet<T, V> implements Set<T> {
  private final Map<T, V> map;
  private final ValueSupplier<@NonNull V> valueSupplier;
  private final V removedSentinel;

  @SuppressWarnings("unchecked")
  MapValueSet(Map<? extends T, V> map, ValueSupplier<@NonNull V> valueSupplier, V removedSentinel) {
    this.valueSupplier = Objects.requireNonNull(valueSupplier);
    this.removedSentinel = removedSentinel;
    this.map = (Map<T, V>) map;
  }

  @FunctionalInterface
  interface ValueSupplier<V> {
    V supplyValue();
  }

  /**
   * Marks the given element as "removed"; this may actually add an element to the underlying map.
   * <p>
   * Depending on the "removed" sentinel, the key may be added (if value is non-null but the map
   * does not yet contain the key), modified (value is non-null, and the map has a different value
   * for the key), or removed (if value is null).
   *
   * @param elem The element to remove.
   */
  public void markRemoved(T elem) {
    if (removedSentinel == null) {
      map.remove(elem);
    } else {
      map.put(elem, removedSentinel);
    }
  }

  /**
   * Sets all entries in the backing map to the "removed" sentinel, or removes them all if that
   * value is {@code null}.
   */
  public void markAllRemoved() {
    if (removedSentinel == null) {
      map.clear();
    } else {
      for (Map.Entry<T, V> en : map.entrySet()) {
        en.setValue(removedSentinel);
      }
    }
  }

  private @NonNull V getValue() {
    return Objects.requireNonNull(valueSupplier.supplyValue());
  }

  @Override
  public int size() {
    V val = getValue();
    if (val.equals(removedSentinel)) {
      return 0;
    }

    int size = 0;
    for (Map.Entry<T, V> en : map.entrySet()) {
      if (val.equals(en.getValue())) {
        size++;
      }
    }
    return size;
  }

  @Override
  public boolean isEmpty() {
    V val = getValue();
    if (val.equals(removedSentinel)) {
      return true;
    }

    for (Map.Entry<T, V> en : map.entrySet()) {
      if (val.equals(en.getValue())) {
        return false;
      }
    }
    return true;
  }

  private boolean isDefinitelyEmpty() {
    return getValue().equals(removedSentinel);
  }

  @Override
  public boolean contains(Object o) {
    if (isDefinitelyEmpty()) {
      return false;
    }
    return getValue().equals(map.get(o));
  }

  @Override
  public Iterator<T> iterator() {
    if (isDefinitelyEmpty()) {
      return Collections.emptyIterator();
    }

    Iterator<Map.Entry<T, V>> mapit = map.entrySet().iterator();

    V val = getValue();

    return new Iterator<T>() {
      Map.Entry<T, V> nextObj = null;
      Map.Entry<T, V> currentObj = null;

      @Override
      public boolean hasNext() {
        if (nextObj != null) {
          return true;
        }
        while (mapit.hasNext()) {
          Map.Entry<T, V> en = mapit.next();
          if (val.equals(en.getValue())) {
            nextObj = en;
            return true;
          }
        }
        return false;
      }

      @Override
      public T next() {
        currentObj = null;
        if (nextObj == null) {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
        }
        T next = nextObj.getKey();
        if (val.equals(nextObj.getValue())) {
          currentObj = nextObj;
          nextObj = null;
          return next;
        } else {
          throw new ConcurrentModificationException();
        }
      }

      @Override
      public void remove() {
        if (currentObj == null) {
          throw new IllegalStateException();
        }
        markRemoved(currentObj.getKey());
        currentObj = null;
      }
    };
  }

  @Override
  @SuppressWarnings("PMD.OptimizableToArrayCall")
  public Object[] toArray() {
    return toArray(new Object[size()]);
  }

  @SuppressWarnings({"unchecked", "null"})
  @Override
  public <E> E[] toArray(E[] a) {
    int size = size();

    if (a.length < size) {
      return toArray((E[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(),
          size));
    }

    int i = 0;
    for (T elem : this) {
      a[i++] = (E) elem;
    }
    if (i < a.length) {
      a[i] = null;
    }

    return a;
  }

  /**
   * Updates an already-existing entry in the backing map to the current value (obtained via
   * {@link #getValue()}), thereby adding it to the set.
   *
   * @param e The entry to update.
   */
  public boolean update(T e) {
    if (map.containsKey(e)) {
      map.put(e, getValue());
      return true;
    } else {
      return false;
    }
  }

  /**
   * Adds an entry to the set, adding it to the backing map if necessary.
   */
  @Override
  public boolean add(T e) {
    if (contains(e)) {
      return false;
    } else if (update(e)) {
      return true;
    } else {
      map.put(e, getValue());
      return true;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean remove(Object o) {
    if (isDefinitelyEmpty() || !map.containsKey(o)) {
      return false;
    }

    markRemoved((T) o);
    return true;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    if (isDefinitelyEmpty()) {
      return c.isEmpty();
    }
    for (Object obj : c) {
      if (!contains(obj)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    boolean changed = false;
    for (T elem : c) {
      changed |= add(elem);
    }
    return changed;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    boolean changed = false;
    for (Iterator<T> it = iterator(); it.hasNext();) {
      T elem = it.next();
      if (!c.contains(elem)) {
        it.remove();
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    if (isDefinitelyEmpty()) {
      return false;
    }
    boolean changed = false;
    for (Object obj : c) {
      changed |= remove(obj);
    }
    return changed;
  }

  /**
   * Marks all entries in the backing map that are currently considered contained in this set as
   * removed; see {@link #markAllRemoved()} for an unoptimized version that affects all keys.
   *
   * @see #markAllRemoved()
   */
  @Override
  public void clear() {
    V val = getValue();
    if (val.equals(removedSentinel)) {
      return;
    }

    for (Map.Entry<T, V> en : map.entrySet()) {
      if (val.equals(en.getValue())) {
        markRemoved(en.getKey());
      }
    }
  }
}
