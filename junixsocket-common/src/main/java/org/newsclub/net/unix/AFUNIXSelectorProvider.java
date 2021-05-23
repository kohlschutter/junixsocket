/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

final class AFUNIXSelectorProvider extends SelectorProvider {
  private static final AFUNIXSelectorProvider INSTANCE = new AFUNIXSelectorProvider();

  private AFUNIXSelectorProvider() {
    super();
    // TODO Auto-generated constructor stub
  }

  public static AFUNIXSelectorProvider getInstance() {
    return INSTANCE;
  }

  @Override
  public DatagramChannel openDatagramChannel() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Pipe openPipe() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AbstractSelector openSelector() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServerSocketChannel openServerSocketChannel() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public SocketChannel openSocketChannel() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

}
