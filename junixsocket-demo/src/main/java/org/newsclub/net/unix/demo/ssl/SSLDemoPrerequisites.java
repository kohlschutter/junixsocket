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
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * SSL-over-UNIX sockets demo.
 * <p>
 * Prerequisites:
 * <ol>
 * <li>Create server public/private key pair, valid for ~10 years, Store it as a PKCS12 file named
 * "{@code juxserver.p12}":
 * <p>
 * {@code keytool -genkeypair -alias juxserver -keyalg RSA -keysize 2048
 *   -storetype PKCS12 -validity 3650 -ext san=dns:localhost.junixsocket
 *   -dname "CN=First and Last, OU=Organizational Unit, O=Organization, L=City, ST=State, C=XX"
 *   -keystore juxserver.p12 -storepass serverpass}
 * <p>
 * Omit {@code -dname "CN=..."} to interactively specify the distinguished name for the certificate.
 * <p>
 * You may verify the contents of this p12 file via
 * {@code keytool -list -v -keystore juxserver.p12 -storepass serverpass}; omit the
 * {@code -storepass...} parameters to specify the password interactively for additional security.
 * </li>
 * <li>Export the server's public key as a X.509 certificate:
 * <p>
 * {@code keytool -exportcert -alias juxserver -keystore juxserver.p12 -storepass serverpass -file juxserver.pem}
 * <p>
 * You may verify the contents of the certificate file via
 * {@code keytool -printcert -file juxserver.pem}</li>
 * <li>Import the server's X.509 certificate into the client truststore:
 * <p>
 * {@code keytool -importcert -alias juxserver -keystore juxclient.truststore -storepass clienttrustpass
 *   -file juxserver.pem -noprompt}
 * <p>
 * Omit {@code -noprompt} to interactively verify the imported certificate.
 * </p>
 * <p>
 * You may verify the contents of this truststore via
 * {@code keytool -list -v -keystore juxclient.truststore -storepass clienttrustpass}; omit the
 * {@code -storepass...} parameters to specify the password interactively for additional security.
 * </li>
 * </ol>
 * <p>
 * If you want client authentication as well, perform these additional steps:
 * <ol>
 * <li>Create client public/private key pair, valid for ~10 years, Store it as a PKCS12 file named
 * "{@code juxclient.p12}":
 * <p>
 * {@code keytool -genkeypair -alias juxclient -keyalg RSA -keysize 2048 -storetype PKCS12
 *   -validity 3650 -ext san=dns:localhost.junixsocket
 *   -dname "CN=First and Last, OU=Organizational Unit, O=Organization, L=City, ST=State, C=XX"
 *   -keystore juxclient.p12 -storepass clientpass}
 * <p>
 * Omit {@code -dname "CN=..."} to interactively specify the distinguished name for the certificate.
 * <p>
 * You may verify the contents of this p12 file via
 * {@code keytool -list -v -keystore juxclient.p12 -storepass clientpass}; omit the
 * {@code -storepass...} parameters to specify the password interactively for additional security.
 * </li>
 * <li>Export the client's public key as a X.509 certificate:
 * <p>
 * {@code keytool -exportcert -alias juxclient -keystore juxclient.p12 -storepass clientpass -file juxclient.pem}
 * <p>
 * You may verify the contents of the certificate file via
 * {@code keytool -printcert -file juxclient.pem}</li>
 * <li>Import the client's X.509 certificate into the servers truststore:
 * <p>
 * {@code keytool -importcert -alias juxclient -keystore juxserver.truststore -storepass servertrustpass
 *   -file juxclient.pem -noprompt}
 * <p>
 * Omit {@code -noprompt} to interactively verify the imported certificate.
 * </p>
 * <p>
 * You may verify the contents of this truststore via
 * {@code keytool -list -v -keystore juxserver.truststore -storepass servertrustpass}; omit the
 * {@code -storepass...} parameters to specify the password interactively for additional security.
 * </li>
 * </ol>
 *
 * @author Christian Kohlschütter
 * @see org.newsclub.net.unix.demo.ssl.SSLDemoServer
 * @see org.newsclub.net.unix.demo.ssl.SSLDemoClient
 */
@SuppressWarnings({
    "FutureReturnValueIgnored", // errorprone
    "CatchAndPrintStackTrace", // errorprone
})
public class SSLDemoPrerequisites {
  private static final boolean runCommand(String explanation, String... command) throws IOException,
      InterruptedException {
    System.out.println(explanation + "...");

    StringBuilder sb = new StringBuilder();
    for (String c : command) {
      sb.append(" ");
      if (c.isEmpty() || c.contains(" ") || c.contains("\"")) {
        sb.append("\"" + c.replace("\"", "\\\"") + "\"");
      } else {
        sb.append(c);
      }
    }
    System.out.println("#" + sb);

    Process process = Runtime.getRuntime().exec(command);

    CompletableFuture.runAsync(() -> {
      try (InputStream stdout = process.getInputStream()) {
        byte[] buf = new byte[1024];
        int r;
        while ((r = stdout.read(buf)) >= 0) {
          System.out.write(buf, 0, r);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
    CompletableFuture.runAsync(() -> {
      try (InputStream stderr = process.getErrorStream()) {
        byte[] buf = new byte[1024];
        int r;
        while ((r = stderr.read(buf)) >= 0) {
          System.err.write(buf, 0, r);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    });

    int rc = process.waitFor();
    System.out.println("rc=" + rc);
    System.out.println();
    return rc == 0;
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Working directory: " + new File("").getAbsolutePath());
    System.out.println();

    boolean success = true;

    success &= runCommand("Generating server key pair", //
        "keytool", "-genkeypair", "-alias", "juxserver", "-keyalg", "RSA", "-keysize", "2048",
        "-storetype", "PKCS12", "-validity", "3650", "-ext", "san=dns:localhost.junixsocket",
        "-dname",
        "CN=First and Last, OU=Organizational Unit, O=Organization, L=City, ST=State, C=XX",
        "-keystore", "juxserver.p12", "-storepass", "serverpass");

    success &= runCommand("Exporting server certificate", //
        ("keytool -exportcert -alias juxserver -keystore juxserver.p12 -storepass serverpass -file juxserver.pem")
            .split("[ ]+"));

    success &= runCommand("Importing server certificate into client truststore", //
        ("keytool -importcert -alias juxserver -keystore juxclient.truststore -storepass clienttrustpass"
            + " -file juxserver.pem -noprompt").split("[ ]+"));

    success &= runCommand("Generating client key pair (optional, only for client authentication)", //
        "keytool", "-genkeypair", "-alias", "juxclient", "-keyalg", "RSA", "-keysize", "2048",
        "-storetype", "PKCS12", "-validity", "3650", "-ext", "san=dns:localhost.junixsocket",
        "-dname",
        "CN=First and Last, OU=Organizational Unit, O=Organization, L=City, ST=State, C=XX",
        "-keystore", "juxclient.p12", "-storepass", "clientpass");

    success &= runCommand("Exporting client certificate (optional, only for client authentication)", //
        ("keytool -exportcert -alias juxclient -keystore juxclient.p12 -storepass clientpass -file juxclient.pem")
            .split("[ ]+"));

    success &= runCommand(
        "Importing client certificate server client truststore (optional, only for client authentication)", //
        ("keytool -importcert -alias juxclient -keystore juxserver.truststore -storepass servertrustpass"
            + " -file juxclient.pem -noprompt").split("[ ]+"));

    System.out.println("DONE. All successful=" + success);
  }
}
