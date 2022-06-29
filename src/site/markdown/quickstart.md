# Basic Usage

junixsocket supports the [Java Socket API](https://docs.oracle.com/javase/8/docs/api/java/net/Socket.html) and the [Java NIO SocketChannel API](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SocketChannel.html).

## Adding junixsocket to your project

[See here](dependency.html) how to add junixsocket to your project, whether you use a dependency management system, or not.

Be sure to read the [compatibility considerations](compatibility.html), which also describes how to run a comprehensive self-test on your target environments.

## General remarks

`AFSocket` and `AFServerSocket` work just like their regular Java `AF_INET` (TCP/IP) counterparts — they extend `java.net.Socket` and `java.net.ServerSocket` respectively. The same goes for `AFSocketChannel` and `AFServerSocketChannel`, as well as `AFSocketFactory`.

These classes themselves are extended for the particular protocol implementations (for example, UNIX domain socket classes have the prefix _AFUNIX_: `AFUNIXSocket`, `AFUNIXServerSocket`, etc.).

The socket addresses are subclasses of `AFSocketAddress`, such as `AFUNIXSocketAddress` (for `AF_UNIX` domain sockets) and `AFTIPCSocketAddress` (for `AF_TIPC` sockets).

Several helper methods simplify using junixsocket versus using the standard Java API.
Not everything can be covered in this quick start document. Be sure to explore the API via Javadoc and by browsing the source code.

## Working with AF_UNIX domain sockets

### Connecting to an existing AF_UNIX Socket

    File socketFile = new File("/path/to/your/socket");
    AFUNIXSocket sock = AFUNIXSocket.newInstance();
    sock.connect(AFUNIXSocketAddress.of(socketFile));

### Creating a new AF_UNIX Server Socket

    File socketFile = new File("/path/to/your/socket");
    AFUNIXServerSocket server = AFUNIXServerSocket.newInstance();
    server.bind(AFUNIXSocketAddress.of(socketFile));

### Working with AF_UNIX SocketChannels

	AFUNIXSelectorProvider provider = AFUNIXSelectorProvider.provider();
	AFUNIXSocketChannel sc = provider.openSocketChannel();
	sc.connect(AFUNIXSocketAddress.of(new File("/tmp/test.sock")));
	// (work with SocketChannel API)

	AFUNIXSocket socket = sc.socket(); // this always succeeds [1]
	// (work with Socket API)

	AFUNIXSocket sock = ...;
	sock.getChannel(); // this always succeeds as well [1]

	AbstractSelector selector = provider.openSelector();
	// work with Selector API

[1] Unlike regular Java SocketChannels, you can use the SocketChannel and Socket API interchangeably.

## Complete Examples

junixsocket provides [working demos](junixsocket-demo/xref/index.html) for:

  * Sockets (package `org.newsclub.net.unix.demo`)
  * RMI over Unix Sockets (packages `org.newsclub.net.unix.demo.rmi` and `org.newsclub.net.unix.demo.rmi.services`)
  * MySQL over Unix Sockets  (package `org.newsclub.net.mysql.demo`), also see how to [add junixsocket to your MySQL project](dependency.html)

You can even [run the demos from the command line](demo.html) without having to compile anything.

## Caveats

### General considerations

#### Ports, InetSocketAddress

Whereas TCP/IP sockets point to ports at particular Internet addresses, AF_UNIX sockets point to special files on the local file system. The notion of "port" is not necessary in this case.

However, junixsocket supports the concept of "ports" for `AFUNIXSocketAddress` etc., in order to enable features like Java RMI. It does so by deliberately extending `InetSocketAddress` for all `AFSocketAddress` implementations.

Port support may also come handy in other situations, especially when an existing application expects a particular port on a Socket. Please be aware that specifying a particular port number may have no effect for non-RMI connections.

### AF_UNIX domain sockets, AFUNIXSocketAddress & co.

#### Maximum path length

At the system-level, the maximum length of the pathname referring to a Unix domain socket is restricted and may vary from one OS to another. Currently, junixsocket limits the maximum pathname length to 103 bytes (this may mean fewer characters, depending on the encoding). If the maximum length is exceeded an Exception is thrown.

It is therefore recommended to use short pathnames whenever possible.

#### Security

Unix domain server sockets are created with read-write privileges for everybody on the system, just like TCP/IP sockets are accessible for local users.

If you want to restrict access to a particular user or group, simply create the socket in a directory that has proper access restrictions.

### Other socket implementations

While most platforms that support junixsocket inherently support `AF_UNIX` domain sockets, protocols like `AF_TIPC` are not available everywhere.

However, junixsocket guarantees that you are available to instantiate a corresponding `AFSocketAddress` (e.g., `AFTIPCSocketAddress`) on all platforms.
