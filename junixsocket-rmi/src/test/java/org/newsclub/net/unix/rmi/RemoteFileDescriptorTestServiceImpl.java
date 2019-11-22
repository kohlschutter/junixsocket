/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;

/**
 * The implementation for the test service for {@link RemoteFileDescriptorTest}.
 * 
 * @author Christian Kohlschütter
 */
public class RemoteFileDescriptorTestServiceImpl implements RemoteFileDescriptorTestService,
    Closeable {
  private File tmpFile;
  private final RMISocketFactory socketFactory;

  public RemoteFileDescriptorTestServiceImpl(RMISocketFactory socketFactory) throws IOException {
    this.socketFactory = socketFactory;
    this.tmpFile = File.createTempFile("FDTestService", ".tmp");

    UnicastRemoteObject.exportObject(this, 0, socketFactory, socketFactory);
  }

  @Override
  public RemoteFileDescriptor stdout() throws IOException {
    return new RemoteFileDescriptor(FileDescriptor.out);
  }

  @Override
  @SuppressWarnings("resource")
  public RemoteFileDescriptor.FileInput input() throws IOException {
    return new RemoteFileDescriptor.FileInput(new FileInputStream(tmpFile));
  }

  @Override
  @SuppressWarnings("resource")
  public RemoteFileDescriptor.FileInput input(long skipBytes) throws IOException {
    FileInputStream fin = new FileInputStream(tmpFile);
    fin.skip(skipBytes);
    return new RemoteFileDescriptor.FileInput(fin);
  }

  @Override
  @SuppressWarnings("resource")
  public RemoteFileDescriptor.FileOutput output() throws IOException {
    return new RemoteFileDescriptor.FileOutput(new FileOutputStream(tmpFile));
  }

  @Override
  public void verifyContents(byte[] expectedData) throws IOException {
    try (FileInputStream fin = new FileInputStream(tmpFile)) {
      byte[] bytes = fin.readAllBytes();
      if (!Arrays.equals(bytes, expectedData)) {
        throw new IOException("Unexpected bytes");
      }
    }
  }

  @Override
  public void verifyContents(int numBytes, byte[] expectedData) throws IOException {
    try (FileInputStream fin = new FileInputStream(tmpFile)) {
      byte[] bytes = new byte[numBytes];
      int numRead = fin.read(bytes);
      if (numRead != bytes.length || !Arrays.equals(bytes, expectedData)) {
        throw new IOException("Unexpected bytes");
      }
    }
  }

  @Override
  public void deleteFile() throws IOException {
    tmpFile.delete();
  }

  @Override
  public void touchFile() throws IOException {
    tmpFile.createNewFile();
  }

  @Override
  public NaiveFileInputStreamRemote naiveInputStreamRemote() throws IOException {
    return new NaiveFileInputStreamRemoteImpl(socketFactory, tmpFile);
  }

  @Override
  public void close() throws IOException {
    RemoteObjectUtil.unexportObject(this);
    deleteFile();
  }
}
