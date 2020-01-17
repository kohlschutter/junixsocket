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

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;

/**
 * A reference to a AFUNIXNaming instance.
 * 
 * @author Christian Kohlschütter
 */
final class AFUNIXNamingRef {
  final File socketDir;
  private final int port;
  private final String socketPrefix;
  private final String socketSuffix;

  AFUNIXNamingRef(File socketDir, int port, String socketPrefix, String socketSuffix)
      throws RemoteException {
    try {
      this.socketDir = socketDir.getCanonicalFile();
    } catch (IOException e) {
      throw new RemoteException(e.getMessage(), e);
    }
    this.port = port;
    this.socketPrefix = socketPrefix == null ? AFUNIXRMISocketFactory.DEFAULT_SOCKET_FILE_PREFIX
        : socketPrefix;
    this.socketSuffix = socketSuffix == null ? AFUNIXRMISocketFactory.DEFAULT_SOCKET_FILE_SUFFIX
        : socketSuffix;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + port;
    result = prime * result + ((socketDir == null) ? 0 : socketDir.hashCode());
    result = prime * result + ((socketPrefix == null) ? 0 : socketPrefix.hashCode());
    result = prime * result + ((socketSuffix == null) ? 0 : socketSuffix.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AFUNIXNamingRef)) {
      return false;
    }
    AFUNIXNamingRef other = (AFUNIXNamingRef) obj;
    if (port != other.port) {
      return false;
    }
    if (socketDir == null) {
      if (other.socketDir != null) {
        return false;
      }
    } else if (!socketDir.equals(other.socketDir)) {
      return false;
    }
    if (socketPrefix == null) {
      if (other.socketPrefix != null) {
        return false;
      }
    } else if (!socketPrefix.equals(other.socketPrefix)) {
      return false;
    }
    if (socketSuffix == null) {
      if (other.socketSuffix != null) {
        return false;
      }
    } else if (!socketSuffix.equals(other.socketSuffix)) {
      return false;
    }
    return true;
  }
}