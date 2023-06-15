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
package org.newsclub.net.unix;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RemoteServer;
import java.util.Arrays;
import java.util.UUID;

/**
 * AF_UNIX socket credentials.
 *
 * @see AFUNIXSocket#getPeerCredentials()
 */
public final class AFUNIXSocketCredentials implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Special instance, indicating that there is no remote peer, but the referenced object is from
   * the same process.
   */
  public static final AFUNIXSocketCredentials SAME_PROCESS = new AFUNIXSocketCredentials();

  /**
   * The PID, or -1 for "not set".
   */
  private long pid = -1; // NOPMD -- Set in native code

  /**
   * The UID, or -1 for "not set".
   */
  private long uid = -1; // NOPMD -- Set in native code

  /**
   * All GID values (or null for "not set"); the first being the primary one.
   */
  private long[] gids = null;

  /**
   * The UUID, or null for "not set".
   */
  private UUID uuid = null;

  AFUNIXSocketCredentials() {
  }

  /**
   * Returns the "pid" (process ID), or {@code -1} if it could not be retrieved.
   *
   * @return The pid, or -1.
   */
  public long getPid() {
    return pid;
  }

  /**
   * Returns the "uid" (user ID), or {@code -1} if it could not be retrieved.
   *
   * @return The uid, or -1.
   */
  public long getUid() {
    return uid;
  }

  /**
   * Returns the primary "gid" (group ID), or {@code -1} if it could not be retrieved.
   *
   * @return The gid, or -1.
   */
  public long getGid() {
    return gids == null ? -1 : gids.length == 0 ? -1 : gids[0];
  }

  /**
   * Returns all "gid" values (group IDs), or {@code null} if they could not be retrieved.
   *
   * Note that this list may be incomplete (only the primary gid may be returned), but it is
   * guaranteed that the first one in the list is the primary gid as returned by {@link #getGid()}.
   *
   * @return The gids, or null.
   */
  public long[] getGids() {
    return gids == null ? null : gids.clone();
  }

  /**
   * Returns the process' unique identifier, or {@code null} if no such identifier could be
   * retrieved. Note that all processes run by the same Java runtime may share the same UUID.
   *
   * @return The UUID, or null.
   */
  public UUID getUUID() {
    return uuid;
  }

  void setUUID(String uuidStr) {
    this.uuid = UUID.fromString(uuidStr);
  }

  void setGids(long[] gids) {
    this.gids = gids.clone();
  }

  /**
   * Checks if neither of the possible peer credentials are set.
   *
   * @return {@code true} if no credentials set.
   */
  public boolean isEmpty() {
    return pid == -1 && uid == -1 && (gids == null || gids.length == 0) && uuid == null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append('[');
    if (this == SAME_PROCESS) { // NOPMD: CompareObjectsWithEquals
      sb.append("(same process)]");
      return sb.toString();
    }
    if (pid != -1) {
      sb.append("pid=");
      sb.append(pid);
      sb.append(';');
    }
    if (uid != -1) {
      sb.append("uid=");
      sb.append(uid);
      sb.append(';');
    }
    if (gids != null) {
      sb.append("gids=");
      sb.append(Arrays.toString(gids));
      sb.append(';');
    }
    if (uuid != null) {
      sb.append("uuid=");
      sb.append(uuid);
      sb.append(';');
    }
    if (sb.charAt(sb.length() - 1) == ';') {
      sb.setLength(sb.length() - 1);
    }
    sb.append(']');
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(gids);
    result = prime * result + (int) (pid ^ (pid >>> 32));
    result = prime * result + (int) (uid ^ (uid >>> 32));
    result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
    return result;
  }

  @Override
  @SuppressWarnings("all")
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AFUNIXSocketCredentials other = (AFUNIXSocketCredentials) obj;
    if (!Arrays.equals(gids, other.gids))
      return false;
    if (pid != other.pid)
      return false;
    if (uid != other.uid)
      return false;
    if (uuid == null) {
      if (other.uuid != null)
        return false;
    } else if (!uuid.equals(other.uuid))
      return false;
    return true;
  }

  /**
   * Returns the {@link AFUNIXSocketCredentials} for the currently active remote session, or
   * {@code null} if it was not possible to retrieve these credentials.
   *
   * NOTE: For now, only RMI remote sessions are supported (RemoteServer sessions during a remote
   * method invocation).
   *
   * If you want to retrieve the peer credentials for an RMI server, see junixsocket-rmi's
   * RemotePeerInfo.
   *
   * @return The credentials, or {@code null} if unable to retrieve.
   */
  public static AFUNIXSocketCredentials remotePeerCredentials() {
    try {
      RemoteServer.getClientHost();
    } catch (Exception e) {
      return null;
    }

    Socket sock = NativeUnixSocket.currentRMISocket();
    if (!(sock instanceof AFUNIXSocket)) {
      return null;
    }
    AFUNIXSocket socket = (AFUNIXSocket) sock;

    try {
      return socket.getPeerCredentials();
    } catch (IOException e) {
      return null;
    }
  }
}
