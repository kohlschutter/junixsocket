![junixsocket logo](https://user-images.githubusercontent.com/822690/246675372-d1775152-5f5e-4576-8f3d-8445779ea584.png)

[![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/kohlschutter/junixsocket/codeql-analysis.yml?cacheSeconds=60)](https://github.com/kohlschutter/junixsocket/actions/workflows/codeql-analysis.yml) [![Last commit on main](https://img.shields.io/github/last-commit/kohlschutter/junixsocket/main)](https://github.com/kohlschutter/junixsocket/commits/main) [![Maven Central version](https://img.shields.io/maven-central/v/com.kohlschutter.junixsocket/junixsocket)](https://search.maven.org/artifact/com.kohlschutter.junixsocket/junixsocket) [![Apache 2.0 Licensed](https://img.shields.io/github/license/kohlschutter/junixsocket)](https://github.com/kohlschutter/junixsocket/blob/main/NOTICE)

**Users of junixsocket are strongly advised to upgrade to version 2.10.1 or newer ([changelog](https://kohlschutter.github.io/junixsocket/changelog.html))**

# junixsocket

junixsocket is a Java/JNI library that allows the use of
[Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets), and
other address/protocol families (such as [AF_TIPC](http://tipc.io/), AF_VSOCK, and AF_SYSTEM),
from Java.

## Unix sockets API, in Java, AF.

* *junixsocket* is the most complete implementation of AF_UNIX sockets for the Java ecosystem.
* Supports other socket types, such as TIPC (on Linux), VSOCK (on Linux, and certain macOS VMs), and
  AF_SYSTEM (on macOS) as well!
* Comes with pre-built native libraries for most operating systems and platforms, including
  macOS, Linux, Android, Windows, Solaris, FreeBSD, NetBSD, OpenBSD, DragonFlyBSD, AIX, IBM i.
* Additionally, you can build and run junixsocket natively on IBM z/OS (experimental).
* Supports all Java versions since Java 8* (with common AF_UNIX support available for Java 7 and newer)
* Supports both the Java Socket API and NIO (`java.net.Socket`, `java.net.SocketChannel`, etc.)
* Supports streams and datagrams.
* Supports Remote Method Invocation (RMI) over AF_UNIX.
* Supports JDBC database connectors (connect to a local database server via Unix sockets).
    * Generic *AFUNIXSocketFactory* for databases like PostgreSQL
    * Custom socket factory for MySQL Connector/J, as [recommended by Oracle](https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-unix-socket.html)
* Supports [peer credentials](https://kohlschutter.github.io/junixsocket/peercreds.html).
* Supports sending and receiving [file descriptors](https://kohlschutter.github.io/junixsocket/filedescriptors.html).
* Supports the abstract namespace on Linux.
* Supports socketpair, and instantiating socket classes from file descriptors.
* Supports [HTTP over UNIX sockets](https://kohlschutter.github.io/junixsocket/http.html) (using [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd), [OkHttp](https://github.com/square/okhttp), and [jetty](https://github.com/eclipse/jetty.project/)).
* Supports JPMS/Jigsaw modules. The project is modularized so you can install only what you need.
* Supports GraalVM native-image AOT/ahead-of-time compilation (since 2.6.0)
* Provides a selftest package with 300+ tests to ensure compatibility with any target platform.
* No outside dependencies are necessary at runtime.
* Apache 2.0 licensed.

`*` (Tested up to Java 24; basic support for Java 7 was dropped in version 2.5.0 and reintroduced in version 2.8.0).

## Quick links

 * [Project website](https://kohlschutter.github.io/junixsocket/) and [Github project](https://github.com/kohlschutter/junixsocket/)
 * [Changelog](https://kohlschutter.github.io/junixsocket/changelog.html)
 * [Getting started](https://kohlschutter.github.io/junixsocket/quickstart.html)
 * [Demo code](https://kohlschutter.github.io/junixsocket/demo.html) ([Java source](https://kohlschutter.github.io/junixsocket/junixsocket-demo/xref/index.html))
    - Sockets (`org.newsclub.net.unix.demo`)
    - RMI over Unix Sockets (`org.newsclub.net.unix.demo.rmi` and `org.newsclub.net.unix.demo.rmi.services`)
    - MySQL over Unix Sockets (`org.newsclub.net.mysql.demo`)
    - Postgres over Unix Sockets (`org.newsclub.net.unix.demo.jdbc`)
    - Apache Mina (`org.newsclub.net.unix.demo.mina`)
    - NanoHttpd (`org.newsclub.net.unix.demo.nanohttpd`)
    - Netty (`org.newsclub.net.unix.demo.netty`)
    - OkHttp (`org.newsclub.net.unix.demo.okhttp`)
    - SSL (`org.newsclub.net.unix.demo.ssl`)
  * [API Javadocs](https://kohlschutter.github.io/junixsocket/apidocs/)
  * [Unix Domain Socket Reference](https://kohlschutter.github.io/junixsocket/unixsockets.html)
  * [TIPC documentation](https://kohlschutter.github.io/junixsocket/junixsocket-tipc/index.html)
  * [VSOCK documentation](https://kohlschutter.github.io/junixsocket/junixsocket-vsock/index.html)
  * [AF_SYSTEM documentation](https://kohlschutter.github.io/junixsocket/junixsocket-darwin/index.html)

## Licensing

junixsocket is released under the Apache 2.0 License.

Commercial support is available through [Kohlschütter Search Intelligence](http://www.kohlschutter.com/).

## Self-test

To verify that the software works as expected on your platform, you can run the
[junixsocket-selftest](https://kohlschutter.github.io/junixsocket/selftest.html) program, which is
located in the "junixsocket-dist" distribution package, and also released on GitHub.

```
java -jar junixsocket-selftest-VERSION-jar-with-dependencies.jar
```

(with VERSION being the corresponding junixsocket version).

## Maven dependency

To include the core junixsocket functionality in your project, add the following Maven dependency

> **NOTE** Since version 2.4.0, `junixsocket-core` is POM-only (that's why you need to specify
`<type>pom</type>`)

```
<dependency>
  <groupId>com.kohlschutter.junixsocket</groupId>
  <artifactId>junixsocket-core</artifactId>
  <version>2.10.1</version>
  <type>pom</type>
</dependency>
```

While you should definitely pin your dependency to a specific version, you are very much encouraged
to keep updating to the most recent version. Check back frequently.

For more, optional packages (RMI, MySQL, Jetty, TIPC, VSOCK, server, Darwin, SSL, GraalVM, etc.) and
Gradle instructions see [here](https://kohlschutter.github.io/junixsocket/dependency.html)

## Snapshot builds for testing

When you're testing a `-SNAPSHOT` version, make sure that the Sonatype snapshot repository is
enabled in your POM:

```
<repositories>
    <repository>
        <id>sonatype.snapshots</id>
        <name>Sonatype snapshot repository</name>
        <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
        <layout>default</layout>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

To update to the latest SNAPSHOT (which is currently not being built for every commit),
run the following command from within your own project:

```
mvn -U dependency:resolve
```

or (for Gradle)

```
./gradlew refreshVersions
```
