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
package org.newsclub.net.unix.rmi;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInput;

/**
 * A specialized subclass of {@link RemoteFileDescriptorBase}, specifically for
 * {@link FileInputStream}s.
 *
 * @author Christian Kohlschütter
 */
public final class RemoteFileInput extends RemoteFileDescriptorBase<FileInputStream> implements
    Closeable {
  private static final long serialVersionUID = 1L;

  /**
   * Creates an uninitialized instance; used for externalization.
   *
   * @see #readExternal(ObjectInput)
   */
  public RemoteFileInput() {
    super();
  }

  /**
   * Creates a new {@link RemoteFileInput} instance, encapsulating a {@link FileInputStream} so that
   * it can be shared with other processes via RMI.
   *
   * @param socketFactory The socket factory.
   * @param fin The {@link FileInputStream}.
   * @throws IOException if the operation fails.
   */
  public RemoteFileInput(AFUNIXRMISocketFactory socketFactory, FileInputStream fin)
      throws IOException {
    super(socketFactory, fin, fin.getFD(), RemoteFileDescriptorBase.MAGIC_VALUE_MASK
        | RemoteFileDescriptorBase.BIT_READABLE);
  }

  /**
   * Returns a FileInputStream for the given instance. This either is the actual instance provided
   * by the constructor or a new instance created from the file descriptor.
   *
   * @return The FileInputStream.
   * @throws IOException if the operation fails.
   */
  public FileInputStream asFileInputStream() throws IOException {
    if ((getMagicValue() & RemoteFileDescriptorBase.BIT_READABLE) == 0) {
      throw new IOException("FileDescriptor is not readable");
    }

    return resource.accumulateAndGet(null, (prev, x) -> {
      if (prev != null) {
        return prev;
      }
      return new FileInputStream(getFileDescriptor()) {
        @Override
        public void close() throws IOException {
          super.close();

          synchronized (RemoteFileInput.this) {
            RemoteFileInput.this.close();
          }
        }
      };
    });
  }
}