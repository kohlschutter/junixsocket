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
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Exposes a {@link FileInputStream}'s basic functionality over RMI.
 *
 * As opposed to {@link RemoteFileDescriptorBase}, all data is read, then copied and serialized via
 * RMI.
 *
 * @author Christian Kohlschütter
 * @see RemoteFileDescriptorBase for a better way.
 */
public interface NaiveFileInputStreamRemote extends Remote, Closeable {
  RemoteFileInput getRemoteFileDescriptor() throws RemoteException;

  int read() throws IOException;

  long skip(long n) throws IOException;

  int available() throws IOException;

  byte[] readAllBytes() throws IOException;

  // JDK-8230967 If we want to call #close remotely, we need to declare it
  @Override
  void close() throws IOException;
}
