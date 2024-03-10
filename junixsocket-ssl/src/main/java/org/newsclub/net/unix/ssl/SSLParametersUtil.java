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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import com.kohlschutter.annotations.compiletime.ExcludeFromCodeCoverageGeneratedReport;

/**
 * Utility class to work with {@link SSLParameters}.
 *
 * @author Christian Kohlschütter
 */
public final class SSLParametersUtil {
  @ExcludeFromCodeCoverageGeneratedReport
  private SSLParametersUtil() {
    throw new IllegalStateException("No instances");
  }

  /**
   * Removes the given protocols from the set of protocols specified in an {@link SSLParameters}
   * instance.
   *
   * @param params The parameters instance to configure.
   * @param protocols The protocols to disable.
   */
  public static void disableProtocols(SSLParameters params, String... protocols) {
    removeElements(params::getProtocols, params::setProtocols, protocols);
  }

  /**
   * Removes the given cipher suites from the set of cipher suites specified in an
   * {@link SSLParameters} instance.
   *
   * @param params The parameters instance to configure.
   * @param cipherSuites The protocols to disable.
   */
  public static void disableCipherSuites(SSLParameters params, String... cipherSuites) {
    removeElements(params::getCipherSuites, params::setCipherSuites, cipherSuites);
  }

  /**
   * Sets an {@link SNIMatcher} instance with the parameters of the given {@link SSLSocket} (which
   * should be in <em>server</em> mode).
   *
   * @param s The socket to modify.
   * @param matcher The matcher.
   */
  public static void setSNIMatcher(SSLSocket s, SNIMatcher matcher) {
    SSLParameters p = s.getSSLParameters();
    p.setSNIMatchers(Collections.singleton(matcher));
    s.setSSLParameters(p);
  }

  /**
   * Sets the desired SNI server name with the parameters of the given {@link SSLSocket} (which
   * should be in <em>client</em> mode).
   *
   * @param s The socket to modify.
   * @param name The name.
   */
  public static void setSNIServerName(SSLSocket s, SNIServerName name) {
    SSLParameters p = s.getSSLParameters();
    p.setServerNames(Collections.singletonList(name));
    s.setSSLParameters(p);
  }

  private static void removeElements(Supplier<String[]> getter, Consumer<String[]> setter,
      String... protocols) {
    Set<String> protos = new HashSet<>(Arrays.asList(getter.get()));
    protos.removeAll(Arrays.asList(protocols));
    setter.accept(protos.toArray(new String[0]));
  }
}
