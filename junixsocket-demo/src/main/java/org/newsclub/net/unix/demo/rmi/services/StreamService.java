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
package org.newsclub.net.unix.demo.rmi.services;

import java.io.File;
import java.io.IOException;
import java.rmi.Remote;

import org.newsclub.net.unix.demo.rmi.fd.StreamServer;
import org.newsclub.net.unix.rmi.RemoteCloseable;
import org.newsclub.net.unix.rmi.RemoteFileInput;
import org.newsclub.net.unix.rmi.RemoteFileOutput;

/**
 * The {@link StreamServer}'s RMI service.
 *
 * @author Christian Kohlschütter
 * @see StreamServer
 */
public interface StreamService extends Remote {
  /**
   * Opens the given file for reading.
   *
   * @param path The file to open.
   * @return A remote instance for the file.
   * @throws IOException on error.
   */
  RemoteCloseable<RemoteFileInput> openForReading(File path) throws IOException;

  /**
   * Opens the given file for writing.
   *
   * @param path The file to open.
   * @return A remote instance for the file.
   * @throws IOException on error.
   */
  RemoteCloseable<RemoteFileOutput> openForWriting(File path) throws IOException;
}
