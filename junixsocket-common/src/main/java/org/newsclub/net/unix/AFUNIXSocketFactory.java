/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.Objects;

import javax.net.SocketFactory;

/**
 * The base for a SocketFactory that connects to UNIX sockets.
 * 
 * Typically, the "hostname" is used as a reference to a socketFile on the file system. The actual
 * mapping is left to the implementor.
 * 
 * Three default implementations are provided.
 * 
 * @see AFUNIXSocketFactory.FactoryArg
 * @see AFUNIXSocketFactory.SystemProperty
 * @see AFUNIXSocketFactory.URIScheme
 */
public abstract class AFUNIXSocketFactory extends SocketFactory {
  /**
   * Translates a "host" string (and port) to an {@link AFUNIXSocketAddress}.
   * 
   * @param host The hostname
   * @param port The port, or 0.
   * @return The {@link AFUNIXSocketAddress}
   * @throws IOException If there was a problem converting the hostname
   * @throws NullPointerException If host was {@code null}.
   */
  protected abstract AFUNIXSocketAddress addressFromHost(String host, int port) throws IOException;

  /**
   * Checks whether the given hostname is supported by this socket factory. If not, calls to
   * createSocket will cause an UnknownHostException.
   * 
   * @param host The host to check.
   * @return {@code true} if supported.
   */
  protected boolean isHostnameSupported(String host) {
    return host != null;
  }

  /**
   * Checks whether the given {@link InetAddress} is supported by this socket factory. If not, calls
   * to createSocket will cause an UnknownHostException.
   * 
   * By default, this only checks the hostname part of the address via
   * {@link #isHostnameSupported(String)}.
   * 
   * @param address The address to check.
   * @return {@code true} if supported.
   */
  protected boolean isInetAddressSupported(InetAddress address) {
    return address != null && isHostnameSupported(address.getHostName());
  }

  @Override
  public Socket createSocket() throws IOException {
    return AFUNIXSocket.newInstance(this);
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
    if (!isHostnameSupported(host)) {
      throw new UnknownHostException();
    }
    if (port < 0) {
      throw new IllegalArgumentException("Illegal port");
    }

    AFUNIXSocketAddress socketAddress = addressFromHost(host, port);
    return AFUNIXSocket.connectTo(socketAddress);
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException, UnknownHostException {
    if (!isHostnameSupported(host)) {
      throw new UnknownHostException();
    }
    if (localPort < 0) {
      throw new IllegalArgumentException("Illegal local port");
    }
    // NOTE: we simply ignore localHost and localPort
    return createSocket(host, port);
  }

  @Override
  public Socket createSocket(InetAddress address, int port) throws IOException {
    if (!isInetAddressSupported(address)) {
      throw new UnknownHostException();
    }
    String hostname = address.getHostName();
    if (!isHostnameSupported(hostname)) {
      throw new UnknownHostException();
    }
    return createSocket(hostname, port);
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    if (!isInetAddressSupported(address)) {
      throw new UnknownHostException();
    }
    Objects.requireNonNull(localAddress, "Local address was null");
    if (localPort < 0) {
      throw new IllegalArgumentException("Illegal local port");
    }
    // NOTE: we simply ignore localAddress and localPort
    return createSocket(address, port);
  }

  /**
   * A socket factory that handles a custom hostname ("localhost", by default, and configured by the
   * system property &quot;org.newsclub.net.unix.socket.hostname&quot;), forwarding all other
   * requests to the fallback {@link SocketFactory}.
   */
  private abstract static class DefaultSocketHostnameSocketFactory extends AFUNIXSocketFactory {
    private static final String PROP_SOCKET_HOSTNAME = "org.newsclub.net.unix.socket.hostname";

    @Override
    protected final boolean isHostnameSupported(String host) {
      return getDefaultSocketHostname().equals(host);
    }

    private static String getDefaultSocketHostname() {
      return System.getProperty(PROP_SOCKET_HOSTNAME, "localhost");
    }
  }

  /**
   * A socket factory that handles a custom hostname ("junixsocket.localhost", by default, and
   * configured by the system property &quot;org.newsclub.net.unix.socket.hostname&quot;),
   * forwarding all other requests to the fallback {@link SocketFactory}.
   * 
   * The socket path is configured through an argument passed by to the constructor.
   * 
   * This is particularly useful for JDBC drivers that take a "socketFactory" and a
   * "socketFactoryArg". The latter will be passed as a constructor argument.
   */
  public static final class FactoryArg extends DefaultSocketHostnameSocketFactory {
    private final File socketFile;

    public FactoryArg(String socketPath) {
      super();
      Objects.requireNonNull(socketPath, "Socket path was null");

      this.socketFile = new File(socketPath);
    }

    public FactoryArg(File file) {
      super();
      Objects.requireNonNull(file, "File was null");

      this.socketFile = file;
    }

    @Override
    protected AFUNIXSocketAddress addressFromHost(String host, int port) throws IOException {
      return new AFUNIXSocketAddress(socketFile, port);
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

    @Override
    protected AFUNIXSocketAddress addressFromHost(String host, int port) throws IOException {
      String path = System.getProperty(PROP_SOCKET_DEFAULT);
      if (path == null || path.isEmpty()) {
        throw new IllegalStateException("Property not configured: " + PROP_SOCKET_DEFAULT);
      }
      File socketFile = new File(path);

      return new AFUNIXSocketAddress(socketFile, port);
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
    protected boolean isHostnameSupported(String host) {
      host = stripBrackets(host);
      return host.startsWith(FILE_SCHEME_PREFIX) || host.startsWith(FILE_SCHEME_PREFIX_ENCODED);
    }

    @Override
    protected AFUNIXSocketAddress addressFromHost(String host, int port) throws IOException {
      host = stripBrackets(host);
      if (host.startsWith(FILE_SCHEME_PREFIX_ENCODED)) {
        try {
          host = URLDecoder.decode(host, "UTF-8");
        } catch (Exception e) {
          throw (UnknownHostException) new UnknownHostException().initCause(e);
        }
      }
      if (!host.startsWith(FILE_SCHEME_PREFIX)) {
        throw new UnknownHostException();
      }

      String path = host.substring(FILE_SCHEME_PREFIX.length());
      if (path.isEmpty()) {
        throw new UnknownHostException();
      }
      if (path.startsWith(FILE_SCHEME_LOCALHOST)) {
        path = path.substring(FILE_SCHEME_LOCALHOST.length());
      }
      if (!path.startsWith("/")) {
        throw new UnknownHostException();
      }

      File socketFile = new File(path);
      return new AFUNIXSocketAddress(socketFile, port);
    }
  }
}
