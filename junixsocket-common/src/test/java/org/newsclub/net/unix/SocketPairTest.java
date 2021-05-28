package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

public class SocketPairTest {
  @Test
  public void testSocketPair() throws Exception {
    Entry<AFUNIXSocketChannel, AFUNIXSocketChannel> pair = AFUNIXSelectorProvider.provider()
        .openSocketChannelPair();

    AFUNIXSocketChannel sc1 = pair.getKey();
    AFUNIXSocketChannel sc2 = pair.getValue();

    assertTrue(sc1.isConnected());
    assertTrue(sc2.isConnected());

    assertEquals(sc1.getPeerCredentials(), sc2.getPeerCredentials());

    ByteBuffer bb = ByteBuffer.allocate(4096);
    bb.putInt(0x04030201);
    bb.flip();
    sc1.write(bb);

    ByteBuffer bb2 = ByteBuffer.allocate(4096);
    sc2.read(bb2);
    bb2.flip();
    assertEquals(0x04030201, bb2.getInt());
  }
}
