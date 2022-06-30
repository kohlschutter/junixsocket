# junixsocket-jetty

Use junixsocket sockets in [Jetty](https://www.eclipse.org/jetty/) 9.4 or newer, with Java 8 or newer.

[junixsocket](https://kohlschutter.github.io/junixsocket/) is an Apache 2.0-licensed Java/JNI library that allows the use of
[Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets), and
other address/protocol families (such as [TIPC](http://tipc.io/)), from Java.

## Provided functionality and supported Jetty versions

* `AFSocketServerConnector` allows serving content via junixsocket-provided sockets. The implementation should work with jetty versions 9.4.12 or newer.

* `AFSocketClientConnector` allows requesting content via junixsocket-provided sockets. The implementation should work with jetty versions 10.0.8 or newer.

Both connectors have been tested with Jetty 9.4, 10, and 11.

Please file a [New Issue](https://github.com/kohlschutter/junixsocket/issues) if you encounter any problems.

## Jetty Fundamentals

see Jetty's programming guide on how to use ServerConnectors and ClientConnectors:

* [Jetty 11 Programming Guide](https://www.eclipse.org/jetty/documentation/jetty-11/programming-guide/index.html)
* [Jetty 10 Programming Guide](https://www.eclipse.org/jetty/documentation/jetty-10/programming-guide/index.html)

## AFSocketServerConnector

Similar to how you would use a regular ServerConnector, instantiate `AFSocketServerConnector` as follows:

    AFSocketServerConnector connector = new AFSocketServerConnector(server, acceptors, selectors,
        new HttpConnectionFactory());
    AFSocketAddr afAddr = ...; // e.g., AFUNIXSocketAddress.of(new File("/tmp/sock));
    connector.setListenSocketAddress(afAddr);
    // (optional) try to automatically stop server if another instance reuses our address
    connector.setMayStopServer(true);

    server.addConnector(connector);

## AFSocketClientConnector

Similar to how you would use the JEP 380-based ClientConnector, simply instantiate it as follows:

	ClientConnector clientConnector = AFSocketClientConnector.withSocketAddress(afAddr);
	
For example, for a UNIX domain socket at path `/tmp/socket`, use:

	AFSocketClientConnector.withSocketAddress(AFUNIXSocketAddress.of(new File("/tmp/socket")));

## More examples

See [junixsocket-jetty's unit tests](https://kohlschutter.github.io/junixsocket/junixsocket-jetty/xref-test/index.html)
