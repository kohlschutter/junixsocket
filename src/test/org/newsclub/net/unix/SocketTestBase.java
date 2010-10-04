package org.newsclub.net.unix;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Some base functionality for socket tests.
 * 
 * @author Christian Kohlschuetter
 */
abstract class SocketTestBase {
	private final AFUNIXSocketAddress serverAddress;

	public SocketTestBase() throws IOException {
		this.serverAddress = new AFUNIXSocketAddress(getSocketFile());
	}

	private File getSocketFile() {
		String explicitFile = System
				.getProperty("org.newsclub.net.unix.testsocket");
		if (explicitFile != null) {
			return new File(explicitFile);
		} else {
			return new File(new File(System.getProperty("java.io.tmpdir")),
					"junixsocket-test.sock");
		}
	}

	protected AFUNIXServerSocket startServer() throws IOException {
		final AFUNIXServerSocket server = AFUNIXServerSocket.newInstance();
		server.bind(serverAddress);
		return server;
	}

	protected AFUNIXSocket connectToServer() throws IOException {
		return AFUNIXSocket.connectTo(serverAddress);
	}

	protected abstract class ServerThread extends Thread {
		private final AFUNIXServerSocket serverSocket;
		private Exception exception = null;

		protected ServerThread() throws IOException {
			serverSocket = startServer();
			setDaemon(true);
			start();
		}

		protected abstract boolean handleConnection(final Socket sock)
				throws IOException;
		
		protected void onServerSocketClose() {
		}
		
		public ServerSocket getServerSocket() {
			return serverSocket;
		}

		public final void run() {
			try {
				boolean loop = true;
				try {
					while (loop) {
						Socket sock = serverSocket.accept();
						try {
							loop = handleConnection(sock);
						} finally {
							sock.close();
						}
					}
				} finally {
					onServerSocketClose();
					serverSocket.close();
				}
			} catch (IOException e) {
				exception = e;
			}
		}

		public void checkException() throws Exception {
			if (exception != null) {
				throw exception;
			}
		}
	}

	protected void sleepFor(final int ms) throws IOException {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			throw (IOException) new IOException(e.getMessage()).initCause(e);
		}
	}
}
