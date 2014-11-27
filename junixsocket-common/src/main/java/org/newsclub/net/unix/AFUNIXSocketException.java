/**
 * junixsocket
 *
 * Copyright (c) 2009,2014 Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.net.SocketException;

/**
 * Something went wrong with the communication to a Unix socket.
 * 
 * @author Christian Kohlschütter
 */
public class AFUNIXSocketException extends SocketException {
  private static final long serialVersionUID = 1L;
  private final String socketFile;

  public AFUNIXSocketException(String reason) {
    this(reason, (String) null);
  }

  public AFUNIXSocketException(String reason, final Throwable cause) {
    this(reason, (String) null);
    initCause(cause);
  }

  public AFUNIXSocketException(String reason, final String socketFile) {
    super(reason);
    this.socketFile = socketFile;
  }

  @Override
  public String toString() {
    if (socketFile == null) {
      return super.toString();
    } else {
      return super.toString() + " (socket: " + socketFile + ")";
    }
  }
}
