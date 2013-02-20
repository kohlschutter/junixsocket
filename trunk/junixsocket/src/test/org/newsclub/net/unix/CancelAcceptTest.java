package org.newsclub.net.unix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.junit.Test;

/**
 * Tests breaking out of accept.
 * 
 * @see <a href="http://code.google.com/p/junixsocket/issues/detail?id=6">Issue
 *      6</a>
 */
public class CancelAcceptTest extends SocketTestBase {
    public CancelAcceptTest() throws IOException {
        super();
    }

    private boolean serverSocketClosed = false;

    @Test
    public void issue6test1() throws Exception {
        serverSocketClosed = false;

        final ServerThread st = new ServerThread() {

            @Override
            protected boolean handleConnection(final Socket sock)
                    throws IOException {

                return true;
            }

            @Override
            protected void onServerSocketClose() {
                serverSocketClosed = true;
            }
        };

        AFUNIXSocket sock;
        sock = connectToServer();
        sock.close();
        sock = connectToServer();
        sock.close();

        final ServerSocket servSock = st.getServerSocket();

        assertFalse("ServerSocket should not be closed now", serverSocketClosed
                && !servSock.isClosed());
        servSock.close();
        try {
            sock = connectToServer();
        } catch (final SocketException e) {
            // as expected
        }
        assertTrue("ServerSocket should be closed now", serverSocketClosed
                || servSock.isClosed());

        try {
            sock = connectToServer();
            fail("ServerSocket should have been closed already");
        } catch (final SocketException e) {
            // as expected
        }

    }

}
