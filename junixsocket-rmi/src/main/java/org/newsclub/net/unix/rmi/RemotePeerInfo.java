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
import java.io.ObjectOutput;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteObjectInvocationHandler;

import org.newsclub.net.unix.AFUNIXSocketCredentials;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Information about the remote connection.
 *
 * @author Christian Kohlschütter
 */
public final class RemotePeerInfo {
  private RMISocketFactory socketFactory;
  String host;
  int port;
  private AFUNIXSocketCredentials peerCredentials;

  RemotePeerInfo() {
  }

  /**
   * The socket factory used to establish connections.
   *
   * @return The socket factory.
   */
  public RMISocketFactory getSocketFactory() {
    return socketFactory;
  }

  /**
   * The hostname.
   *
   * @return The hostname
   */
  public String getHost() {
    return host;
  }

  /**
   * The port.
   *
   * @return The port
   */
  public int getPort() {
    return port;
  }

  /**
   * The remote socket credentials, or {@code null} if they could not be retrieved.
   *
   * @return The peer credentials, or {@code null}.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public AFUNIXSocketCredentials getPeerCredentials() {
    return peerCredentials;
  }

  /**
   * Returns the {@link AFUNIXSocketCredentials} for the currently active remote session
   * (RemoteServer session during a remote method invocation), or {@code null} if it was not
   * possible to retrieve these credentials.
   *
   * @return The credentials, or {@code null} if unable to retrieve.
   */
  public static AFUNIXSocketCredentials remotePeerCredentials() {
    return AFUNIXSocketCredentials.remotePeerCredentials();
  }

  /**
   * Returns the {@link AFUNIXSocketCredentials} for the peer (server) of the given {@link Remote}
   * instance, or {@code null} if it was not possible to retrieve these credentials.
   *
   * @param obj The remote object.
   * @return The credentials, or {@code null} if unable to retrieve.
   * @throws IOException if an exception occurs.
   */
  public static AFUNIXSocketCredentials remotePeerCredentials(Remote obj) throws IOException {
    return getConnectionInfo(obj).getPeerCredentials();
  }

  /**
   * Returns the connection information ({@link RMISocketFactory}, hostname and port) used for the
   * given {@link Remote} object, or {@code null} if no custom {@link RMISocketFactory} was
   * specified.
   *
   * An {@link IOException} may be thrown if we couldn't determine the socket factory.
   *
   * @param obj The remote object.
   * @return The factory, or {@code null}
   * @throws IOException if the operation fails.
   */
  public static RemotePeerInfo getConnectionInfo(Remote obj) throws IOException {
    try (RemotePeerInfo.ExtractingObjectOutput eoo = new RemotePeerInfo.ExtractingObjectOutput()) {
      RemoteObjectInvocationHandler roih = (RemoteObjectInvocationHandler) Proxy
          .getInvocationHandler(RemoteObject.toStub(obj));

      roih.getRef().writeExternal(eoo);
      if (!eoo.validate()) {
        throw new IOException("Unexpected data format for " + obj.getClass());
      }

      RemotePeerInfo data = eoo.data;
      if ((data.socketFactory) instanceof AFUNIXRMISocketFactory) {
        AFUNIXRMISocketFactory sf = ((AFUNIXRMISocketFactory) (data.socketFactory));
        data.peerCredentials = sf.peerCredentialsFor(data);

        if (data.peerCredentials == null) {
          if (sf.isLocalServer(data.port)) {
            data.peerCredentials = AFUNIXSocketCredentials.SAME_PROCESS;
          }
        }
      } else {
        data.peerCredentials = null;
      }

      return data;
    }
  }

  /**
   * Mimics a Serializer for RemoteRef.writeExternal, so we can extract the information we need
   * about the remote reference without having to break-open internal Java classes.
   *
   * NOTE: The format for the data we extract is assumed to be stable across JVM implementations,
   * otherwise RMI would probably not work.
   *
   * @author Christian Kohlschütter
   */
  static final class ExtractingObjectOutput implements ObjectOutput {
    private int callId = 0;
    private boolean done = false;
    private boolean invalid = false;
    private int format = -1;

    final RemotePeerInfo data = new RemotePeerInfo();

    public ExtractingObjectOutput() {
    }

    private void setInvalid() {
      invalid = done = true;
    }

    private void call(int id, Object v) {
      switch (id) {
        case 1:
          if (v instanceof Integer) {
            // see sun.rmi.transport.tcp.TCPEndpoint
            switch ((int) v) {
              case 0:
                // FORMAT_HOST_PORT (= no socket factory)
                format = 0;
                return;
              case 1:
                // FORMAT_HOST_PORT_FACTORY
                format = 1;
                return;
              default:
            }
          }
          break;
        case 2:
          if (v instanceof String) {
            this.data.host = (String) v;
            return;
          }
          break;
        case 3:
          if (format == 0) {
            done = true;
          }
          if (v instanceof Integer) {
            this.data.port = (int) v;
            if (this.data.port <= 0) {
              setInvalid();
            }
            return;
          }
          break;
        case 4:
          if (v instanceof RMISocketFactory && format == 1) {
            this.data.socketFactory = (RMISocketFactory) v;
            return;
          }
          break;
        default:
          // no need to read any further
          done = true;
          return;
      }

      // otherwise:
      setInvalid();
    }

    @Override
    public void writeBoolean(boolean v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeByte(int v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeShort(int v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeChar(int v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeInt(int v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeLong(long v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeFloat(float v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeDouble(double v) {
      if (done) {
        return;
      }
      call(++callId, v);
    }

    @Override
    public void writeBytes(String s) {
      if (done) {
        return;
      }
      call(++callId, s);
    }

    @Override
    public void writeChars(String s) {
      if (done) {
        return;
      }
      call(++callId, s);
    }

    @Override
    public void writeUTF(String s) {
      if (done) {
        return;
      }
      call(++callId, s);
    }

    @Override
    public void writeObject(Object obj) {
      if (done) {
        return;
      }
      call(++callId, obj);
    }

    @Override
    public void write(int b) {
      if (done) {
        return;
      }
      call(++callId, b);
    }

    @Override
    public void write(byte[] b) {
      if (done) {
        return;
      }
      call(++callId, b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      if (done) {
        return;
      }
      setInvalid();
      // NOTE: not yet needed
      // byte[] copy = Arrays.copyOfRange(b, off, off + len);
      // call(++callId, copy);
    }

    @Override
    public void flush() {
    }

    public boolean validate() {
      this.done = true;
      if (callId < 3) {
        setInvalid();
      }
      return !invalid;
    }

    @Override
    public void close() {
    }
  }

  @Override
  public String toString() {
    return getClass().getName() + "[" + host + ":" + port + ";socketFactory=" + socketFactory
        + ";peerCredentials=" + peerCredentials + "]";
  }
}
