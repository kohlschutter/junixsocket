# junixsocket

junixsocket is a Java/JNI library that allows the use of [Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets) from Java.

## Why it's cool

* In contrast to other implementations, *junixsocket* extends the Java Sockets API (`java.net.Socket, java.net.SocketAddress`, etc.)
* Supports *RMI over AF_UNIX*
* Can connect to local MySQL server via Unix domain sockets (provides a *AFUNIXDatabaseSocketFactory* for Connector/J).
* Apache 2.0 licensed.

## Licensing

junixsocket has been written by Christian Kohlschütter. It is released under the Apache 2.0 License.

Commercial support is available through [http://www.kohlschutter.com/ Kohlschütter Search Intelligence].

## Changelog

### Noteworthy changes

  * _(2014-09-29)_ *junixsocket 2.0.1*

   * **Bugfix:** Added byte array bounds checking to read/write methods.
   * Fix C compiler warnings
   * Remove synchronized byte[] array for single-byte reads/writes.

  * _(2014-09-28)_ *junixsocket 2.0.0*
   * Moved from *Google Code* to *GitHub*.
   * Now uses Maven as the build system, code is distributed to the *Maven Central* repository.
   * C code is built using *nar-maven-plugin*
   * JNI libraries are loaded using *native-lib-loader*

See the commit log for details.

For 1.x releases, please see [https://code.google.com/p/junixsocket](https://code.google.com/p/junixsocket).

## Documentation

For now, please refer to the [Wiki on Google Code](http://code.google.com/p/junixsocket/w/list). 

Quick links:
 * [Getting Started](http://code.google.com/p/junixsocket/wiki/GettingStarted)
 * [Socket Demo](http://code.google.com/p/junixsocket/source/browse/#svn/trunk/junixsocket/src/demo/org/newsclub/net/unix/demo)
 * [RMI Demo](http://code.google.com/p/junixsocket/source/browse/#svn/trunk/junixsocket/src/demo/org/newsclub/net/unix/demo/rmi)
 * [MySQL Socket Demo](http://code.google.com/p/junixsocket/wiki/ConnectingToMySQL)
 * [API Javadocs](http://junixsocket.googlecode.com/svn/trunk/junixsocket/javadoc/index.html)

## Related Work

 * [JUDS](http://code.google.com/p/juds/) (LGPL, no RMI, not using Java Sockets API)
 * J-BUDS (LGPL, no RMI, not using Java Sockets API, orphaned)
 * gnu.net.local (GPL with Classpath exception, no RMI, not using Java Sockets API, orphaned) -- [Archive mirror](http://web.archive.org/web/20060702213439/http://www.nfrese.net/software/gnu_net_local/overview.html).