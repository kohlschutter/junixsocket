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
package org.newsclub.net.unix.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketFactory;
import org.newsclub.net.unix.SocketTestBase;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_UNIX_DOMAIN)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION",
    "DMI_HARDCODED_ABSOLUTE_FILENAME"})
public final class SocketFactoryTest extends SocketTestBase<AFUNIXSocketAddress> {

  public SocketFactoryTest() {
    super(AFUNIXAddressSpecifics.INSTANCE);
  }

  @Test
  public void testURISchemeCeateSocketThenConnect() throws Exception {
    AFUNIXSocketFactory.URIScheme factory = new AFUNIXSocketFactory.URIScheme();

    try (Socket sock = factory.createSocket(); //
        AFUNIXSocket socket = (AFUNIXSocket) sock) {

      assertThrows(IllegalArgumentException.class, () -> {
        socket.connect(null);
      });
      assertThrows(IllegalArgumentException.class, () -> {
        socket.connect(new InetSocketAddress("http://example.com./", 0));
      });
      assertThrows(IllegalArgumentException.class, () -> {
        socket.connect(new InetSocketAddress("", 0));
      });

      assertThrows(SocketException.class, () -> {
        // file:// has an empty path component
        socket.connect(new InetSocketAddress("file://", 0));
      });
      assertThrows(SocketException.class, () -> {
        // file://not-absolute is not an absolute path (three slashes needed)
        socket.connect(new InetSocketAddress("file://not-absolute", 0));
      });
      assertThrows(SocketException.class, () -> {
        // file exists (root directory), but is definitely not a unix socket
        socket.connect(new InetSocketAddress("file:///", 0));
      });
    }
  }

  @Test
  public void testURISchemeCeateSocketWithIllegalArguments() throws Exception {
    AFUNIXSocketFactory.URIScheme factory = new AFUNIXSocketFactory.URIScheme();
    assertThrows(IllegalArgumentException.class, () -> {
      // Illegal local port
      try (Socket sock = factory.createSocket("file:///", 0, null, -1)) {
        fail("Should not be reached: " + sock);
      }
    });
  }

  @Test
  public void testURISchemeCeateSocketWithInvalidHostname() throws Exception {
    AFUNIXSocketFactory.URIScheme factory = new AFUNIXSocketFactory.URIScheme();

    assertThrows(SocketException.class, () -> {
      // We don't support empty hosts
      try (Socket sock = factory.createSocket("", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // We don't support empty hosts
      try (Socket sock = factory.createSocket("", 0, null, 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // We don't support IP addresses
      try (Socket sock = factory.createSocket(InetAddress.getByName(""), 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // We don't support IP addresses
      try (Socket sock = factory.createSocket(InetAddress.getLoopbackAddress(), 0, null, 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // file:// has an empty path component
      try (Socket sock = factory.createSocket("file:", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // file:// has an empty path component
      try (Socket sock = factory.createSocket("file:/", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // file:// has an empty path component
      try (Socket sock = factory.createSocket("file://", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // file://not-absolute is not an absolute path (three slashes needed)
      try (Socket sock = factory.createSocket("file://not-absolute", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // incomplete
      try (Socket sock = factory.createSocket("[", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // encoded; incomplete trailing escape
      try (Socket sock = factory.createSocket("file%3A%2F%2F%", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
  }

  @Test
  public void testURISchemeCeateSocketWithHostnameValidCases() throws Exception {
    AFUNIXSocketFactory.URIScheme factory = new AFUNIXSocketFactory.URIScheme();

    assertThrows(SocketException.class, () -> {
      // file exists (root directory), but is definitely not a unix socket
      try (Socket sock = factory.createSocket("file:///", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // file exists (root directory), but is definitely not a unix socket
      try (Socket sock = factory.createSocket("file://localhost/", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // file exists (root directory), but is definitely not a unix socket
      try (Socket sock = factory.createSocket("[file:///]", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // file exists (root directory), but is definitely not a unix socket
      try (Socket sock = factory.createSocket("[file:///", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // encoded; file exists (root directory), but is definitely not a unix socket
      try (Socket sock = factory.createSocket("file%3A%2F%2F%2F", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
    assertThrows(SocketException.class, () -> {
      // encoded; file exists (root directory), but is definitely not a unix socket
      try (Socket sock = factory.createSocket("file%3A%2F%2Flocalhost%2F", 0)) {
        fail("Should not be reached: " + sock);
      }
    });
  }

  @Test
  public void testSystemProperty() throws Exception {
    AFUNIXSocketFactory.SystemProperty factory = new AFUNIXSocketFactory.SystemProperty();

    String hostnameToConnectTo = System.getProperty("org.newsclub.net.unix.socket.hostname",
        "localhost");
    String existingSocketDefault = System.getProperty("org.newsclub.net.unix.socket.default");
    try {

      System.clearProperty("org.newsclub.net.unix.socket.default");
      assertThrows(IllegalStateException.class, () -> {
        // system property "org.newsclub.net.unix.socket.default" not configured
        try (Socket sock = factory.createSocket(hostnameToConnectTo, 0)) {
          fail("Should not be reached: " + sock);
        }
      });

      System.setProperty("org.newsclub.net.unix.socket.default", "");
      assertThrows(IllegalStateException.class, () -> {
        // system property "org.newsclub.net.unix.socket.default" not configured
        try (Socket sock = factory.createSocket(hostnameToConnectTo, 0)) {
          fail("Should not be reached: " + sock);
        }
      });

      System.setProperty("org.newsclub.net.unix.socket.default", "/");
      assertThrows(SocketException.class, () -> {
        // file exists (root directory), but is definitely not a unix socket
        try (Socket sock = factory.createSocket(hostnameToConnectTo, 0)) {
          fail("Should not be reached: " + sock);
        }
      });
    } finally {
      if (existingSocketDefault == null) {
        System.clearProperty("org.newsclub.net.unix.socket.default");
      } else {
        System.setProperty("org.newsclub.net.unix.socket.default", existingSocketDefault);
      }
    }
  }

  @Test
  public void testFactoryArg() throws Exception {
    String hostnameToConnectTo = System.getProperty("org.newsclub.net.unix.socket.hostname",
        "localhost");
    assertThrows(SocketException.class, () -> {
      AFUNIXSocketFactory.FactoryArg factory = new AFUNIXSocketFactory.FactoryArg("/");
      // file exists (root directory), but is definitely not a unix socket
      try (Socket unused = factory.createSocket(hostnameToConnectTo, 0)) {
        // not reached
      }
    });

    assertThrows(SocketException.class, () -> {
      AFUNIXSocketFactory.FactoryArg factory = new AFUNIXSocketFactory.FactoryArg(new File("/"));
      // file exists (root directory), but is definitely not a unix socket
      try (Socket unused = factory.createSocket(hostnameToConnectTo, 0)) {
        // not reached
      }
    });
  }
}
