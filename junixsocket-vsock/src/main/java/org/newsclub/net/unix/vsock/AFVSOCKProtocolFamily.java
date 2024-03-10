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
package org.newsclub.net.unix.vsock;

import java.io.IOException;

import org.newsclub.net.unix.AFProtocolFamily;

/**
 * Describes the protocol families supported by junixsocket-vsock.
 *
 * @author Christian Kohlschütter
 */
public enum AFVSOCKProtocolFamily implements AFProtocolFamily {
  /**
   * VSOCK.
   */
  VSOCK;

  @Override
  public AFVSOCKDatagramChannel openDatagramChannel() throws IOException {
    return AFVSOCKDatagramChannel.open();
  }

  @Override
  public AFVSOCKServerSocketChannel openServerSocketChannel() throws IOException {
    return AFVSOCKServerSocketChannel.open();
  }

  @Override
  public AFVSOCKSocketChannel openSocketChannel() throws IOException {
    return AFVSOCKSocketChannel.open();
  }
}
