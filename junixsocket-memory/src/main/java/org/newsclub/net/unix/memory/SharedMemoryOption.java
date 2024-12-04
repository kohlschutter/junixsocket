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

import org.newsclub.net.unix.MemoryImplUtilInternal;

/**
 * Options for {@link SharedMemory}s.
 *
 * @author Christian Kohlschütter
 */
public enum SharedMemoryOption {
  /**
   * Open for reading only (instead of read-write).
   */
  READ_ONLY(MemoryImplUtilInternal.MOPT_RDONLY), //

  /**
   * Allow sealing operations (only supported on Linux).
   */
  SEALABLE(MemoryImplUtilInternal.MOPT_SEALABLE), //

  /**
   * Require a more "secret" memory area.
   * <p>
   * On Linux, this uses {@code memfd_secret}, which needs to be enabled via a kernel option
   * ({@code secretmem.enable=1}). Note that this feature may impact overall system performance,
   * such as disabling hibernation.
   */
  SECRET(MemoryImplUtilInternal.MOPT_SECRET), //

  /**
   * Unlink the object upon calling {@link SharedMemory#close}.
   */
  UNLINK_UPON_CLOSE(MemoryImplUtilInternal.MOPT_UNLINK_UPON_CLOSE), //
  ;

  private final int opt;

  SharedMemoryOption(int opt) {
    this.opt = opt;
  }

  /**
   * An value only used by junixsocket internals; do not use.
   *
   * @return The value.
   */
  public int getOpt() {
    return opt;
  }
}
