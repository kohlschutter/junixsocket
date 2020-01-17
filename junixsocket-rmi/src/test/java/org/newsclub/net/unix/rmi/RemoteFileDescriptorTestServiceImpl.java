/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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
import java.nio.file.Files;
import java.util.Arrays;

/**
 * The implementation for the test service for {@link RemoteFileDescriptorTest}.
 * 
 * @author Christian Kohlschütter
 */
public class RemoteFileDescriptorTestServiceImpl implements RemoteFileDescriptorTestService,
    Closeable {
  private final File tmpFile;
  private final AFUNIXRMISocketFactory socketFactory;

  public RemoteFileDescriptorTestServiceImpl(AFUNIXRMISocketFactory socketFactory)
      throws IOException {
    this.socketFactory = socketFactory;
    this.tmpFile = File.createTempFile("FDTestService", ".tmp");

    AFUNIXNaming.exportObject(this, socketFactory);
  }

  @Override
  public RemoteFileDescriptor stdout() throws IOException {
    return new RemoteFileDescriptor(socketFactory, FileDescriptor.out);
  }

  @Override
  @SuppressWarnings("resource")
  public RemoteFileInput input() throws IOException {
    return new RemoteFileInput(socketFactory, new FileInputStream(tmpFile));
  }

  @Override
  @SuppressWarnings("resource")
  public RemoteFileInput input(long skipBytes) throws IOException {
    FileInputStream fin = new FileInputStream(tmpFile);
    try {

      long skipped;
      while (skipBytes > 0 && (skipped = fin.skip(skipBytes)) >= 0) {
        skipBytes -= skipped;
      }

      return new RemoteFileInput(socketFactory, fin);
    } catch (IOException e) {
      fin.close();
      throw e;
    }
  }

  @Override
  @SuppressWarnings("resource")
  public RemoteFileOutput output() throws IOException {
    return new RemoteFileOutput(socketFactory, new FileOutputStream(tmpFile));
  }

  @Override
  public void verifyContents(byte[] expectedData) throws IOException {
    try (FileInputStream fin = new FileInputStream(tmpFile)) {
      byte[] bytes = TestUtils.readAllBytes(fin);
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
    Files.deleteIfExists(tmpFile.toPath());
  }

  @Override
  public void touchFile() throws IOException {
    Files.createFile(tmpFile.toPath());
  }

  @Override
  public NaiveFileInputStreamRemote naiveInputStreamRemote() throws IOException {
    return new NaiveFileInputStreamRemoteImpl(socketFactory, tmpFile);
  }

  @Override
  public void close() throws IOException {
    AFUNIXNaming.unexportObject(this);
    deleteFile();
  }
}
