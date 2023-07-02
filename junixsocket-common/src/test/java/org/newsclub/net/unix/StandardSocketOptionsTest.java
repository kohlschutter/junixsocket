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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.SoftAssertions;

/**
 * Tests the {@code Socket#getOption(SocketOption)} API available since Java 9.
 *
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
@SuppressWarnings("PMD.CouplingBetweenObjects")
public abstract class StandardSocketOptionsTest<A extends SocketAddress> extends SocketTestBase<A> {
  private static final Set<SocketOption<?>> IGNORABLE_OPTIONS = Set.of( //
      StandardSocketOptions.IP_TOS, //
      StandardSocketOptions.TCP_NODELAY, //
      StandardSocketOptions.SO_KEEPALIVE //
  );

  private SoftAssertions softAssertions;

  protected StandardSocketOptionsTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @BeforeEach
  public void beforeEach() {
    softAssertions = new SoftAssertions();
  }

  @AfterEach
  public void afterEach() {
    softAssertions.assumePass();
  }

  @Test
  public void testUnconnectedServerSocketOptions() throws Exception {
    try (ServerSocket sock = newServerSocketBindOn(newTempAddress(), true); //
        TestState<ServerSocket> state = new TestStateServerSocket(sock);) {

      // supported
      state.testSocketOption(StandardSocketOptions.SO_RCVBUF, null, 1024, null);
      state.testSocketOption(StandardSocketOptions.SO_REUSEADDR, null, true, true);

      // unsupported; throws an exception
      state.testSocketOption(StandardSocketOptions.IP_MULTICAST_IF, null, NetworkInterface
          .getByIndex(0), null);
      state.testSocketOption(StandardSocketOptions.IP_MULTICAST_LOOP, null, true, null);
      state.testSocketOption(StandardSocketOptions.IP_MULTICAST_TTL, null, 123, null);
      state.testSocketOption(StandardSocketOptions.SO_BROADCAST, null, true, null);
      state.testSocketOption(StandardSocketOptions.SO_REUSEPORT, null, true, null);

      // Java 8 mostly: cannot use on server sockets that are not connected to a socket
      state.testSocketOption(StandardSocketOptions.TCP_NODELAY, null, true, null);
      state.testSocketOption(StandardSocketOptions.SO_SNDBUF, null, 4096, null);
      state.testSocketOption(StandardSocketOptions.SO_LINGER, null, 123, null);
      state.testSocketOption(StandardSocketOptions.SO_KEEPALIVE, null, null, null);
      // Make sure we acknowledge them as "covered" nevertheless
      state.coveredSupportedOptions.addAll(Set.of(StandardSocketOptions.TCP_NODELAY,
          StandardSocketOptions.SO_SNDBUF, StandardSocketOptions.SO_LINGER,
          StandardSocketOptions.SO_KEEPALIVE));

      // always 0
      state.testSocketOption(StandardSocketOptions.IP_TOS, 0, 3, 0);
    }
  }

  @Test
  public void testSocketOptions() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(Socket sock) throws IOException {
          try (TestState<Socket> state = new TestStateSocket(sock)) {
            state.testSocketOption(StandardSocketOptions.SO_REUSEADDR, null, true, true);
            state.testSocketOption(StandardSocketOptions.SO_RCVBUF, null, 8192, null);
            state.testSocketOption(StandardSocketOptions.SO_SNDBUF, null, 8192, null);
            state.testSocketOption(StandardSocketOptions.SO_LINGER, null, 123, null);
            state.testSocketOption(StandardSocketOptions.SO_KEEPALIVE, null, true, null);
            try (OutputStream out = sock.getOutputStream()) {
              out.write(0xAF);
            }
          }
        }
      }; //
          Socket sock = connectTo(serverThread.getServerAddress());
          InputStream in = sock.getInputStream();//
      ) {
        assertEquals(0xAF, in.read());
      }
    });
  }

  abstract class TestState<S extends Closeable> implements Closeable {
    protected final S sock;
    private final Set<SocketOption<?>> coveredSupportedOptions = new HashSet<>();

    protected TestState(S sock) {
      this.sock = sock;
    }

    @SuppressWarnings("null")
    @Override
    public void close() throws AssertionError, IOException {
      Set<SocketOption<?>> sockSupportedOptions = supportedOptions();

      // Ignore these -- they're a standard option we have to silently "support" (by doing nothing)
      coveredSupportedOptions.removeAll(IGNORABLE_OPTIONS);

      for (SocketOption<?> opt : sockSupportedOptions) {
        if (!coveredSupportedOptions.contains(opt)) {
          assumeTrue(false, "Test did not cover supported option: " + opt);
        }
      }
      for (SocketOption<?> opt : coveredSupportedOptions) {
        if (!sockSupportedOptions.contains(opt)) {
          fail(sock.getClass() + "#supportedOptions does not cover supported option: " + opt);
        }
      }
    }

    private <T> void testSocketOption(SocketOption<T> option, T oldValExpected, T setVal,
        T newValExpected) {
      boolean unsupportedIsNotAFailure = (newValExpected == null);
      try {

        T prevVal = checkUnsupported(option, unsupportedIsNotAFailure, () -> {
          return getOption(option);
        });
        if (oldValExpected != null) {
          assertEquals(oldValExpected, prevVal, () -> "Unexpected old value for " + option);
        }
        try {
          assertEquals(sock, checkUnsupported(option, unsupportedIsNotAFailure, () -> {
            return setOption(option, setVal);
          }));

          T newVal = checkUnsupported(option, unsupportedIsNotAFailure, () -> {
            return getOption(option);
          });
          if (newValExpected != null) {
            assertEquals(newValExpected, newVal, () -> "Unexpected new value for " + option);
          }
        } finally {
          // make sure to set old val again after the test
          checkUnsupported(option, unsupportedIsNotAFailure, () -> {
            return setOption(option, prevVal);
          });
        }
      } catch (UnsupportedOperationException e) {
        // already handled
        return;
      }

      coveredSupportedOptions.add(option);
    }

    protected abstract <T> T getOption(SocketOption<T> option) throws IOException;

    protected abstract <T> Closeable setOption(SocketOption<T> option, T value) throws IOException; // NOPMD

    protected abstract Set<SocketOption<?>> supportedOptions() throws IOException;

    protected <R> R checkUnsupported(SocketOption<?> option, boolean unsupportedIsNotAFailure,
        Callable<R> r) {
      try {
        return r.call();
      } catch (UnsupportedOperationException e) {
        // ignore
        if (!unsupportedIsNotAFailure) {
          softAssertions.fail("Unsupported socket option: " + option, e);
        }
        throw e;
      } catch (Exception e) {
        if (unsupportedIsNotAFailure && e.getMessage().toLowerCase(Locale.ENGLISH).contains(
            "support")) {
          // unsupported, not supported, supports only, etc.
        } else {
          softAssertions.fail("Unsupported socket option: " + option, e);
        }
        throw new UnsupportedOperationException(e);
      }
    }
  }

  private class TestStateSocket extends TestState<Socket> {
    TestStateSocket(Socket sock) {
      super(sock);
    }

    @Override
    protected <T> T getOption(SocketOption<T> option) throws IOException {
      return sock.getOption(option);
    }

    @Override
    @SuppressWarnings("PMD.LinguisticNaming")
    protected <T> Closeable setOption(SocketOption<T> option, T value) throws IOException {
      return sock.setOption(option, value);
    }

    @Override
    protected Set<SocketOption<?>> supportedOptions() throws IOException {
      return sock.supportedOptions();
    }
  }

  private class TestStateServerSocket extends TestState<ServerSocket> {
    TestStateServerSocket(ServerSocket sock) {
      super(sock);
    }

    @Override
    protected <T> T getOption(SocketOption<T> option) throws IOException {
      return sock.getOption(option);
    }

    @Override
    @SuppressWarnings("PMD.LinguisticNaming")
    protected <T> Closeable setOption(SocketOption<T> option, T value) throws IOException {
      return sock.setOption(option, value);
    }

    @Override
    protected Set<SocketOption<?>> supportedOptions() throws IOException {
      return sock.supportedOptions();
    }
  }
}
