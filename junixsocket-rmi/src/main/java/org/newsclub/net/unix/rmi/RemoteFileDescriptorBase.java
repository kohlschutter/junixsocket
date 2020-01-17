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

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.server.AFUNIXSocketServer;

/**
 * A wrapper that allows a {@link FileDescriptor} be sent via RMI over AF_UNIX sockets.
 * 
 * @author Christian Kohlschütter
 * @see RemoteFileInput subclass for sending {@link FileInputStream}s.
 * @see RemoteFileOutput subclass for sending {@link FileOutputStream}s.
 */
public abstract class RemoteFileDescriptorBase<T> implements Externalizable, Closeable {
  private static final String PROP_SERVER_TIMEOUT =
      "org.newsclub.net.unix.rmi.rfd-server-timeout-millis";
  private static final String PROP_CONNECT_TIMEOUT =
      "org.newsclub.net.unix.rmi.rfd-connect-timeout-millis";

  private static final int SERVER_TIMEOUT = //
      parseTimeoutMillis(System.getProperty(PROP_SERVER_TIMEOUT, "10000"), false);
  private static final int CONNECT_TIMEOUT = //
      parseTimeoutMillis(System.getProperty(PROP_CONNECT_TIMEOUT, "1000"), true);

  protected static final int MAGIC_VALUE_MASK = 0x00FD0000;
  protected static final int BIT_READABLE = 1 << 0;
  protected static final int BIT_WRITABLE = 1 << 1;

  private static final long serialVersionUID = 1L;
  private static final Random RANDOM = new Random();

  /**
   * An optional, closeable resource that is related to this instance. If non-null, this will be
   * closed upon {@link #close()}.
   * 
   * For unidirectional implementations, this could be the corresponding input/output stream. For
   * bidirectional implementations (e.g., a Socket, Pipe, etc.), this should close both directions.
   */
  protected transient T resource;

  private int magicValue;
  private FileDescriptor fd;
  private AFUNIXRMISocketFactory socketFactory;

  /**
   * Creates an uninitialized instance; used for externalization.
   * 
   * @see #readExternal(ObjectInput)
   */
  protected RemoteFileDescriptorBase() {
  }

  RemoteFileDescriptorBase(AFUNIXRMISocketFactory socketFactory, T stream, FileDescriptor fd,
      int magicValue) {
    this.resource = stream;
    this.socketFactory = socketFactory;
    this.fd = fd;
    this.magicValue = magicValue;
  }

  @Override
  public final void writeExternal(ObjectOutput objOut) throws IOException {
    if (fd == null || !fd.valid()) {
      throw new IOException("No or invalid file descriptor");
    }
    final int randomValue = RANDOM.nextInt();

    int localPort = 0;
    try {
      @SuppressWarnings("resource")
      AFUNIXServerSocket serverSocket = (AFUNIXServerSocket) socketFactory.createServerSocket(0);
      localPort = serverSocket.getLocalPort();
      AFUNIXSocketServer server = new AFUNIXSocketServer(serverSocket) {

        @Override
        protected void doServeSocket(Socket socket) throws IOException {
          AFUNIXSocket unixSocket = (AFUNIXSocket) socket;
          try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            unixSocket.setOutboundFileDescriptors(fd);
            out.writeInt(randomValue);
          }
          stop();
        }

        @Override
        protected void onServerStopped(java.net.ServerSocket socket) {
          try {
            serverSocket.close();
          } catch (IOException e) {
            // ignore
          }
        }
      };
      server.stopAfter(SERVER_TIMEOUT, TimeUnit.MILLISECONDS);
      if (!server.startAndWait(SERVER_TIMEOUT, TimeUnit.MILLISECONDS)) {
        server.stop();
        serverSocket.close();
        throw new SocketTimeoutException("Timeout setting up FileDescriptor server");
      }
    } catch (InterruptedException e) {
      InterruptedIOException ioe = (InterruptedIOException) new InterruptedIOException().initCause(
          e);
      objOut.writeObject(ioe);
      throw ioe;
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

  @Override
  public final void readExternal(ObjectInput objIn) throws IOException, ClassNotFoundException {
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
    FileDescriptor[] descriptors;
    try (AFUNIXSocket socket = (AFUNIXSocket) socketFactory.createSocket("", port)) {
      try {
        socket.setSoTimeout(CONNECT_TIMEOUT);
      } catch (IOException e) { // NOPMD
        // ignore
        // FIXME: spurious IOExceptions ("socket closed) on Solaris only; ignoring them for now
      }
      try (DataInputStream in1 = new DataInputStream(socket.getInputStream())) {
        socket.ensureAncillaryReceiveBufferSize(128);

        int random = in1.readInt();

        if (random != randomValue) {
          throw new IOException("Invalid socket connection");
        }
        descriptors = socket.getReceivedFileDescriptors();
      }
    }
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

  @Override
  public synchronized void close() throws IOException {
    T c = this.resource;
    if (c != null) {
      this.resource = null;
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
