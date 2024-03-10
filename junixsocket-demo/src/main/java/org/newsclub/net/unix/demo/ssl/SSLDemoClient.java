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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.DestroyFailedException;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.ssl.SSLContextBuilder;

/**
 * A simple SSL demo client.
 *
 * @author Christian Kohlschütter
 * @see SSLDemoServer
 * @see SSLDemoPrerequisites
 */
public class SSLDemoClient {
  public static void main(String[] args) throws InterruptedException, IOException,
      GeneralSecurityException, DestroyFailedException {

    // Enable to see SSL debugging
    // System.setProperty("javax.net.debug", "all");

    AFUNIXSocketAddress addr = AFUNIXSocketAddress.of(new File("/tmp/ssldemo"));

    SSLSocketFactory clientSocketFactory = SSLContextBuilder.forClient() //
        .withKeyStore(new File("juxclient.p12"), () -> "clientpass".toCharArray()) //
        .withTrustStore(new File("juxclient.truststore"), () -> "clienttrustpass".toCharArray())
        .withDefaultSSLParameters((p) -> {
          // A simple way to disable some undesired protocols:
          // SSLParametersUtil.disableProtocols(p, "TLSv1.0", "TLSv1.1", "TLSv1.2");

          // Uncomment to perform endpoint checking of server certificates
          // p.setEndpointIdentificationAlgorithm("HTTPS");

        }) //
        .buildAndDestroyBuilder().getSocketFactory();

    System.out.println("Connecting to " + addr);

    try (AFUNIXSocket plainSocket = AFUNIXSocket.connectTo(addr);
        SSLSocket sslSocket = (SSLSocket) clientSocketFactory.createSocket(plainSocket,
            // This hostname is sent to the server, unless we define an SNI hostname below
            "localhost.junixsocket", //
            plainSocket.getPort(), false)) {

      // Setting the client mode manually is not necessary when using SSLContextBuilder
      // sslSocket.setUseClientMode(false);

      // Uncomment to send SNI hostname to server
      // SSLParametersUtil.setSNIServerName(sslSocket, new SNIHostName("subdomain.example.com"));

      try (InputStream in = sslSocket.getInputStream();
          OutputStream out = sslSocket.getOutputStream()) {
        plainSocket.setOutboundFileDescriptors(FileDescriptor.err);
        System.out.println("Writing byte...");
        out.write(0xAF);
        out.flush();

        byte[] by = new byte[11];
        int r = in.read(by);
        System.out.println("Received string: " + new String(by, 0, r, StandardCharsets.UTF_8));
      }
    }
  }
}
