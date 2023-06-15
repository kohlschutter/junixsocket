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

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.newsclub.net.unix.AFServerSocket;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.FileDescriptorAccess;
import org.newsclub.net.unix.server.AFSocketServer;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A wrapper that allows a {@link FileDescriptor} be sent via RMI over AF_UNIX sockets.
 *
 * @author Christian Kohlschütter
 * @param <T> The resource type.
 * @see RemoteFileInput
 * @see RemoteFileOutput
 */
public abstract class RemoteFileDescriptorBase<T> implements Externalizable, Closeable,
    FileDescriptorAccess {
  private static final String PROP_SERVER_TIMEOUT =
      "org.newsclub.net.unix.rmi.rfd-server-timeout-millis";
  private static final String PROP_CONNECT_TIMEOUT =
      "org.newsclub.net.unix.rmi.rfd-connect-timeout-millis";

  private static final int SERVER_TIMEOUT = //
      parseTimeoutMillis(System.getProperty(PROP_SERVER_TIMEOUT, "10000"), false);
  private static final int CONNECT_TIMEOUT = //
      parseTimeoutMillis(System.getProperty(PROP_CONNECT_TIMEOUT, "1000"), true);

  static final int MAGIC_VALUE_MASK = 0x00FD0000;
  static final int BIT_READABLE = 1 << 0;
  static final int BIT_WRITABLE = 1 << 1;

  private static final long serialVersionUID = 1L;

  private final AtomicReference<DataInputStream> remoteConnection = new AtomicReference<>();
  private final AtomicReference<AFUNIXSocket> remoteServer = new AtomicReference<>();

  /**
   * An optional, closeable resource that is related to this instance. If the reference is non-null,
   * this will be closed upon {@link #close()}.
   *
   * For unidirectional implementations, this could be the corresponding input/output stream. For
   * bidirectional implementations (e.g., a Socket, Pipe, etc.), this should close both directions.
   */
  protected final transient AtomicReference<T> resource = new AtomicReference<>();

  private int magicValue;
  private transient FileDescriptor fd;
  private AFUNIXRMISocketFactory socketFactory;

  /**
   * Creates an uninitialized instance; used for externalization.
   *
   * @see #readExternal(ObjectInput)
   */
  public RemoteFileDescriptorBase() {
  }

  RemoteFileDescriptorBase(AFUNIXRMISocketFactory socketFactory, T stream, FileDescriptor fd,
      int magicValue) {
    this.resource.set(stream);
    this.socketFactory = socketFactory;
    this.fd = fd;
    this.magicValue = magicValue;
  }

  @Override
  @SuppressWarnings("PMD.ExceptionAsFlowControl")
  public final void writeExternal(ObjectOutput objOut) throws IOException {
    if (fd == null || !fd.valid()) {
      throw new IOException("No or invalid file descriptor");
    }
    final int randomValue = ThreadLocalRandom.current().nextInt();

    int localPort;
    try {
      AFServerSocket<?> serverSocket = (AFServerSocket<?>) socketFactory.createServerSocket(0);
      localPort = serverSocket.getLocalPort();

      AFSocketServer<?> server = new AFSocketServer<AFSocketAddress>(serverSocket) {
        @Override
        protected void doServeSocket(AFSocket<?> socket) throws IOException {
          AFUNIXSocket unixSocket = (AFUNIXSocket) socket;
          try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
              InputStream in = socket.getInputStream();) {
            unixSocket.setOutboundFileDescriptors(fd);
            out.writeInt(randomValue);

            try {
              socket.setSoTimeout(CONNECT_TIMEOUT);
            } catch (IOException e) {
              // ignore
            }

            // This call blocks until the remote is done with the file descriptor, or we time out.
            int response = in.read();
            if (response != 1) {
              if (response == -1) {
                // EOF, remote terminated
              } else {
                throw new IOException("Unexpected response: " + response);
              }
            }
          } finally {
            stop();
          }
        }

        @Override
        protected void onServerStopped(AFServerSocket<?> socket) {
          try {
            serverSocket.close();
          } catch (IOException e) {
            // ignore
          }
        }

      };
      server.startThenStopAfter(SERVER_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (IOException e) {
      objOut.writeObject(e);
      throw e;
    }

    objOut.writeObject(socketFactory);
    objOut.writeInt(magicValue);
    objOut.writeInt(randomValue);
    objOut.writeInt(localPort);
    objOut.flush();
  }

  @SuppressWarnings("resource")
  @Override
  public final void readExternal(ObjectInput objIn) throws IOException, ClassNotFoundException {
    DataInputStream in1 = remoteConnection.getAndSet(null);
    if (in1 != null) {
      in1.close();
    }

    Object obj = objIn.readObject();
    if (obj instanceof IOException) {
      IOException e = new IOException("Could not read RemoteFileDescriptor");
      e.addSuppressed((IOException) obj);
      throw e;
    }
    this.socketFactory = (AFUNIXRMISocketFactory) obj;

    // Since ancillary messages can only be read in combination with real data, we read and verify a
    // magic value
    this.magicValue = objIn.readInt();
    if ((magicValue & MAGIC_VALUE_MASK) != MAGIC_VALUE_MASK) {
      throw new IOException("Unexpected magic value: " + Integer.toHexString(magicValue));
    }
    final int randomValue = objIn.readInt();
    int port = objIn.readInt();

    AFUNIXSocket socket = (AFUNIXSocket) socketFactory.createSocket("", port);
    if (remoteServer.getAndSet(socket) != null) {
      throw new IllegalStateException("remoteServer was not null");
    }

    try {
      socket.setSoTimeout(CONNECT_TIMEOUT);
    } catch (IOException e) {
      // ignore
    }

    in1 = new DataInputStream(socket.getInputStream());
    this.remoteConnection.set(in1);
    socket.ensureAncillaryReceiveBufferSize(128);

    int random = in1.readInt();

    if (random != randomValue) {
      throw new IOException("Invalid socket connection");
    }
    FileDescriptor[] descriptors = socket.getReceivedFileDescriptors();

    if (descriptors == null || descriptors.length != 1) {
      throw new IOException("Did not receive exactly 1 file descriptor but " + (descriptors == null
          ? 0 : descriptors.length));
    }

    this.fd = descriptors[0];
  }

  /**
   * Returns the file descriptor.
   *
   * This is either the original one that was specified in the constructor or a copy that was sent
   * via RMI over an AF_UNIX connection as part of an ancillary message.
   *
   * @return The file descriptor.
   */
  @Override
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public final FileDescriptor getFileDescriptor() {
    return fd;
  }

  /**
   * Returns the "magic value" for this type of file descriptor.
   *
   * The magic value consists of an indicator ("this is a file descriptor") as well as its
   * capabilities (read/write). It is used to prevent, for example, converting an output stream to
   * an input stream.
   *
   * @return The magic value.
   */
  protected final int getMagicValue() {
    return magicValue;
  }

  @SuppressWarnings("resource")
  @Override
  public void close() throws IOException {
    DataInputStream in1 = remoteConnection.getAndSet(null);
    if (in1 != null) {
      try {
        in1.close();
      } catch (SocketException e) {
        // ignore
      }
    }

    AFUNIXSocket remoteSocket = remoteServer.getAndSet(null);
    if (remoteSocket != null) {
      try (OutputStream out = remoteSocket.getOutputStream()) {
        out.write(1);
      } catch (SocketException e) {
        // ignore
      }
      remoteSocket.close();
    }

    @SuppressWarnings("null")
    T c = this.resource.getAndSet(null);
    if (c != null) {
      if (c instanceof Closeable) {
        ((Closeable) c).close();
      }
    }
  }

  private static int parseTimeoutMillis(String s, boolean zeroPermitted) {
    Objects.requireNonNull(s);
    int duration;
    try {
      duration = Integer.parseInt(s);
    } catch (Exception e) {
      throw new IllegalArgumentException("Illegal timeout value: " + s, e);
    }
    if (duration < 0 || (duration == 0 && !zeroPermitted)) {
      throw new IllegalArgumentException("Illegal timeout value: " + s);
    }
    return duration;
  }
}
