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
package org.newsclub.net.unix.demo.ssl;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.DestroyFailedException;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.FileDescriptorCast;
import org.newsclub.net.unix.server.AFSocketServer;
import org.newsclub.net.unix.ssl.SNIHostnameCapture;
import org.newsclub.net.unix.ssl.SSLContextBuilder;

/**
 * A simple SSL demo server.
 *
 * @author Christian Kohlschütter
 * @see SSLDemoClient
 * @see SSLDemoPrerequisites
 */
@SuppressWarnings("CatchAndPrintStackTrace" /* errorprone */)
public class SSLDemoServer {
  public static void main(String[] args) throws InterruptedException, IOException,
      GeneralSecurityException, DestroyFailedException {

    // Enable to see SSL debugging
    // System.setProperty("javax.net.debug", "all");

    AFUNIXSocketAddress addr = AFUNIXSocketAddress.of(new File("/tmp/ssldemo"));

    SSLSocketFactory serverSocketFactory = SSLContextBuilder.forServer() //
        .withKeyStore(new File("juxserver.p12"), () -> "serverpass".toCharArray()) //
        .withTrustStore(new File("juxserver.truststore"), () -> "servertrustpass".toCharArray()) //
        .withDefaultSSLParameters((p) -> {
          // A simple way to disable some undesired protocols:
          // SSLParametersUtil.disableProtocols(p, "TLSv1.0", "TLSv1.1", "TLSv1.2");

          // Uncomment to require client-authentication
          // (which is meaningful in the context of UNIX sockets)
          // p.setNeedClientAuth(true);

          // Uncomment to perform endpoint checking of client certificates
          // p.setEndpointIdentificationAlgorithm("HTTPS");
        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    AFSocketServer<AFUNIXSocketAddress> server = new AFSocketServer<AFUNIXSocketAddress>(addr) {

      // Match any SNI hostname, including none sent
      final SNIMatcher sniHostnameMatcher = SNIHostnameCapture.ACCEPT_ANY_HOSTNAME;

      // Replace with code below to enforce receiving a valid SNI hostname
      // final SNIMatcher sniHostnameMatcher = SNIHostName.createSNIMatcher("(^|.+?\\.)" + Pattern
      // .quote("example.com"));

      @Override
      protected void doServeSocket(AFSocket<? extends AFUNIXSocketAddress> plainSocket)
          throws IOException {
        try (SSLSocket sslSocket = (SSLSocket) serverSocketFactory.createSocket(plainSocket,
            // This is considered the peer name (the client's name)
            "localhost.junixsocket", plainSocket.getPort(), //
            // you can also replace the above line with:
            // null,
            false)) {

          // Setting the client mode manually is not necessary when using SSLContextBuilder
          // sslSocket.setUseClientMode(false);

          SNIHostnameCapture sniHostname = SNIHostnameCapture.configure(sslSocket,
              sniHostnameMatcher);

          // Set to 0 to disable receiving file descriptors, etc.
          plainSocket.setAncillaryReceiveBufferSize(1); // rounds up to minimum buffer size

          sslSocket.startHandshake();
          // Make sure the handshake is complete before we check the hostname
          // (otherwise: IllegalStateException)
          try {
            if (sniHostname.isComplete(1, TimeUnit.SECONDS)) {
              System.out.println("Requested SNI hostname: " + sniHostname.getHostname());
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }

          try (InputStream in = sslSocket.getInputStream();
              OutputStream out = sslSocket.getOutputStream();) {
            System.out.println("Received byte: " + Integer.toHexString(in.read()));

            System.out.println("Sending hello...");
            out.write("Hello World".getBytes(StandardCharsets.UTF_8));

            FileDescriptor[] fds = ((AFUNIXSocket) plainSocket).getReceivedFileDescriptors();
            if (fds.length > 0) {
              System.out.println("File descriptor received: " + Arrays.asList(fds));
              System.out.println(
                  "Sending an extra message directly to a FileDescriptor of the other process...");
              try (PrintStream ps = new PrintStream(FileDescriptorCast.using(fds[0]).as(
                  OutputStream.class), true, Charset.defaultCharset().name())) {
                ps.println("Greetings from the server, right to your stderr");
              }
            }
          }
        }
      }

      @Override
      protected void onServingException(AFSocket<? extends AFUNIXSocketAddress> socket,
          Exception e) {
        e.printStackTrace();
      }
    };
    server.startAndWaitToBecomeReady();
  }
}
