/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

/**
 * Hack to get a readable AND writable {@link FileChannel} for a {@link FileDescriptor}.
 * 
 * @author Christian Kohlschütter
 */
final class RAFChannelProvider extends RandomAccessFile implements FileDescriptorAccess {
  private final FileDescriptor fdObj;
  private int rafFd;

  private RAFChannelProvider(FileDescriptor fdObj) throws IOException {
    this(fdObj, File.createTempFile("jux", ".sock"));
  }

  private RAFChannelProvider(FileDescriptor fdObj, File tempPath) throws IOException {
    super(tempPath, "rw");
    if (!tempPath.delete() && tempPath.exists()) {
      throw new IOException("Could not delete temporary file: " + tempPath);
    }
    this.fdObj = fdObj;

    FileDescriptor rafFdObj = getFD();
    this.rafFd = NativeUnixSocket.getFD(rafFdObj);

    int fd = NativeUnixSocket.getFD(fdObj);
    if (fd <= 0) {
      throw new IOException("Cannot access file descriptor handle");
    }

    NativeUnixSocket.initFD(rafFdObj, fd);
  }

  @Override
  public FileDescriptor getFileDescriptor() {
    return fdObj;
  }

  @Override
  public void close() throws IOException {
    if (rafFd > 0) {
      NativeUnixSocket.initFD(getFD(), rafFd | (rafFd = 0));
    }
  }
  
  public static FileChannel getFileChannel(FileDescriptor fd) throws IOException {
    return new RAFChannelProvider(fd).getChannel();
  }
}
