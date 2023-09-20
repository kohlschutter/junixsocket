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

import static org.junit.jupiter.api.Assertions.fail;

import java.net.SocketException;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.server.AFSocketServer;

/**
 * An {@link AFSocketServer} that fails upon detecting an exception.
 *
 * @param <A> The socket address.
 * @author Christian Kohlschütter
 */
abstract class TestingAFSocketServer<A extends AFSocketAddress> extends AFSocketServer<A> {
  public TestingAFSocketServer(A listenAddress) {
    super(listenAddress);
  }

  @Override
  protected void onServingException(AFSocket<? extends A> socket, Exception e) {
    fail(e);
  }

  @Override
  protected void onListenException(Exception e) {
    fail(e);
  }

  @Override
  protected void onSocketExceptionAfterAccept(AFSocket<? extends A> socket, SocketException e) {
    fail(e);
  }

  @Override
  protected void onSocketExceptionDuringAccept(SocketException e) {
    fail(e);
  }
}
