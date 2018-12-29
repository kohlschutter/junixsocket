# junixsocket

junixsocket is a Java/JNI library that allows the use of [Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets) from Java.

## Why it's cool

* In contrast to other implementations, *junixsocket* extends the Java Sockets API (`java.net.Socket`, `java.net.SocketAddress`, etc.)
* Supports *RMI over AF_UNIX*.
* Can connect to local MySQL server via Unix domain sockets (provides a *AFUNIXDatabaseSocketFactory* for Connector/J).
* Supports Java 8, 9, 10, 11.
* Comes with pre-built native libraries for macOS and Linux; custom libraries can be built using Maven.
* Supports Jigsaw modules.
* Apache 2.0 licensed.

## Licensing

junixsocket is released under the Apache 2.0 License.

Commercial support is available through [Kohlschütter Search Intelligence](http://www.kohlschutter.com/).

## Quick links

 * [Project website](https://kohlschutter.github.io/junixsocket/)
 * [Changelog](https://kohlschutter.github.io/junixsocket/changelog.html)
 * [Getting started](https://kohlschutter.github.io/junixsocket/quickstart.html)
 * [Demo code](https://kohlschutter.github.io/junixsocket/demo.html) ([Java source](https://kohlschutter.github.io/junixsocket/junixsocket-demo/xref/index.html))
    - Sockets (`org.newsclub.net.unix.demo`)
    - RMI over Unix Sockets (`org.newsclub.net.unix.demo.rmi` and `org.newsclub.net.unix.demo.rmi.services`)
    - MySQL over Unix Sockets  (`org.newsclub.net.mysql.demo`)
  * API Javadocs
    - [The core (common) API](https://kohlschutter.github.io/junixsocket/junixsocket-common/apidocs/org.newsclub.net.unix/org/newsclub/net/unix/package-summary.html)
    - [The RMI-over-Unix-Socket API](https://kohlschutter.github.io/junixsocket/junixsocket-rmi/apidocs/org.newsclub.net.unix.rmi/org/newsclub/net/unix/rmi/package-summary.html)

## Feature Comparison Matrix

| Project  | License | Java Sockets API | Supports MySQL | Supports RMI | Comments |
| ------------- | --------- | ------------- | ------------- | ------------- | ------------- |
| junixsocket | Apache 2.0 | ✅ Yes | ✅ Yes | ✅ Yes | This project |
| [netty](https://github.com/netty/netty) | Apache 2.0 | ✅ Yes | ❌ No | ❌ No |  |
| [JUDS](http://code.google.com/p/juds/)  | LGPL | ❌ No | ❌ No | ❌ No | |
| J-BUDS  | LGPL | ❌ No | ❌ No | ❌ No | orphaned |
| [gnu.net.local](http://web.archive.org/web/20060702213439/http://www.nfrese.net/software/gnu_net_local/overview.html) | GPL with Classpath exception | ❌ No | ❌ No | ❌ No | orphaned |
