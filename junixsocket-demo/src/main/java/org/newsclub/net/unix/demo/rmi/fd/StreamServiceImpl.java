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
package org.newsclub.net.unix.demo.rmi.fd;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.rmi.RemoteException;

import org.newsclub.net.unix.demo.rmi.services.StreamService;
import org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory;
import org.newsclub.net.unix.rmi.RemoteCloseableImpl;
import org.newsclub.net.unix.rmi.RemoteFileInput;
import org.newsclub.net.unix.rmi.RemoteFileOutput;

public class StreamServiceImpl implements StreamService, Closeable {
  private final AFUNIXRMISocketFactory socketFactory;

  public StreamServiceImpl(AFUNIXRMISocketFactory socketFactory) throws RemoteException {
    this.socketFactory = socketFactory;
  }

  @Override
  public void close() throws IOException {
  }

  @SuppressWarnings("resource")
  @Override
  public RemoteCloseableImpl<RemoteFileInput> openForReading(File path) throws IOException {
    boolean permitted = mayRead(path);
    System.out.println("Reading from " + path + "; permitted=" + permitted);
    if (!permitted) {
      throw new AccessDeniedException("Not permitted");
    }
    FileInputStream fin = new FileInputStream(path);
    return new RemoteCloseableImpl<>(socketFactory, new RemoteFileInput(socketFactory, fin));
  }

  @SuppressWarnings("resource")
  @Override
  public RemoteCloseableImpl<RemoteFileOutput> openForWriting(File path) throws IOException {
    boolean permitted = mayWrite(path);
    System.out.println("Writing to " + path + "; permitted=" + permitted);
    if (!permitted) {
      throw new AccessDeniedException("Not permitted");
    }
    FileOutputStream fout = new FileOutputStream(path);
    return new RemoteCloseableImpl<>(socketFactory, new RemoteFileOutput(socketFactory, fout));
  }

  protected boolean mayRead(File path) {
    return true;
  }

  protected boolean mayWrite(File path) {
    return true;
  }
}
