/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
import java.io.IOException;
import java.io.ObjectOutput;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteObjectInvocationHandler;
import java.rmi.server.UnicastRemoteObject;

/**
 * Helper class for RMI remote objects.
 * 
 * @author Christian Kohlschütter
 */
public final class RemoteObjectUtil {
  private RemoteObjectUtil() {
    throw new UnsupportedOperationException("No instances");
  }

  /**
   * Exports the given Remote object, using the given socket factory and a randomly assigned port.
   * 
   * @param obj The object to export.
   * @param socketFactory The socket factory to use.
   * @return The remote stub.
   * @throws RemoteException if the operation fails.
   */
  public static Remote exportObject(Remote obj, RMISocketFactory socketFactory)
      throws RemoteException {
    return UnicastRemoteObject.exportObject(obj, 0, socketFactory, socketFactory);
  }

  /**
   * Exports and binds the given Remote object to the given name, using the given
   * {@link AFUNIXNaming} setup.
   * 
   * @param naming The {@link AFUNIXNaming} instance to use.
   * @param name The name to use to bind the object in the registry.
   * @param obj The object to export and bind.
   * @throws RemoteException if the operation fails.
   * @throws AlreadyBoundException if there already was something bound at that name
   */
  public static void exportAndBind(AFUNIXNaming naming, String name, Remote obj)
      throws RemoteException, AlreadyBoundException {
    RemoteObjectUtil.exportObject(obj, naming.getSocketFactory());

    naming.getRegistry().bind(name, obj);
  }

  /**
   * Exports and re-binds the given Remote object to the given name, using the given
   * {@link AFUNIXNaming} setup.
   * 
   * @param naming The {@link AFUNIXNaming} instance to use.
   * @param name The name to use to bind the object in the registry.
   * @param obj The object to export and bind.
   * @throws RemoteException if the operation fails.
   */
  public static void exportAndRebind(AFUNIXNaming naming, String name, Remote obj)
      throws RemoteException {
    RemoteObjectUtil.exportObject(obj, naming.getSocketFactory());

    naming.getRegistry().rebind(name, obj);
  }

  /**
   * Forcibly un-exports the given object, if it exists (otherwise returns without an error). This
   * should be called upon closing a {@link Closeable} {@link Remote} object.
   * 
   * @param obj The object to un-export.
   */
  public static void unexportObject(Remote obj) {
    try {
      UnicastRemoteObject.unexportObject(obj, true);
    } catch (NoSuchObjectException e) {
      // ignore
    }
  }

  /**
   * Forcibly un-exports the given object, if it exists, and unbinds the object from the registry
   * (otherwise returns without an error).
   * 
   * @param obj The object to un-export.
   */
  public static void unexportAndUnbind(AFUNIXNaming naming, String name, Remote obj)
      throws RemoteException {
    unexportObject(obj);
    try {
      naming.unbind(name);
    } catch (MalformedURLException | NotBoundException e) {
      // ignore
    }
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
  public static RemoteConnectionInfo getConnectionInfo(Remote obj) throws IOException {
    try (ExtractingObjectOutput eoo = new ExtractingObjectOutput()) {
      RemoteObjectInvocationHandler roih = (RemoteObjectInvocationHandler) Proxy
          .getInvocationHandler(RemoteObject.toStub(obj));

      roih.getRef().writeExternal(eoo);
      if (!eoo.validate()) {
        throw new IOException("Unexpected data format for " + obj.getClass());
      }

      return eoo.data;
    }
  }

  public static final class RemoteConnectionInfo {
    private RMISocketFactory socketFactory;
    private String host;
    private int port;

    public RMISocketFactory getSocketFactory() {
      return socketFactory;
    }

    public String getHost() {
      return host;
    }

    public int getPort() {
      return port;
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

    private final RemoteConnectionInfo data = new RemoteConnectionInfo();

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
}
