# junixsocket

junixsocket is a Java/JNI library that allows the use of [Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets) from Java.

## Why it's cool

* junixsocket is a small, modular library. Install only what you need.
* In contrast to other implementations, *junixsocket* extends the Java Sockets API (`java.net.Socket`, `java.net.SocketAddress`, etc.)
* Supports *RMI over AF_UNIX*.
* Database support (connect to a local database server via Unix sockets and JDBC).
    * MySQL (provides a custom *AFUNIXDatabaseSocketFactory* for Connector/J).
    * PostgreSQL and others (provides a generic *AFUNIXSocketFactory* with a variety of configuration options).
* Supports [peer credentials](https://kohlschutter.github.io/junixsocket/peercreds.html).
* Supports sending and receiving [file descriptors](https://kohlschutter.github.io/junixsocket/filedescriptors.html).
* Supports the abstract namespace on Linux.
* Supports HTTP over unix socket (using [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd))
* Supports Java 7*, 8, 9, 10, 11, 12, 13, 14, 15.
* Comes with pre-built native libraries for several operating systems and platforms, including
  macOS, Linux, FreeBSD, Solaris and Windows; custom libraries can be built using Maven.
* Supports JPMS/Jigsaw modules.
* Apache 2.0 licensed.

`*` (basic support for Java 7 only, no RMI, no Demos).

## Quick links

 * [Project website](https://kohlschutter.github.io/junixsocket/)
 * [Github project](https://github.com/kohlschutter/junixsocket/)
 * [Changelog](https://kohlschutter.github.io/junixsocket/changelog.html)
 * [Getting started](https://kohlschutter.github.io/junixsocket/quickstart.html)
 * [Demo code](https://kohlschutter.github.io/junixsocket/demo.html) ([Java source](https://kohlschutter.github.io/junixsocket/junixsocket-demo/xref/index.html))
    - Sockets (`org.newsclub.net.unix.demo`)
    - RMI over Unix Sockets (`org.newsclub.net.unix.demo.rmi` and `org.newsclub.net.unix.demo.rmi.services`)
    - MySQL over Unix Sockets  (`org.newsclub.net.mysql.demo`)

  * API Javadocs
    - [The core (common) API](https://kohlschutter.github.io/junixsocket/junixsocket-common/apidocs/org.newsclub.net.unix/org/newsclub/net/unix/package-summary.html)
    - [The RMI-over-Unix-Socket API](https://kohlschutter.github.io/junixsocket/junixsocket-rmi/apidocs/org.newsclub.net.unix.rmi/org/newsclub/net/unix/rmi/package-summary.html)
    
  * [Unix Domain Socket Reference](https://kohlschutter.github.io/junixsocket/unixsockets.html)

## Feature Comparison Matrix

| Project  | License | Java Sockets API | Supports MySQL | Supports RMI | Comments |
| ------------- | --------- | ------------- | ------------- | ------------- | ------------- |
| junixsocket | Apache 2.0 | ✅ Yes | ✅ Yes | ✅ Yes | This project |
| [netty](https://github.com/netty/netty) | Apache 2.0 | ✅ Yes | ❌ No | ❌ No |  |
| [JUDS](http://code.google.com/p/juds/)  | LGPL | ❌ No | ❌ No | ❌ No | |
| J-BUDS  | LGPL | ❌ No | ❌ No | ❌ No | orphaned |
| [gnu.net.local](http://web.archive.org/web/20060702213439/http://www.nfrese.net/software/gnu_net_local/overview.html) | GPL with Classpath exception | ❌ No | ❌ No | ❌ No | orphaned |

## Licensing

junixsocket is released under the Apache 2.0 License.

Commercial support is available through [Kohlschütter Search Intelligence](http://www.kohlschutter.com/).

## Self-test

To verify that the software works as expected on your platform, you can run the selftest program,
which is located in the "junixsocket-dist" distribution package:

```
java -jar junixsocket-selftest-VERSION-jar-with-dependencies.jar 
```

(with VERSION being the corresponding junixsocket version).
