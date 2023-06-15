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

import java.net.DatagramPacket;

import com.kohlschutter.annotations.compiletime.ExcludeFromCodeCoverageGeneratedReport;

/**
 * Helper methods for datagrams, but mostly for testing.
 *
 * @author Christian Kohlschütter
 */
public final class AFDatagramUtil {
  @ExcludeFromCodeCoverageGeneratedReport(reason = "unreachable")
  private AFDatagramUtil() {
    throw new IllegalStateException("No instances");
  }

  public static DatagramPacket datagramWithCapacityAndPayload(byte[] payload) {
    return new DatagramPacket(payload, payload.length);
  }

  public static DatagramPacket datagramWithCapacityAndPayload(int capacity, byte[] payload) {
    if (capacity < payload.length) {
      throw new IllegalArgumentException("data exceeds capacity");
    } else if (capacity == payload.length) {
      return new DatagramPacket(payload, payload.length);
    }
    byte[] buf = new byte[capacity];
    System.arraycopy(payload, 0, buf, 0, payload.length);

    DatagramPacket dp = new DatagramPacket(buf, buf.length);
    dp.setLength(payload.length);
    return dp;
  }

  public static void expandToCapacity(DatagramPacket dp) {
    synchronized (dp) {
      byte[] data = dp.getData();
      dp.setLength(data.length - dp.getOffset());
    }
  }

  public static DatagramPacket datagramWithCapacity(int i) {
    return new DatagramPacket(new byte[i], i);
  }
}
