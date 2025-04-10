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
package org.newsclub.net.unix.ssl;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.StandardConstants;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

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
  public static final SNIMatcher ACCEPT_ANY_HOSTNAME = new SNIMatcher(
      StandardConstants.SNI_HOST_NAME) {

    @Override
    public boolean matches(SNIServerName serverName) {
      return serverName.getType() == StandardConstants.SNI_HOST_NAME;
    }
  };

  private final CompletableFuture<String> hostnameFuture = new CompletableFuture<>();

  private final Supplier<String> defaultHostnameSupplier;

  private SNIHostnameCapture(Supplier<String> defaultHostnameSupplier) {
    this.defaultHostnameSupplier = defaultHostnameSupplier;
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
    SNIHostnameCapture capture = new SNIHostnameCapture(defaultHostnameSupplier == null ? () -> null
        : defaultHostnameSupplier);

    sslSocket.addHandshakeCompletedListener((e) -> {
      capture.set(null);
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
    hostnameFuture.complete(hostname);
  }

  /**
   * Checks if a hostname can be returned by calling {@link #getHostname()} (which most likely means
   * the handshake is complete), optionally waiting up to the given time interval if not yet
   * complete.
   *
   * @param timeout The maximum timeout value to wait for.
   * @param unit Timeout unit.
   * @return {@code true} if {@link #getHostname()} will not throw an {@link IllegalStateException}.
   * @throws InterruptedException if the current thread was interrupted while waiting.
   */
  public boolean isComplete(long timeout, TimeUnit unit) throws InterruptedException {
    try {
      hostnameFuture.get(timeout, unit);
      return hostnameFuture.isDone();
    } catch (ExecutionException | TimeoutException e) {
      return false;
    }
  }

  /**
   * Checks if a hostname can be returned by calling {@link #getHostname()} (which most likely means
   * the handshake is complete).
   *
   * @return {@code true} if {@link #getHostname()} will not throw an {@link IllegalStateException}.
   */
  public boolean isComplete() {
    return hostnameFuture.isDone();
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
    if (!isComplete()) {
      throw new IllegalStateException("Handshake not yet complete");
    }
    String hn = hostnameFuture.getNow(null);
    if (hn == null) {
      return defaultHostnameSupplier.get();
    } else {
      return hn;
    }
  }

  /**
   * Returns the hostname from the data stored in a socket's {@link ExtendedSSLSession}, if
   * available. The default fallback handler is used if the data could not be retrieved.
   * <p>
   * This is only for testing purposes. BouncyCastle doesn't support this, for example.
   *
   * @param socket The socket.
   * @return The hostname (retrieved or fallback).
   */
  @SuppressWarnings("PatternMatchingInstanceof") // ErrorProne
  String getHostnameFromSSLSession(SSLSocket socket,
      Consumer<UnsupportedOperationException> usoCallback) {
    SSLSession session = socket.getSession();
    if (session instanceof ExtendedSSLSession) {
      ExtendedSSLSession extSession = (ExtendedSSLSession) session;
      try {
        List<SNIServerName> list = extSession.getRequestedServerNames();
        if (list != null) {
          for (SNIServerName sn : list) {
            if (sn instanceof SNIHostName) {
              return ((SNIHostName) sn).getAsciiName();
            } else if (sn.getType() == StandardConstants.SNI_HOST_NAME) {
              return new SNIHostName(sn.getEncoded()).getAsciiName();
            }
          }
        }
      } catch (UnsupportedOperationException e) {
        // fall through
        if (usoCallback != null) {
          usoCallback.accept(e);
        }
      }
    }
    return defaultHostnameSupplier.get();
  }
}
