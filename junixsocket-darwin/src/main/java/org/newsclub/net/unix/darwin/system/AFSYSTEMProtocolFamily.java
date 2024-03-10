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
package org.newsclub.net.unix.darwin.system;

import java.io.IOException;

import org.newsclub.net.unix.AFProtocolFamily;

/**
 * Describes the protocol families supported by junixsocket-afsystem.
 *
 * @author Christian Kohlschütter
 */
public enum AFSYSTEMProtocolFamily implements AFProtocolFamily {
  /**
   * SYSTEM.
   */
  SYSTEM;

  @Override
  public AFSYSTEMDatagramChannel openDatagramChannel() throws IOException {
    return AFSYSTEMDatagramChannel.open();
  }

  @Override
  public AFSYSTEMServerSocketChannel openServerSocketChannel() throws IOException {
    return AFSYSTEMServerSocketChannel.open();
  }

  @Override
  public AFSYSTEMSocketChannel openSocketChannel() throws IOException {
    return AFSYSTEMSocketChannel.open();
  }
}
