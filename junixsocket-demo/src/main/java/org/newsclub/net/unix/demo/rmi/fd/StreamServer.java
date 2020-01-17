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

import org.newsclub.net.unix.rmi.AFUNIXNaming;

/**
 * Demonstrates how to read/write files via FileDescriptors that are exchanged via RMI.
 * 
 * This allows reading/writing from and to files that are otherwise not even accessible by the user.
 * For example, starting the {@link StreamServer} as root and the {@link StreamClient} as a
 * non-privileged user will allow the non-privileged user to read files only accessible to root.
 * 
 * NOTE: For obvious security reasons, running this server without modification is not advised for
 * anything other than demo purposes.
 * 
 * @author Christian Kohlschütter
 * @see StreamClient
 */
public class StreamServer {
  public static void main(String[] args) throws Exception {
    AFUNIXNaming naming = AFUNIXNaming.getInstance();
    System.out.println("Socket directory: " + naming.getSocketFactory().getSocketDir());

    try (StreamServiceImpl service = new StreamServiceImpl(naming.getSocketFactory())) {
      naming.exportAndBind("streamService", service);

      System.out.println("StreamServer ready; user.name=" + System.getProperty("user.name"));
    }
  }
}
