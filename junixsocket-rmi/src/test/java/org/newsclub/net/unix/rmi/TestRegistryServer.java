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
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.util.SystemPropertyUtil;

/**
 * A simple RMI Registry that is launched as forked Java VM from unit tests such as
 * {@link RemoteRegistryTest}.
 *
 * Important: The server will terminate ca. 10 seconds after starting.
 *
 * @author Christian Kohlschütter
 */
public class TestRegistryServer {
  public static void main(String[] args) throws AlreadyBoundException, IOException,
      InterruptedException {
    AFUNIXNaming naming;
    String socketDirStr = System.getProperty("rmitest.junixsocket.socket-dir", "");
    if (socketDirStr.isEmpty()) {
      naming = AFUNIXNaming.getInstance();
    } else {
      File socketDir = new File(socketDirStr);
      if (!socketDir.mkdirs() && !socketDir.exists()) {
        throw new IOException("Could not create socketDir: " + socketDir);
      }
      naming = AFUNIXNaming.getInstance(socketDir);
    }

    naming.setRemoteShutdownAllowed(SystemPropertyUtil.getBooleanSystemProperty(
        "rmitest.junixsocket.remote-shutdown.allowed", true));

    File socketDir = naming.getRegistrySocketDir();
    System.out.println(socketDir);

    if (SystemPropertyUtil.getBooleanSystemProperty("rmitest.junixsocket.create-registry", true)) {
      naming.createRegistry();
    }

    if (SystemPropertyUtil.getBooleanSystemProperty("rmitest.junixsocket.export-hello", true)) {
      int delay = SystemPropertyUtil.getIntSystemProperty("rmitest.junixsocket.export-hello.delay",
          0);
      if (delay > 0) {
        System.out.println("Delaying Hello export by " + delay + " ms");
        try {
          Thread.sleep(delay);
        } catch (InterruptedException e) {
          // ignore
        }
      }
      naming.exportAndBind("hello", new HelloImpl());
    }

    System.out.println("Server is ready");

    int shutdownAfter = SystemPropertyUtil.getIntSystemProperty(
        "rmitest.junixsocket.shutdown-after.secs", -1);
    if (shutdownAfter >= 0) {
      Thread t = new Thread() {
        @SuppressFBWarnings({"DM_EXIT"})
        @Override
        public void run() {
          try {
            Thread.sleep(shutdownAfter * 1000L);
          } catch (InterruptedException e) {
            // ignored
          }
          try {
            naming.shutdownRegistry();
          } catch (RemoteException e) {
            e.printStackTrace();
          }

          System.exit(0); // NOPMD
        }
      };
      t.setDaemon(true);
      t.start();
    }
  }
}
