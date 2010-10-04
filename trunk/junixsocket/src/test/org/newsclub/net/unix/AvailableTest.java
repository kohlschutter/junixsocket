package org.newsclub.net.unix;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.junit.Test;

public class AvailableTest extends SocketTestBase {

    public AvailableTest() throws IOException {
        super();
    }

    private final int bytesSent = 23;
    private final int timeToSleep = 100;

    private void receiveBytes(final Socket sock, final int expected)
            throws IOException {
        InputStream in = sock.getInputStream();

        int toExpect = expected;
        
        char firstChar = 'A';
        
        int available = in.available();
        if (available == 0 && expected != 0) {
            // this may happen, and it's ok.
            int r = in.read();
            assertEquals(
                    "Available returned 0, so we tried to read the first byte (which should be 65=='A')",
                    'A', r);
            
            // as we have already read one byte, we now expect one byte less
            toExpect--;
            
            available = in.available();
            
            firstChar = 'B';
        }
        assertEquals(toExpect, available);
        byte[] buf = new byte[expected];
        int numRead = in.read(buf);
        assertEquals(toExpect, numRead);
        
        for(int i=0;i<numRead;i++) {
            assertEquals(firstChar+i, buf[i] & 0xFF);
        }

        assertEquals(0, in.available());
    }

    private void sendBytes(final Socket sock) throws IOException {
        OutputStream out = sock.getOutputStream();
        byte[] buf = new byte[bytesSent];
        for (int i = 0; i < bytesSent; i++) {
            buf[i] = (byte) (i + 'A');
        }
        out.write(buf);
        out.flush();
    }

    /**
     * Tests if {@link InputStream#available()} works as expected. The server
     * sends 23 bytes. The client waits for 100ms. After that, the client should
     * be able to read exactly 23 bytes without blocking. Then, we try the
     * opposite direction.
     * 
     * @throws Exception
     */
    @Test(timeout = 2000)
    public void testAvailableAtClient() throws Exception {

        ServerThread serverThread = new ServerThread() {

            @Override
            protected boolean handleConnection(final Socket sock)
                    throws IOException {
                sendBytes(sock);
                sleepFor(timeToSleep);
                receiveBytes(sock, bytesSent);
                
                return false;
            }
        };

        AFUNIXSocket sock = connectToServer();
        sleepFor(timeToSleep);
        receiveBytes(sock, bytesSent);
        sendBytes(sock);
        sock.close();

        serverThread.checkException();
    }

    /**
     * Tests if {@link InputStream#available()} works as expected. The client
     * sends 23 bytes. The server waits for 100ms. After that, the server should
     * be able to read exactly 23 bytes without blocking. Then, we try the
     * opposite direction.
     * 
     * @throws Exception
     */
    @Test(timeout = 2000)
    public void testAvailableAtServer() throws Exception {

        ServerThread serverThread = new ServerThread() {

            @Override
            protected boolean handleConnection(final Socket sock)
                    throws IOException {
                sleepFor(timeToSleep);
                receiveBytes(sock, bytesSent);
                sendBytes(sock);
                
                return false;
            }
        };

        AFUNIXSocket sock = connectToServer();
        sendBytes(sock);
        sleepFor(timeToSleep);

        receiveBytes(sock, bytesSent);
        sock.close();

        serverThread.checkException();
    }
}
