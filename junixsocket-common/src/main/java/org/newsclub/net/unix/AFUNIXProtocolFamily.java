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
package org.newsclub.net.unix;

import java.io.IOException;

/**
 * Describes the protocol family supported by {@link AFUNIXSocketAddress} etc.
 *
 * @author Christian Kohlschütter
 */
public enum AFUNIXProtocolFamily implements AFProtocolFamily {
  /**
   * UNIX domain.
   */
  UNIX;

  @Override
  public AFDatagramChannel<?> openDatagramChannel() throws IOException {
    return AFUNIXDatagramChannel.open();
  }

  @Override
  public AFServerSocketChannel<?> openServerSocketChannel() throws IOException {
    return AFUNIXServerSocketChannel.open();
  }

  @Override
  public AFSocketChannel<?> openSocketChannel() throws IOException {
    return AFUNIXSocketChannel.open();
  }
}
