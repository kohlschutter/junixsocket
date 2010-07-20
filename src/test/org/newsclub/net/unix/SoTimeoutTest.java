package org.newsclub.net.unix;

import java.io.IOException;
import java.net.Socket;

import org.junit.Test;

/**
 * Tests {@link Socket#setSoTimeout(int)} behavior.
 * 
 * @see <a href="http://code.google.com/p/junixsocket/issues/detail?id=14">Issue 14</a>
 */
public class SoTimeoutTest extends SocketTestBase {
	public SoTimeoutTest() throws IOException {
		super();
	}

	/**
	 * Triggers a case where {@link Socket#setSoTimeout(int)} fails on some platforms: when the socket is closed.
	 */
	@Test
	public void issue14Fail() throws Exception {
		new ServerThread() {

			@Override
			protected void handleConnection(final Socket sock)
					throws IOException {
				// terminates connection
			}
		};

		AFUNIXSocket sock = connectToServer();

		// Sometimes this test would pass, so let's sleep for a moment
		Thread.yield();

		try {
			sock.setSoTimeout(12000);
			System.err.println("NOTE: Socket#setSoTimeout(int) did not throw an AFUNIXSocketException. This is OK.");
		} catch (AFUNIXSocketException e) {
			// expected, as the socket is actually closed
		}
		sock.close();
	}

	/**
	 * Triggers a regular case where {@link Socket#setSoTimeout(int)} should work.
	 */
	@Test
	public void issue14Pass() throws Exception {
		new ServerThread() {

			@Override
			protected void handleConnection(final Socket sock)
					throws IOException {
				// Let's wait some time for a byte that never gets sent by the client
				sock.getInputStream().read();
			}
		};

		AFUNIXSocket sock = connectToServer();

		sock.setSoTimeout(12000);
		sock.close();
	}
}
