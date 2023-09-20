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
package org.newsclub.net.unix.ssl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SSLSocket;

/**
 * Helper class to simplify retrieving the requested SNI hostname sent from an SSL client to an SSL
 * server.
 *
 * @author Christian Kohlschütter
 */
public final class SNIHostnameCapture {
  /**
   * {@link SNIMatcher} that accepts and matches "any hostname".
   */
  public static final SNIMatcher ACCEPT_ANY_HOSTNAME = SNIHostName.createSNIMatcher(".*");

  private final AtomicBoolean complete = new AtomicBoolean();
  private String hostname = null;

  private SNIHostnameCapture() {
  }

  /**
   * Configures the given server {@link SSLSocket} to match the given hostname pattern.
   * <p>
   * The matched hostname is then accessible after the handshake is completed, via
   * {@link #getHostname()}. If no hostname was retrieved, {@code null} is assumed.
   *
   * @param sslSocket The socket to configure.
   * @param hostnameMatcher The matcher to use.
   * @return The {@link SNIHostnameCapture} instance.
   * @see #ACCEPT_ANY_HOSTNAME
   */
  public static SNIHostnameCapture configure(SSLSocket sslSocket, SNIMatcher hostnameMatcher) {
    return configure(sslSocket, hostnameMatcher, null);
  }

  /**
   * Configures the given server {@link SSLSocket} to match the given hostname pattern.
   * <p>
   * The matched hostname is then accessible after the handshake is completed, via
   * {@link #getHostname()}. If no hostname was retrieved, a fallback is retrieved via the given
   * supplier.
   *
   * @param sslSocket The socket to configure.
   * @param hostnameMatcher The matcher to use.
   * @param defaultHostnameSupplier The supplier for a default hostname, or {@code null}.
   * @return The {@link SNIHostnameCapture} instance.
   * @see #ACCEPT_ANY_HOSTNAME
   */
  public static SNIHostnameCapture configure(SSLSocket sslSocket, SNIMatcher hostnameMatcher,
      Supplier<String> defaultHostnameSupplier) {
    SNIHostnameCapture capture = new SNIHostnameCapture();

    sslSocket.addHandshakeCompletedListener((e) -> {
      if (capture.hostname == null) {
        capture.set(defaultHostnameSupplier == null ? null : defaultHostnameSupplier.get());
      }
    });

    SSLParametersUtil.setSNIMatcher(sslSocket, new CallbackSNIMatcher(hostnameMatcher, (name,
        matches) -> {
      if (matches && name instanceof SNIHostName) {
        capture.set(((SNIHostName) name).getAsciiName());
      }
    }));

    return capture;
  }

  // @ExcludeFromCodeCoverageGeneratedReport(reason = "if-statement is just a precaution")
  private void set(String hostname) {
    if (complete.compareAndSet(false, true)) {
      this.hostname = hostname;
    }
  }

  /**
   * Checks if a hostname can be returned by calling {@link #getHostname()} (which most likely means
   * the handshake is complete).
   *
   * @return {@code true} if {@link #getHostname()} will not throw an {@link IllegalStateException}.
   */
  public boolean isComplete() {
    return complete.get();
  }

  /**
   * Returns the hostname (either the retrieved one, or a fallback), which could also be
   * {@code null}.
   * <p>
   * If the method is called before the handshake is complete (check with {@link #isComplete()} or
   * simply call after {@link SSLSocket#startHandshake()}), an {@link IllegalStateException} is
   * thrown.
   *
   * @return The hostname, or {@code null}.
   * @throws IllegalStateException if the method was called before a hostname could be retrieved.
   */
  public String getHostname() {
    if (!complete.get()) {
      throw new IllegalStateException("Handshake not yet complete");
    }
    return hostname;
  }
}
