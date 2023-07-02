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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInput;

/**
 * A generic (unspecific) {@link FileDescriptor} reference.
 *
 * @author Christian Kohlschütter
 * @see RemoteFileInput
 * @see RemoteFileOutput
 */
public final class RemoteFileDescriptor extends RemoteFileDescriptorBase<Void> {
  private static final long serialVersionUID = 1L;

  /**
   * Creates an uninitialized instance; used for externalization.
   *
   * @see #readExternal(ObjectInput)
   */
  public RemoteFileDescriptor() {
    super();
  }

  /**
   * Creates a new {@link RemoteFileOutput} instance, encapsulating a generic {@link FileDescriptor}
   * so that it can be shared with other processes via RMI.
   *
   * @param socketFactory The socket factory.
   * @param fd The {@link FileDescriptor}.
   */
  public RemoteFileDescriptor(AFUNIXRMISocketFactory socketFactory, FileDescriptor fd) {
    super(socketFactory, null, fd, MAGIC_VALUE_MASK | 0);
  }

  @Override
  public synchronized void close() throws IOException {
    FileDescriptor fd = getFileDescriptor();
    if (fd != null && fd.valid()) {
      try (FileInputStream unused = new FileInputStream(fd)) {
        // should succeed
      }
    }
  }
}