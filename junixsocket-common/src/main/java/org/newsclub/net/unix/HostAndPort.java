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

import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hostname and port.
 *
 * @author Christian Kohlschütter
 */
public final class HostAndPort {
  private static final Pattern PAT_HOST_AND_PORT = Pattern.compile(
      "^//((?<userinfo>[^/\\@]*)\\@)?(?<host>[^/\\:]+)(?:\\:(?<port>[0-9]+))?");
  private final String hostname;
  private final int port;

  /**
   * Creates a new hostname and port combination.
   *
   * @param hostname The hostname.
   * @param port The port, or {@code -1} for "no port".
   */
  public HostAndPort(String hostname, int port) {
    this.hostname = hostname;
    this.port = port;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getHostname() == null) ? 0 : getHostname().hashCode());
    result = prime * result + getPort();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof HostAndPort)) {
      return false;
    }
    HostAndPort other = (HostAndPort) obj;
    if (getHostname() == null) {
      if (other.getHostname() != null) {
        return false;
      }
    } else if (!getHostname().equals(other.getHostname())) {
      return false;
    }

    return getPort() == other.getPort();
  }

  @Override
  public String toString() {
    if (getPort() == -1) {
      return getHostname();
    } else {
      return getHostname() + ":" + getPort();
    }
  }

  /**
   * Tries to extract hostname and port information from the given URI.
   *
   * @param u The URI to extract from.
   * @return The parsed {@link HostAndPort} instance.
   * @throws SocketException on error.
   */
  public static HostAndPort parseFrom(URI u) throws SocketException {
    String host = u.getHost();
    if (host != null) {
      return new HostAndPort(host, u.getPort());
    }
    String raw = u.getRawSchemeSpecificPart();
    Matcher m = PAT_HOST_AND_PORT.matcher(raw);
    if (!m.find()) {
      throw new SocketException("Cannot parse URI: " + u);
    }
    try {
      host = URLDecoder.decode(m.group("host"), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }

    String portStr = m.group("port");
    int port;
    if (portStr == null) {
      port = -1;
    } else {
      port = Integer.parseInt(portStr);
    }

    return new HostAndPort(host, port);
  }

  private static String urlEncode(String s) {
    try {
      return URLEncoder.encode(s, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns the hostname.
   *
   * @return The hostname.
   */
  public String getHostname() {
    return hostname;
  }

  /**
   * Returns the port, or {@code -1} for "no port specified".
   *
   * @return The port.
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns a URI with this hostname and port.
   *
   * @param scheme The scheme to use.
   * @return The URI.
   */
  public URI toURI(String scheme) {
    return toURI(scheme, null, null, null, null);
  }

  /**
   * Returns a URI with this hostname and port, potentially reusing other URI parameters from the
   * given template URI (authority, path, query, fragment).
   *
   * @param scheme The scheme to use.
   * @param template The template. or {@code null}.
   * @return The URI.
   */
  public URI toURI(String scheme, URI template) {
    if (template == null) {
      return toURI(scheme, null, null, null, null);
    }

    String rawAuthority = template.getRawAuthority();
    int at = rawAuthority.indexOf('@');
    if (at >= 0) {
      rawAuthority = rawAuthority.substring(0, at);
    } else if (rawAuthority.length() > 0 && template.getHost() == null) {
      // encoded hostname was parsed as authority
      rawAuthority = null;
    } else if (rawAuthority.length() > 0 && template.getAuthority().equals(template.getHost())) {
      // hostname was duplicated as authority
      rawAuthority = null;
    } else if (rawAuthority.length() > 0 && template.getAuthority().equals(template.getHost() + ":"
        + template.getPort())) {
      // hostname:port was duplicated as authority
      rawAuthority = null;
    }

    return toURI(scheme, rawAuthority, template.getRawPath(), template.getRawQuery(), template
        .getRawFragment());
  }

  /**
   * Returns a URI with this hostname and port, potentially using other URI parameters from the
   * given set of parameters.
   *
   * @param scheme The scheme to use.
   * @param rawAuthority The raw authority field, or {@code null}.
   * @param rawPath The raw path field, or {@code null}.
   * @param rawQuery The raw query field, or {@code null}.
   * @param rawFragment The raw fragment field, or {@code null}.
   * @return The URI.
   */
  public URI toURI(String scheme, String rawAuthority, String rawPath, String rawQuery,
      String rawFragment) {
    Objects.requireNonNull(scheme);
    if (rawPath != null && !rawPath.isEmpty()) {
      if (!rawPath.startsWith("/")) {
        throw new IllegalArgumentException("Path must be absolute: " + rawPath);
      }
    }

    return URI.create(scheme + "://" + (rawAuthority == null ? "" : rawAuthority + "@") + urlEncode(
        getHostname()).replace("%2C", ",") + (port <= 0 ? "" : (":" + port)) + (rawPath == null ? ""
            : rawPath) + (rawQuery == null ? "" : "?" + rawQuery) + (rawFragment == null ? "" : "#"
                + rawFragment));
  }
}
