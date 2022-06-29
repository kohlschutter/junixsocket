# HTTP over Unix sockets

(HTTP over Unix Domain sockets, and practically all other sockets supported by junixsocket, such as AF_TIPC, as well)

## Motivation

Serving Web content doesn't always mean that the serving machine has a working TCP/IP stack, the required network permissions, or simply the need to expose its endpoints externally.

Specifically, serving content via UNIX domain sockets can significantly reduce the exposure of a web server to a variety of security issues.

In other cases, such as with TIPC sockets, you can build a fault-tolerant cluster of HTTP servers with little to no system setup, thanks to the powers of the [TIPC protocol](http://tipc.io/).

## Libraries known to work

### Jetty (HTTP Server and Client)

Custom `AFSocketServerConnector` and `AFSocketClientConnector` classes are provided.

See [junixsocket-jetty](junixsocket-jetty/) for details.

### NanoHTTPD (HTTP Server)

You can simply create `AFServerSocket` instances with a custom `ServerSocketFactory`.

See [NanoHttpdServerDemo](junixsocket-demo/xref/org/newsclub/net/unix/demo/nanohttpd/NanoHttpdServerDemo.html) for an example.

### OkHttp (HTTP Client)

Build your `OkHttpClient` with a `AFSocketFactory.FixedAddressSocketFactory`.

See [OkHttpClientDemo](junixsocket-demo/xref/org/newsclub/net/unix/demo/okhttp/OkHttpClientDemo.html) for an example with AF_UNIX sockets.

See [OkHttpClientTIPCDemo](junixsocket-demo/xref/org/newsclub/net/unix/demo/okhttp/OkHttpClientTIPCDemo.html) for an example with AF_TIPC sockets.

## Libraries known to not work

### JEP 321 HTTP Client (java.net.HTTP)

Specifying an `AFSocketAddress` will compile but fail to establish connections.

Unfortunately, there is currently no way to specify custom Socket factories, and the java.net code heavily depends on implementation-specifics.
