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
package org.newsclub.net.unix.memory;

/**
 * Specific "memory seal" operations.
 *
 * @author Christian Kohlschütter
 * @see SharedMemory#addSeals(java.util.Set)
 */
public enum MemorySeal {
  /**
   * Prevents any further modifications to the set of seals.
   */
  PREVENT_SEAL,

  /**
   * Prevents shrinking the memory area in question.
   */
  PREVENT_SHRINK,

  /**
   * Prevents growing the memory area in question.
   */
  PREVENT_GROW,

  /**
   * Prevents writing to the memory area in question.
   */
  PREVENT_WRITE,

  /**
   * Prevents writing to the memory area in question, except for shared writable mappings that were
   * created prior to the seal being set. This is available on Linux since version 5.1, and on
   * NetBSD since version 11.
   */
  PREVENT_FUTURE_WRITE,
}
