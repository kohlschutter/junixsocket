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
//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.newsclub.net.unix.jetty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.ProxyProtocolClientConnectionFactory.V1;
import org.eclipse.jetty.client.ProxyProtocolClientConnectionFactory.V2;
import org.eclipse.jetty.client.transport.HttpClientTransportDynamic;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.LifeCycle;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class UnixDomainTest {
  private static final Class<?> unixDomainSocketAddressClass = probe();

  private static Class<?> probe() {
    try {
      return ClassLoader.getPlatformClassLoader().loadClass("java.net.UnixDomainSocketAddress");
    } catch (Throwable x) {
      return null;
    }
  }

  private ConnectionFactory[] factories = new ConnectionFactory[] {new HttpConnectionFactory()};
  private Server server;
  private Path unixDomainPath;

  @BeforeAll
  public static void setUp() {
    System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "error");
  }

  private void start(Handler handler) throws Exception {
    server = new Server();
    // UnixDomainServerConnector connector = new UnixDomainServerConnector(server, factories);
    AFSocketServerConnector connector = new AFSocketServerConnector(server, factories);
    String dir = System.getProperty("jetty.unixdomain.dir", System.getProperty("java.io.tmpdir"));
    assertNotNull(dir);
    File dirFile = new File(dir);
    dirFile.mkdirs();
    unixDomainPath = Files.createTempFile(dirFile.toPath(), "unix_", ".sock");
    assertTrue(unixDomainPath.toAbsolutePath().toString().length() < 108,
        "Unix-Domain path too long");
    Files.delete(unixDomainPath);
    // connector.setUnixDomainPath(unixDomainPath);
    connector.setListenSocketAddress(AFUNIXSocketAddress.of(unixDomainPath));
    server.addConnector(connector);
    server.setHandler(handler);
    server.start();
  }

  @AfterEach
  public void dispose() {
    LifeCycle.stop(server);
  }

  @Test
  public void testHTTPOverUnixDomain() throws Exception {
    String uri = "http://localhost:1234/path";
    start(new Handler.Abstract() {

      @Override
      public boolean handle(Request request, Response response, Callback callback)
          throws Exception {
        // Verify the URI is preserved.
        assertEquals(uri, request.getHttpURI().toString());

        // Verify the SocketAddresses.
        ConnectionMetaData connectionMetaData = request.getConnectionMetaData();
        SocketAddress local = connectionMetaData.getLocalSocketAddress();
        SocketAddress remote = connectionMetaData.getRemoteSocketAddress();

        assertThat(local, Matchers.instanceOf(AFSocketAddress.class));
        if (remote != null) {
          // remote should be null if not connected
          assertThat(remote, Matchers.instanceOf(AFSocketAddress.class));
        }

        assertDoesNotThrow(connectionMetaData::toString);

        response.write(true, ByteBuffer.allocate(0), new Callback() {
        });

        return true;
      }
    });

    // ClientConnector clientConnector = ClientConnector.forUnixDomain(unixDomainPath);
    ClientConnector clientConnector = AFSocketClientConnector.withSocketAddress(AFUNIXSocketAddress
        .of(unixDomainPath));
    try (HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector))) {
      httpClient.start();
      try {
        ContentResponse response = httpClient.newRequest(uri).timeout(5, TimeUnit.SECONDS).send();

        assertEquals(HttpStatus.OK_200, response.getStatus());
      } finally {
        httpClient.stop();
      }
    }
  }

  @Test
  public void testHTTPOverUnixDomainWithHTTPProxy() throws Exception {
    int fakeProxyPort = 4567;
    int fakeServerPort = 5678;
    start(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback)
          throws Exception {
        // Proxied requests must have an absolute URI.
        HttpURI uri = request.getHttpURI();
        assertNotNull(uri.getScheme());
        assertEquals(fakeServerPort, uri.getPort());

        response.write(true, ByteBuffer.allocate(0), new Callback() {
        });

        return true;
      }
    });

    // ClientConnector clientConnector = ClientConnector.forUnixDomain(unixDomainPath);

    AFUNIXSocketAddress unixDomainAddress = AFUNIXSocketAddress.of(unixDomainPath);
    ClientConnector clientConnector = AFSocketClientConnector.withSocketAddress(unixDomainAddress);

    // HttpProxy proxy = new HttpProxy("localhost", fakeProxyPort); // this worked until 12.0.6
    HttpProxy proxy = new HttpProxy(new Origin("http", new Origin.Address("localhost",
        fakeProxyPort), null, new Origin.Protocol(List.of("http/1.1"), false), AFSocketTransport
            .withSocketChannel(unixDomainAddress)), null);

    try (HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector))) {
      httpClient.getProxyConfiguration().addProxy(proxy);
      httpClient.start();
      try {
        ContentResponse response = httpClient.newRequest("localhost", fakeServerPort).timeout(5,
            TimeUnit.SECONDS).send();

        assertEquals(HttpStatus.OK_200, response.getStatus());
      } finally {
        httpClient.stop();
      }
    }
  }

  @Test
  public void testHTTPOverUnixDomainWithProxyProtocol() throws Exception {
    String srcAddr = "/proxySrcAddr";
    String dstAddr = "/proxyDstAddr";
    factories = new ConnectionFactory[] {new ProxyConnectionFactory(), new HttpConnectionFactory()};
    start(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback)
          throws Exception {
        ConnectionMetaData connectionMetaData = request.getConnectionMetaData();

        String target = request.getHttpURI().getPath();

        if ("/v1".equals(target)) {
          // As PROXYv1 does not support UNIX, the wrapped EndPoint data is used.
          Path localPath = toUnixDomainPath(connectionMetaData.getLocalSocketAddress());
          assertThat(localPath, Matchers.equalTo(unixDomainPath));
        } else if ("/v2".equals(target)) {
          SocketAddress localSocketAddress = connectionMetaData.getLocalSocketAddress();
          if (localSocketAddress != null) {
            assertThat(toUnixDomainPath(localSocketAddress).toString(), Matchers.equalTo(separators(
                dstAddr)));
          }
          SocketAddress remoteSocketAddress = connectionMetaData.getRemoteSocketAddress();
          if (remoteSocketAddress != null) {
            assertThat(toUnixDomainPath(remoteSocketAddress).toString(), Matchers.equalTo(
                separators(srcAddr)));
          }
        } else {
          Assertions.fail("Invalid PROXY protocol version " + target);
        }

        response.write(true, ByteBuffer.allocate(0), new Callback() {
        });

        return true;
      }
    });

    // Java 11+ portable way to implement SocketChannelWithAddress.Factory.
    // ClientConnector clientConnector = ClientConnector.forUnixDomain(unixDomainPath);
    ClientConnector clientConnector = AFSocketClientConnector.withSocketAddress(AFUNIXSocketAddress
        .of(unixDomainPath));

    try (HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector))) {
      httpClient.start();
      try {
        // Try PROXYv1 with the PROXY information retrieved from the EndPoint.
        // PROXYv1 does not support the UNIX family.
        ContentResponse response1 = httpClient.newRequest("localhost", 0).path("/v1").tag(
            new V1.Tag()).timeout(5, TimeUnit.SECONDS).send();

        assertEquals(HttpStatus.OK_200, response1.getStatus());

        // Try PROXYv2 with explicit PROXY information.
        V2.Tag tag = new V2.Tag(V2.Tag.Command.PROXY, V2.Tag.Family.UNIX, V2.Tag.Protocol.STREAM,
            srcAddr, 0, dstAddr, 0, null);
        ContentResponse response2 = httpClient.newRequest("localhost", 0).path("/v2").tag(tag)
            .timeout(5, TimeUnit.SECONDS).send();

        assertEquals(HttpStatus.OK_200, response2.getStatus());
      } finally {
        httpClient.stop();
      }
    }
  }

  @Test
  public void testInvalidUnixDomainPath() {
    server = new Server();
    // UnixDomainServerConnector connector = new UnixDomainServerConnector(server, factories);
    AFSocketServerConnector connector = new AFSocketServerConnector(server, factories);

    // connector.setUnixDomainPath(new File("/does/not/exist").toPath());
    try {
      connector.setListenSocketAddress(AFUNIXSocketAddress.of(new File("/does/not/exist")
          .toPath()));
    } catch (SocketException e) {
      throw new IllegalStateException(e);
    }

    server.addConnector(connector);
    assertThrows(IOException.class, () -> server.start());
  }

  private static Path toUnixDomainPath(SocketAddress address) {
    Objects.requireNonNull(address, "address");

    if (address instanceof AFUNIXSocketAddress) {
      return new File(((AFUNIXSocketAddress) address).getPath()).toPath();
    } else if (unixDomainSocketAddressClass != null) {
      try {
        return (Path) unixDomainSocketAddressClass.getMethod("getPath").invoke(address);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException
          | SecurityException e) {
        Assertions.fail(e);
        throw new AssertionError(e);
      }
    } else {
      throw new IllegalStateException("Unsupported socket address class " + address.getClass()
          + ": " + address);
    }
  }

  public static String separators(String path) {
    StringBuilder ret = new StringBuilder();
    for (int i = 0, n = path.length(); i < n; i++) {
      char c = path.charAt(i);
      if ((c == '/') || (c == '\\')) {
        ret.append(File.separatorChar);
      } else {
        ret.append(c);
      }
    }
    return ret.toString();
  }

  @Test
  public void testLargeBody() throws Exception {
    String uri = "http://localhost:1234/path";

    byte[] payload = new byte[512 * 1024]; // 512k
    new Random().nextBytes(payload);

    start(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback)
          throws Exception {
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/octet-stream");

        response.write(true, ByteBuffer.wrap(payload), new Callback() {
        });

        return true;
      }
    });

    // ClientConnector clientConnector = ClientConnector.forUnixDomain(unixDomainPath);
    ClientConnector clientConnector = AFSocketClientConnector.withSocketAddress(AFUNIXSocketAddress
        .of(unixDomainPath));
    try (HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector))) {
      httpClient.start();
      try {
        ContentResponse response = httpClient.newRequest(uri).timeout(5, TimeUnit.SECONDS).send();

        assertEquals(HttpStatus.OK_200, response.getStatus());
        byte[] data = response.getContent();
        assertArrayEquals(payload, data);
      } finally {
        httpClient.stop();
      }
    }
  }
}