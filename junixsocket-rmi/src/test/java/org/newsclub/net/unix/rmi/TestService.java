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

import java.io.IOException;
import java.rmi.Remote;

import org.newsclub.net.unix.AFUNIXSocketCredentials;

/**
 * A test service.
 *
 * @author Christian Kohlschütter
 */
public interface TestService extends Remote {
  RemoteFileDescriptor stdout() throws IOException;

  RemoteFileInput input() throws IOException;

  RemoteFileInput input(long skipBytes) throws IOException;

  RemoteFileOutput output() throws IOException;

  void verifyContents(byte[] expectedData) throws IOException;

  void verifyContents(int numBytes, byte[] expectedData) throws IOException;

  void touchFile() throws IOException;

  void deleteFile() throws IOException;

  NaiveFileInputStreamRemote naiveInputStreamRemote() throws IOException;

  AFUNIXSocketCredentials remotePeerCredentials() throws IOException;

  <T extends RemoteCloseableThing> RemoteCloseable<? extends T> remoteCloseable(Class<T> klass)
      throws IOException;

  <T extends RemoteCloseableThing> void remoteCloseableThingResetNumberOfCloseCalls(Class<T> klass)
      throws IOException;

  <T extends RemoteCloseableThing> int remoteCloseableThingNumberOfCloseCalls(Class<T> klass)
      throws IOException;
}
