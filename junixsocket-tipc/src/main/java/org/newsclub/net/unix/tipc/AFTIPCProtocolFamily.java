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
package org.newsclub.net.unix.tipc;

import java.io.IOException;

import org.newsclub.net.unix.AFProtocolFamily;

/**
 * Describes the protocol families supported by junixsocket-tipc.
 *
 * @author Christian Kohlschütter
 */
public enum AFTIPCProtocolFamily implements AFProtocolFamily {
  /**
   * TIPC.
   */
  TIPC;

  @Override
  public AFTIPCDatagramChannel openDatagramChannel() throws IOException {
    return AFTIPCDatagramChannel.open();
  }

  @Override
  public AFTIPCServerSocketChannel openServerSocketChannel() throws IOException {
    return AFTIPCServerSocketChannel.open();
  }

  @Override
  public AFTIPCSocketChannel openSocketChannel() throws IOException {
    return AFTIPCSocketChannel.open();
  }
}
