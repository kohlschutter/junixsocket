package org.newsclub.net.unix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public final class AFUNIXSocketAddressTest {

    @Test
    public void testPort() throws IOException {
        assertEquals(0,
                new AFUNIXSocketAddress(new File("/tmp/whatever")).getPort());
        assertEquals(123, new AFUNIXSocketAddress(new File("/tmp/whatever"),
                123).getPort());
        assertEquals(44444, new AFUNIXSocketAddress(new File("/tmp/whatever"),
                44444).getPort());
        try {
            new AFUNIXSocketAddress(new File("/tmp/whatever"), -1);
            fail("Expected IllegalArgumentException for illegal port");
        } catch (final IllegalArgumentException e) {
            // expected
        }
        try {
            new AFUNIXSocketAddress(new File("/tmp/whatever"), 65536);
        } catch (final IllegalArgumentException e) {
            fail("AFUNIXSocketAddress supports ports larger than 65535");
        }
    }
}
