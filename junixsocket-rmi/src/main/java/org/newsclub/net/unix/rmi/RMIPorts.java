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
package org.newsclub.net.unix.rmi;

import org.newsclub.net.unix.AFSocket;

import com.kohlschutter.annotations.compiletime.ExcludeFromCodeCoverageGeneratedReport;

/**
 * Contains some default ports used by junixsocket for RMI-over-AF_UNIX etc.
 *
 * @author Christian Kohlschütter
 * @see AFRMISocketFactory
 * @see AFRMIService
 */
final class RMIPorts {
  /**
   * This is the base for non-inet {@link AFSocket}-related ports To distinguish between TCP/UDP
   * ports and these ports, we use numbers > 65535.
   */
  static final int AF_PORT_BASE = 100000;

  /**
   * This is the port reserved for the default registry.
   */
  static final int DEFAULT_REGISTRY_PORT = 100001;

  /**
   * This is the port reserved for the port assigner.
   *
   * @see AFRMIService
   */
  static final int RMI_SERVICE_PORT = 100002;

  /**
   * This is the base for anonymous ports. Any anonymous port will be higher than this number.
   *
   * @see AFRMIService
   */
  static final int ANONYMOUS_PORT_BASE = 110000;

  /**
   * This port is used to state that the socket path specified in {@link AFRMISocketFactory} points
   * to a socket <em>file</em>, not a directory.
   */
  static final int PLAIN_FILE_SOCKET = Integer.MAX_VALUE;

  @ExcludeFromCodeCoverageGeneratedReport(reason = "unreachable")
  private RMIPorts() {
    throw new UnsupportedOperationException("No instances");
  }
}
