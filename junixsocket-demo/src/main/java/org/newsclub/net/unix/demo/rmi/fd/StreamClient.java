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
package org.newsclub.net.unix.demo.rmi.fd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.registry.Registry;

import org.newsclub.net.unix.demo.rmi.services.StreamService;
import org.newsclub.net.unix.rmi.AFNaming;
import org.newsclub.net.unix.rmi.AFUNIXNaming;
import org.newsclub.net.unix.rmi.RemoteCloseable;
import org.newsclub.net.unix.rmi.RemoteFileInput;

/**
 * Demonstrates how to read files via FileDescriptors that are exchanged via RMI.
 *
 * @author Christian Kohlschütter
 * @see StreamServer
 */
public final class StreamClient {
  private StreamClient() {
    throw new IllegalStateException("No instances");
  }

  /**
   * {@link StreamClient} command-line tool.
   *
   * @param args Command-line arguments.
   * @throws IOException on error.
   * @throws NotBoundException if the server cannot be reached.
   */
  public static void main(String[] args) throws IOException, NotBoundException {
    if (args.length != 1) {
      System.err.println("Usage: StreamClient <path-to-file>");
      System.exit(1); // NOPMD
      return;
    } else if ("--help".equals(args[0]) || "-h".equals(args[0])) {
      System.out.println("Usage: StreamClient <path-to-file>");
      System.out.println();
      System.out.println("Examples:");
      System.out.println("    StreamClient /etc/hosts");
      System.out.println("    StreamClient /etc/master.passwd");
    }

    AFNaming naming = AFUNIXNaming.getInstance();

    final Registry registry = naming.getRegistry();
    StreamService obj = (StreamService) registry.lookup("streamService");

    File file = new File(args[0]);
    System.out.println("Trying to read " + file);

    try (RemoteCloseable<RemoteFileInput> rc = obj.openForReading(file)) {
      try (RemoteFileInput rfin = rc.get(); FileInputStream fin = rfin.asFileInputStream()) {
        byte[] data = new byte[4096];

        int len;
        while ((len = fin.read(data)) != -1) {
          System.out.write(data, 0, len);
        }
      }
    }
  }
}
