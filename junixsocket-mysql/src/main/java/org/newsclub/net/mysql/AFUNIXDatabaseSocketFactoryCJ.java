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
package org.newsclub.net.mysql;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.mysql.cj.conf.PropertySet;
import com.mysql.cj.conf.RuntimeProperty;
import com.mysql.cj.protocol.ExportControlled;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.protocol.SocketConnection;
import com.mysql.cj.protocol.SocketFactory;

/**
 * Connect to mysql databases (and compatibles) using UNIX domain sockets.
 */
public class AFUNIXDatabaseSocketFactoryCJ implements SocketFactory {
  private AFUNIXSocket rawSocket;

  /**
   * Creates a new instance.
   */
  public AFUNIXDatabaseSocketFactoryCJ() {
  }

  @SuppressWarnings({"unchecked"})
  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public <T extends Closeable> T connect(String hostname, int portNumber, PropertySet props,
      int loginTimeout) throws IOException {
    // Adjust the path to your MySQL socket by setting the
    // "junixsocket.file" property
    // If no socket path is given, use the default: /tmp/mysql.sock
    RuntimeProperty<String> prop = props.getStringProperty("junixsocket.file");
    String sock;
    if (prop != null && !prop.isExplicitlySet()) {
      sock = prop.getStringValue();
    } else {
      sock = "/tmp/mysql.sock";
    }
    final File socketFile = new File(sock);

    this.rawSocket = AFUNIXSocket.connectTo(AFUNIXSocketAddress.of(socketFile));
    return (T) rawSocket;
  }

  @SuppressWarnings({"unchecked"})
  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public <T extends Closeable> T performTlsHandshake(SocketConnection socketConnection,
      ServerSession serverSession) throws IOException {
    return (T) ExportControlled.performTlsHandshake(this.rawSocket, socketConnection,
        serverSession == null ? null : serverSession.getServerVersion(), null);
  }
}
