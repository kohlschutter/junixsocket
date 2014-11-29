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
package org.newsclub.net.unix.rmi;

import java.io.IOException;
import java.util.BitSet;

/**
 * A very simple implementation of a {@link PortAssigner}.
 * 
 * @author Christian Kohlschütter
 */
class PortAssignerImpl implements PortAssigner {
  private BitSet ports = new BitSet(1000);

  public PortAssignerImpl() {
  }

  private/* synchronized */int randomPort() {
    int maxRandom = ports.size();

    int port;
    for (int i = 0; i < 3; i++) {
      port = ports.nextClearBit((int) (Math.random() * maxRandom));
      if (port < maxRandom) {
        return port;
      } else {
        maxRandom = port;
        if (maxRandom == 0) {
          break;
        }
      }
    }
    return ports.nextClearBit(0);
  }

  @Override
  public synchronized int newPort() throws IOException {
    int port = randomPort();
    ports.set(port);
    port += AFUNIXRMIPorts.ANONYMOUS_PORT_BASE;
    return port;
  }

  @Override
  public synchronized void returnPort(int port) throws IOException {
    ports.clear(port - AFUNIXRMIPorts.ANONYMOUS_PORT_BASE);
  }
}
