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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Supplies a FileChannel with certain properties (read-only, write-only, read-write); to be used
 * mostly with {@link FileDescriptorCast}.
 *
 * @author Christian Kohlschütter
 */
public abstract class FileChannelSupplier implements AFIOSupplier<FileChannel> {
  private final FileChannel fc;

  FileChannelSupplier(FileChannel fc) {
    this.fc = fc;
  }

  /**
   * Supplies a read-only {@link FileChannel}.
   */
  public static final class ReadOnly extends FileChannelSupplier {
    ReadOnly(FileChannel fc) {
      super(fc);
    }

    @SuppressWarnings("resource")
    ReadOnly(FileDescriptor fd) {
      this(new FileInputStream(fd).getChannel());
    }
  }

  /**
   * Supplies a write-only {@link FileChannel}.
   */
  public static final class WriteOnly extends FileChannelSupplier {
    WriteOnly(FileChannel fc) {
      super(fc);
    }

    @SuppressWarnings("resource")
    WriteOnly(FileDescriptor fd) {
      this(new FileOutputStream(fd).getChannel());
    }

  }

  /**
   * Supplies a read-write {@link FileChannel}.
   */
  public static final class ReadWrite extends FileChannelSupplier {
    ReadWrite(FileChannel fc) {
      super(fc);
    }

    ReadWrite(FileDescriptor fd) throws IOException {
      this(RAFChannelProvider.getFileChannel(fd));
    }
  }

  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public FileChannel get() throws IOException {
    return fc;
  }
}
