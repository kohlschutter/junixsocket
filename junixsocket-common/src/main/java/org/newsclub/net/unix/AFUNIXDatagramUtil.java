package org.newsclub.net.unix;

import java.net.DatagramPacket;

import com.kohlschutter.annotations.compiletime.ExcludeFromCodeCoverageGeneratedReport;

final class AFUNIXDatagramUtil {
  @ExcludeFromCodeCoverageGeneratedReport
  private AFUNIXDatagramUtil() {
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
