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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.util.IOUtil;

public final class NaiveFileInputStreamRemoteImpl extends FileInputStream implements
    NaiveFileInputStreamRemote {
  private final RemoteFileInput rfd;

  public NaiveFileInputStreamRemoteImpl(AFUNIXRMISocketFactory socketFactory, File file)
      throws IOException {
    super(file);
    this.rfd = new RemoteFileInput(socketFactory, this);

    AFNaming.exportObject(this, socketFactory);
  }

  public NaiveFileInputStreamRemoteImpl(AFUNIXRMISocketFactory socketFactory, FileDescriptor fd)
      throws IOException {
    super(fd);
    this.rfd = new RemoteFileInput(socketFactory, this);

    AFNaming.exportObject(this, socketFactory);
  }

  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public RemoteFileInput getRemoteFileDescriptor() {
    return rfd;
  }

  @Override
  public void close() throws IOException {
    AFNaming.unexportObject(this);
    super.close();
  }

  @Override
  public byte[] readAllBytes() throws IOException {
    return IOUtil.readAllBytesNaively(this);
  }
}
