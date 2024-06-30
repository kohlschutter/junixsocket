package org.newsclub.net.unix.domain;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.newsclub.net.unix.AFUNIXServerSocketChannel;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.AFUNIXServerSocket;


import java.io.IOException;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

public class InterruptIssue158Test {

	private static final Path SOCKET_PATH = Path.of("/", "tmp", "test_socket");
	private static final AFUNIXSocketAddress SOCKET_ADDR;

	static {
		try {
			SOCKET_ADDR = AFUNIXSocketAddress.of(SOCKET_PATH);
		} catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<Arguments> clientProvider() {
		return List.of(
			socket(false, AFUNIXSocket::newInstance, s -> s.connect(SOCKET_ADDR), SocketException.class, AFUNIXSocket::isClosed),
			socket(true, () -> AFUNIXSocket.connectTo(SOCKET_ADDR), s -> s.getInputStream().read(), SocketException.class, AFUNIXSocket::isClosed),
			socket(true, () -> AFUNIXSocket.connectTo(SOCKET_ADDR), s -> s.getOutputStream().write(10), SocketException.class, AFUNIXSocket::isClosed),

			socket(false, AFUNIXSocketChannel::open, s -> s.connect(SOCKET_ADDR), ClosedByInterruptException.class, s -> !s.isOpen()),
			socket(
				true,
				InterruptIssue158Test::connectSocketChannel,
				s -> s.read(ByteBuffer.allocate(1)),
				ClosedByInterruptException.class,
				s -> !s.isOpen()
			),
			socket(
				true,
				InterruptIssue158Test::connectSocketChannel,
				s -> s.write(ByteBuffer.allocate(1)),
				ClosedByInterruptException.class,
				s -> !s.isOpen()
			)
		);
	}

	private static List<Arguments> serverProvider() {
		return List.of(
			serverSocket(() -> AFUNIXServerSocket.bindOn(SOCKET_ADDR), AFUNIXServerSocket::accept, SocketException.class, AFUNIXServerSocket::isClosed),
			serverSocket(InterruptIssue158Test::bindServerSocketChannel, AFUNIXServerSocketChannel::accept, ClosedByInterruptException.class, s -> !s.isOpen())
		);
	}


	@ParameterizedTest
	@MethodSource("clientProvider")
	<T extends AutoCloseable> void testClientInterruption(
		boolean acceptConnections,
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) throws Throwable {
		withServer(
			acceptConnections,
			() -> testSocketInterruption(false, socket, blockingOp, expectedException, closeCheck)
		);
	}

	@ParameterizedTest
	@MethodSource("clientProvider")
	<T extends AutoCloseable> void testClientInterruptionWithDelay(
		boolean acceptConnections,
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) throws Throwable {
		withServer(
			acceptConnections,
			() -> testSocketInterruption(true, socket, blockingOp, expectedException, closeCheck)
		);
	}

	@ParameterizedTest
	@MethodSource("serverProvider")
	<T extends AutoCloseable> void testServerInterruption(
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) throws Throwable {
		try {
			testSocketInterruption(
				false,
				socket,
				blockingOp,
				expectedException,
				closeCheck
			);
		} finally {
			Files.deleteIfExists(SOCKET_PATH);
		}
	}

	@ParameterizedTest
	@MethodSource("serverProvider")
	<T extends AutoCloseable> void testServerInterruptionWithDelay(
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) throws Throwable {
		try {
			testSocketInterruption(
				true,
				socket,
				blockingOp,
				expectedException,
				closeCheck
			);
		} finally {
			Files.deleteIfExists(SOCKET_PATH);
		}
	}


	<T extends AutoCloseable> void testSocketInterruption(
		boolean delay,
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) throws Throwable {
		var exceptionHolder = new AtomicReference<Throwable>();
		var ready = new CountDownLatch(1);
		var t = Thread.ofVirtual()
			.start(() -> exceptionHolder.set(runOperation(
				ready,
				socket,
				blockingOp,
				expectedException,
				closeCheck
			)));

		ready.await();
		if (delay) {
			Thread.sleep(500);
		}
		t.interrupt();
		if (!t.join(Duration.of(1, ChronoUnit.SECONDS))) {
			throw new RuntimeException("Thread failed to terminate after interrupt");
		}
		var thrownException = exceptionHolder.get();
		if (thrownException != null) {
			throw thrownException;
		}
	}

	private static void withServer(boolean acceptConnections, ThrowingRunnable func) throws Throwable {
		try(var serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
			serverSocket.bind(UnixDomainSocketAddress.of(SOCKET_PATH));
			Thread serverThread = null;
			if (acceptConnections) {
				serverThread = Thread.ofPlatform()
					.daemon(true)
					.start(() -> {
						var clients = new ArrayList<SocketChannel>();
						while (serverSocket.isOpen()) {
							try {
								var socket = serverSocket.accept();
								clients.add(socket);
							} catch (ClosedChannelException e) {
								return;
							} catch (IOException e) {
								throw new RuntimeException("Unable to accept socket ", e);
							} finally {
								for (var client : clients) {
									try {
										client.close();
									} catch (IOException ignored) {
									}
								}
							}
						}
					});
			}
			try {
				func.run();
			} finally {
				serverSocket.close();
				if (serverThread != null) {
					serverThread.join();
				}
			}
		} finally {
			Files.deleteIfExists(SOCKET_PATH);
		}
	}

	<T extends AutoCloseable> Throwable runOperation(
		CountDownLatch ready,
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) {
		try {
			var sock = socket.get();
			ready.countDown();
			try {
				blockingOp.accept(sock);
			} catch (Exception e) {
				assertAll(
					() -> assertInstanceOf(expectedException, e, "Socket exception"),
					() -> assertTrue(Thread.interrupted(), "Thread interrupted"),
					() -> assertTrue(closeCheck.test(sock), "Socket closed")
				);
			} finally {
				ready.countDown();
				if (sock != null) {
					try {
						sock.close();
					} catch (Exception e) {
						throw new RuntimeException("Unable to clean up socket", e);
					}
				}
			}
		} catch (Throwable e) {
			return e;
		}
		return null;
	}


	private static <T> Arguments socket(
		boolean acceptConnections,
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) {
		return Arguments.of(acceptConnections, socket, blockingOp, expectedException, closeCheck);
	}

	private static <T> Arguments serverSocket(
		IOSupplier<T> socket,
		IOConsumer<T> blockingOp,
		Class<?> expectedException,
		Predicate<T> closeCheck
	) {
		return Arguments.of(socket, blockingOp, expectedException, closeCheck);
	}

	private static AFUNIXSocketChannel connectSocketChannel() throws IOException {
		var socket = AFUNIXSocketChannel.open();
		socket.connect(SOCKET_ADDR);
		return socket;
	}

	private static AFUNIXServerSocketChannel bindServerSocketChannel() throws IOException {
		var socket = AFUNIXServerSocketChannel.open();
		socket.bind(SOCKET_ADDR);
		return socket;
	}

	private interface IOSupplier<T> {
		T get() throws IOException;
	}

	private interface IOConsumer<T> {
		void accept(T t) throws IOException;
	}

	private interface ThrowingRunnable {
		void run() throws Throwable;
	}
}