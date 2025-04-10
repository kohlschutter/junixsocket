/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Implementation of an AF_UNIX domain socket.
 *
 * @author Christian Kohlschütter
 */
public final class AFUNIXSocket extends AFSocket<AFUNIXSocketAddress> implements
    AFUNIXSocketExtensions {
  private static final Constructor<AFUNIXSocketAddress> CONSTRUCTOR_STRICT =
      new Constructor<AFUNIXSocketAddress>() {

        @Override
        public @NonNull AFSocket<AFUNIXSocketAddress> newInstance(FileDescriptor fdObj,
            AFSocketFactory<AFUNIXSocketAddress> factory) throws SocketException {
          return new AFUNIXSocket(new AFUNIXSocketImpl(fdObj), factory); // NOPMD
        }
      };

  private AFUNIXSocket(AFSocketImpl<AFUNIXSocketAddress> impl,
      AFSocketFactory<AFUNIXSocketAddress> factory) throws SocketException {
    super(impl, factory);
  }

  AFUNIXSocket(FileDescriptor fd, AFSocketFactory<AFUNIXSocketAddress> factory)
      throws SocketException {
    this(new AFUNIXSocketImpl.Lenient(fd), factory);
  }

  @Override
  protected AFUNIXSocketChannel newChannel() {
    return new AFUNIXSocketChannel(this);
  }

  /**
   * Creates a new, unbound {@link AFSocket}.
   *
   * This "default" implementation is a bit "lenient" with respect to the specification.
   *
   * In particular, we ignore calls to {@link Socket#getTcpNoDelay()} and
   * {@link Socket#setTcpNoDelay(boolean)}.
   *
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket newInstance() throws IOException {
    return (AFUNIXSocket) AFSocket.newInstance(AFUNIXSocket::new, (AFUNIXSocketFactory) null);
  }

  static AFUNIXSocket newLenientInstance() throws IOException {
    return newInstance();
  }

  static AFUNIXSocket newInstance(FileDescriptor fdObj, int localPort, int remotePort)
      throws IOException {
    return (AFUNIXSocket) AFSocket.newInstance(AFUNIXSocket::new, (AFUNIXSocketFactory) null, fdObj,
        localPort, remotePort);
  }

  static AFUNIXSocket newInstance(AFUNIXSocketFactory factory) throws SocketException {
    return (AFUNIXSocket) AFSocket.newInstance(AFUNIXSocket::new, factory);
  }

  /**
   * Creates a new, unbound, "strict" {@link AFSocket}.
   *
   * This call uses an implementation that tries to be closer to the specification than
   * {@link #newInstance()}, at least for some cases.
   *
   * @return A new, unbound socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket newStrictInstance() throws IOException {
    return (AFUNIXSocket) AFSocket.newInstance(CONSTRUCTOR_STRICT, (AFUNIXSocketFactory) null);
  }

  /**
   * Creates a new {@link AFSocket} and connects it to the given {@link AFUNIXSocketAddress}.
   *
   * @param addr The address to connect to.
   * @return A new, connected socket.
   * @throws IOException if the operation fails.
   */
  public static AFUNIXSocket connectTo(AFUNIXSocketAddress addr) throws IOException {
    return (AFUNIXSocket) AFSocket.connectTo(AFUNIXSocket::new, addr);
  }

  @Override
  public AFUNIXSocketChannel getChannel() {
    return (AFUNIXSocketChannel) super.getChannel();
  }

  @Override
  public AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    if (isClosed() || !isConnected()) {
      throw new SocketException("Not connected");
    }
    return ((AFUNIXSocketImpl) getAFImpl()).getPeerCredentials();
  }

  @Override
  public FileDescriptor[] getReceivedFileDescriptors() throws IOException {
    return ((AFUNIXSocketImpl) getAFImpl()).getReceivedFileDescriptors();
  }

  @Override
  public void clearReceivedFileDescriptors() {
    ((AFUNIXSocketImpl) getAFImpl()).clearReceivedFileDescriptors();
  }

  @Override
  public void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    if (fdescs != null && fdescs.length > 0 && !isConnected()) {
      throw new SocketException("Not connected");
    }
    ((AFUNIXSocketImpl) getAFImpl()).setOutboundFileDescriptors(fdescs);
  }

  @Override
  public boolean hasOutboundFileDescriptors() {
    return ((AFUNIXSocketImpl) getAFImpl()).hasOutboundFileDescriptors();
  }

  /**
   * Returns <code>true</code> iff {@link AFUNIXSocket}s are supported by the current Java VM.
   *
   * To support {@link AFSocket}s, a custom JNI library must be loaded that is supplied with
   * <em>junixsocket</em>, and the system must support AF_UNIX sockets.
   *
   * This call is equivalent to checking {@link AFSocket#isSupported()} and
   * {@link AFSocket#supports(AFSocketCapability)} with
   * {@link AFSocketCapability#CAPABILITY_UNIX_DOMAIN}.
   *
   * @return {@code true} iff supported.
   */
  @SuppressFBWarnings("HSM_HIDING_METHOD")
  public static boolean isSupported() {
    return AFSocket.isSupported() && AFSocket.supports(AFSocketCapability.CAPABILITY_UNIX_DOMAIN);
  }

  /**
   * Very basic self-test function.
   *
   * Prints "supported" and "capabilities" status to System.out.
   *
   * @param args ignored.
   */
  public static void main(String[] args) {
    // If you want to run this directly from within Eclipse, see
    // org.newsclub.net.unix.domain.SocketTest#testMain.
    System.out.print(AFUNIXSocket.class.getName() + ".isSupported(): ");
    System.out.flush();
    System.out.println(AFUNIXSocket.isSupported());

    for (AFSocketCapability cap : AFSocketCapability.values()) {
      System.out.print(cap + ": ");
      System.out.flush();
      System.out.println(AFSocket.supports(cap));
    }
    System.out.println();
    if (AFSocket.supports(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)) {
      System.out.println("Starting mini selftest...");
      miniSelftest();
    } else {
      System.out.println(
          "Skipping mini selftest; AFSocketCapability.CAPABILITY_UNIX_DOMAIN is missing");
    }
  }

  private static void miniSelftest() {
    AtomicBoolean success = new AtomicBoolean(true);
    try {
      AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();
      System.out.println("Using temporary address: " + addr);
      try (AFUNIXServerSocket server = addr.newBoundServerSocket()) {
        Thread t = new Thread(() -> {
          try {
            try (AFUNIXSocket client = server.accept()) { // NOPMD.UseTryWithResources
              System.out.println("Server accepted client connection");
              try (SocketChannel chann = client.getChannel()) {
                ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);

                int numRead = 0;
                while (bb.position() != 4 && numRead != -1) {
                  numRead = chann.read(bb);
                }
                if (bb.position() != 4) {
                  throw new IOException("Unexpected number of bytes read: " + bb.position());
                }
                bb.flip();
                int v;
                if ((v = bb.getInt()) != 0xABCDEF12) {
                  throw new IOException("Received unexpected data from client: 0x" + Integer
                      .toHexString(v));
                }
                bb.clear();
                bb.putLong(0x00112233456789L);
                bb.flip();
                chann.write(bb);
              }
            } finally {
              server.close(); // NOPMD
            }
          } catch (Exception e) { // NOPMD
            success.set(false);
            e.printStackTrace();
          }
        });
        t.start();

        try (AFUNIXSocket socket = addr.newConnectedSocket();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());) {
          out.writeInt(0xABCDEF12);
          out.flush();
          long v = in.readLong();
          if (v != 0x00112233456789L) {
            throw new IOException("Received unexpected data from server: 0x" + Long.toHexString(v));
          }
        }
        System.out.println("Data exchange succeeded");
      }
    } catch (Exception e) { // NOPMD
      success.set(false);
      e.printStackTrace();
      return;
    } finally {
      System.out.println("mini selftest " + (success.get() ? "passed" : "failed"));
    }
  }
}
