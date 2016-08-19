# junixsocket

This project is a fork of [Christian Kohlschütter's junixsocket](https://github.com/fiken/junixsocket). The original project seems to be dead, and we've been unable to get in touch with him. This fork is compatible with pgjdbc (PostgreSQL) and contains some performance improvements.

junixsocket is a Java/JNI library that allows the use of [Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets) from Java.

## Why it's cool

* In contrast to other implementations, *junixsocket* extends the Java Sockets API (`java.net.Socket, java.net.SocketAddress`, etc.)
* Supports *RMI over AF_UNIX*
* Can connect to local MySQL server via Unix domain sockets (provides a *AFUNIXDatabaseSocketFactory* for Connector/J).
* Apache 2.0 licensed.

## Quick start
Add the following dependencies to your project:
```xml
<dependency>
  <groupId>no.fiken.oss.junixsocket</groupId>
  <artifactId>junixsocket-common</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>no.fiken.oss.junixsocket</groupId>
  <artifactId>junixsocket-native-common</artifactId>
  <version>1.0.0</version>
</dependency>
```
Connect to a socket:
```java
AFUNIXSocket s = AFUNIXSocket.newInstance();
s.connect(new AFUNIXSocketAddress(new File(path)));
//Use like an ordinary socket
```

## Licensing

It is released under the Apache 2.0 License.

### Noteworthy changes

  * _(2016-08-18)_ *junixsocket 1.0.0*
    * Increased performance when reading data to large byte-array (prevent array-copy)
    * Added workaround so it's possible to use this library with pgjdbc (PostgreSQL)
    * Temporarily removed Mac and 32bit-support (we'll hopefully fix this in a later release)

See the commit log for details.

