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

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Hack to get a readable AND writable {@link FileChannel} for a {@link FileDescriptor}.
 *
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings("PATH_TRAVERSAL_IN")
final class RAFChannelProvider extends RandomAccessFile implements FileDescriptorAccess {
  private static final File DEV_NULL = new File("/dev/null");

  private final File tempPath;
  private final FileDescriptor fdObj;
  private final FileDescriptor rafFdOrig = new FileDescriptor();
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private RAFChannelProvider(FileDescriptor fdObj) throws IOException {
    this(fdObj, File.createTempFile("jux", ".sock"));
  }

  private RAFChannelProvider(FileDescriptor fdObj, File tempPath) throws IOException {
    this(fdObj, tempPath, true);
  }

  private RAFChannelProvider(FileDescriptor fdObj, File tempPath, boolean delete)
      throws IOException {
    super(tempPath, "rw");
    NativeUnixSocket.ensureSupported();

    this.tempPath = tempPath;
    if (delete) {
      if (!tempPath.delete() && tempPath.exists()) {
        if (!tempPath.delete()) {
          // we tried our best (looking at you, Windows)
        }
        tempPath = new File(tempPath.getParentFile(), "jux-" + UUID.randomUUID().toString()
            + ".sock");
        if (tempPath.exists()) {
          throw new IOException("Could not create a temporary path: " + tempPath);
        }
      }
      tempPath.deleteOnExit();
    }

    this.fdObj = fdObj;

    FileDescriptor rafFdObj = getFD();
    NativeUnixSocket.copyFileDescriptor(rafFdObj, rafFdOrig);
    NativeUnixSocket.copyFileDescriptor(fdObj, rafFdObj);
  }

  @Override
  public FileDescriptor getFileDescriptor() {
    return fdObj;
  }

  @Override
  public synchronized void close() throws IOException {
    if (!closed.getAndSet(true)) {
      NativeUnixSocket.copyFileDescriptor(rafFdOrig, getFD());
      if (!tempPath.delete()) {
        // we tried our best
      }
    }
  }

  public static FileChannel getFileChannel(FileDescriptor fd) throws IOException {
    return getFileChannel0(fd);
  }

  @SuppressWarnings("resource")
  private static FileChannel getFileChannel0(FileDescriptor fd) throws IOException {
    if (DEV_NULL.canRead() && DEV_NULL.canWrite()) {
      try {
        return new RAFChannelProvider(fd, DEV_NULL, false).getChannel();
      } catch (IOException e) {
        // ignore
      }
    }
    return new RAFChannelProvider(fd).getChannel();
  }
}
