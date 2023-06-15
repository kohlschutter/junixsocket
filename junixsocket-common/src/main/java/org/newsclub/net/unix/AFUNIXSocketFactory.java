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
package org.newsclub.net.unix;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.util.Objects;

import javax.net.SocketFactory;

/**
 * The base for a SocketFactory that connects to UNIX sockets.
 *
 * Typically, the "hostname" is used as a reference to a socketFile on the file system. The actual
 * mapping is left to the implementor.
 *
 * @see AFUNIXSocketFactory.FactoryArg
 * @see AFUNIXSocketFactory.SystemProperty
 * @see AFUNIXSocketFactory.URIScheme
 */
public abstract class AFUNIXSocketFactory extends AFSocketFactory<AFUNIXSocketAddress> {
  /**
   * Creates a {@link AFUNIXSocketFactory}.
   */
  protected AFUNIXSocketFactory() {
    super();
  }

  @Override
  public Socket createSocket() throws SocketException {
    return AFUNIXSocket.newInstance(this);
  }

  @Override
  protected AFUNIXSocket connectTo(AFUNIXSocketAddress addr) throws IOException {
    return AFUNIXSocket.connectTo(addr);
  }

  /**
   * A socket factory that handles a custom hostname ("localhost", by default, and configured by the
   * system property &quot;org.newsclub.net.unix.socket.hostname&quot;), forwarding all other
   * requests to the fallback {@link SocketFactory}.
   */
  private abstract static class DefaultSocketHostnameSocketFactory extends AFUNIXSocketFactory {
    private static final String PROP_SOCKET_HOSTNAME = "org.newsclub.net.unix.socket.hostname";

    /**
     * Creates a {@link DefaultSocketHostnameSocketFactory}.
     */
    public DefaultSocketHostnameSocketFactory() {
      super();
    }

    @Override
    public final boolean isHostnameSupported(String host) {
      return getDefaultSocketHostname().equals(host);
    }

    private static String getDefaultSocketHostname() {
      return System.getProperty(PROP_SOCKET_HOSTNAME, "localhost");
    }
  }

  /**
   * A socket factory that handles a custom hostname ("localhost", by default, and configured by the
   * system property &quot;org.newsclub.net.unix.socket.hostname&quot;), forwarding all other
   * requests to the fallback {@link SocketFactory}.
   *
   * The socket path is configured through an argument passed by to the constructor.
   *
   * This is particularly useful for JDBC drivers that take a "socketFactory" and a
   * "socketFactoryArg". The latter will be passed as a constructor argument.
   */
  public static final class FactoryArg extends DefaultSocketHostnameSocketFactory {
    private final File socketFile;

    /**
     * Constructs a new {@link FactoryArg} factory using the given socket path.
     *
     * @param socketPath The path to the socket.
     */
    public FactoryArg(String socketPath) {
      super();
      Objects.requireNonNull(socketPath, "Socket path was null");

      this.socketFile = new File(socketPath);
    }

    /**
     * Constructs a new {@link FactoryArg} factory using the given socket path.
     *
     * @param file The path to the socket.
     */
    public FactoryArg(File file) {
      super();
      Objects.requireNonNull(file, "File was null");

      this.socketFile = file;
    }

    @Override
    public AFUNIXSocketAddress addressFromHost(String host, int port) throws SocketException {
      return AFUNIXSocketAddress.of(socketFile, port);
    }
  }

  /**
   * A socket factory that handles a custom hostname ("junixsocket.localhost", by default, and
   * configured by the system property &quot;org.newsclub.net.unix.socket.hostname&quot;),
   * forwarding all other requests to the fallback {@link SocketFactory}.
   *
   * The socket path is configured through a system property,
   * &quot;org.newsclub.net.unix.socket.default&quot;.
   *
   * NOTE: While it is technically possible, it is highly discouraged to programmatically change the
   * value of the property as it can lead to concurrency issues and undefined behavior.
   */
  public static final class SystemProperty extends DefaultSocketHostnameSocketFactory {
    private static final String PROP_SOCKET_DEFAULT = "org.newsclub.net.unix.socket.default";

    /**
     * Creates a {@link SystemProperty} socket factory.
     */
    public SystemProperty() {
      super();
    }

    @Override
    public AFUNIXSocketAddress addressFromHost(String host, int port) throws SocketException {
      String path = System.getProperty(PROP_SOCKET_DEFAULT);
      if (path == null || path.isEmpty()) {
        throw new IllegalStateException("Property not configured: " + PROP_SOCKET_DEFAULT);
      }
      File socketFile = new File(path);

      return AFUNIXSocketAddress.of(socketFile, port);
    }
  }

  /**
   * A socket factory that handles special host names formatted as file:// URIs.
   *
   * The file:// URI may also be specified in URL-encoded format, i.e., file:%3A%2F%2F etc.
   *
   * You may also surround the URL with square brackets ("[" and "]"), whereas the closing bracket
   * may be omitted.
   *
   * NOTE: In some circumstances it is recommended to use "<code>[file:%3A%2F%2F</code>(...)", i.e.
   * encoded and without the closing bracket. Since this is an invalid hostname, it will not trigger
   * a DNS lookup, but can still be used within a JDBC Connection URL.
   */
  public static final class URIScheme extends AFUNIXSocketFactory {
    private static final String FILE_SCHEME_PREFIX = "file://";
    private static final String FILE_SCHEME_PREFIX_ENCODED = "file%";
    private static final String FILE_SCHEME_LOCALHOST = "localhost";

    /**
     * Creates a {@link URIScheme} socket factory.
     */
    public URIScheme() {
      super();
    }

    private static String stripBrackets(String host) {
      if (host.startsWith("[")) {
        if (host.endsWith("]")) {
          host = host.substring(1, host.length() - 1);
        } else {
          host = host.substring(1);
        }
      }
      return host;
    }

    @Override
    public boolean isHostnameSupported(String host) {
      host = stripBrackets(host);
      return host.startsWith(FILE_SCHEME_PREFIX) || host.startsWith(FILE_SCHEME_PREFIX_ENCODED);
    }

    @Override
    public AFUNIXSocketAddress addressFromHost(String host, int port) throws SocketException {
      host = stripBrackets(host);
      if (host.startsWith(FILE_SCHEME_PREFIX_ENCODED)) {
        try {
          host = URLDecoder.decode(host, "UTF-8");
        } catch (Exception e) {
          throw (SocketException) new SocketException().initCause(e);
        }
      }
      if (!host.startsWith(FILE_SCHEME_PREFIX)) {
        throw new SocketException("Unsupported scheme");
      }

      String path = host.substring(FILE_SCHEME_PREFIX.length());
      if (path.startsWith(FILE_SCHEME_LOCALHOST)) {
        path = path.substring(FILE_SCHEME_LOCALHOST.length());
      }
      if (path.isEmpty()) {
        throw new SocketException("Path is empty");
      }
      if (!path.startsWith("/")) {
        throw new SocketException("Path must be absolute");
      }

      File socketFile = new File(path);
      return AFUNIXSocketAddress.of(socketFile, port);
    }
  }
}
