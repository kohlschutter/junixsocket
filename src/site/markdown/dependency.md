# Add junixsocket to your project

## Maven dependencies

Add the following dependency to your Maven project

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-core</artifactId>
      <version>2.1.2</version>
    </dependency>

([See here](customarch.html) how to add support for custom architectures that aren't supported out of the box).
    
If you're going to use RMI over Unix sockets, add the following dependency:
    
    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-rmi</artifactId>
      <version>2.1.2</version>
    </dependency>

If you're going to use the mySQL Connector for Unix sockets, add the following dependency:

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-mysql</artifactId>
      <version>2.1.2</version>
    </dependency>
 
## Gradle
 
 Minimum requirement:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-core:2.1.2'
 
 For RMI support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-rmi:2.1.2'
 
 For MySQL support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-mysql:2.1.2'

## jars only

If you don't use Maven or Gradle, etc., simply download the binary distribution from the
[junixsocket releases page](https://github.com/kohlschutter/junixsocket/releases).

junixsocket-dist comes either as a `.tar.gz` or `.zip` archive. Get either one of them. The jars
are in the `lib` directory.

## JDBC

### MySQL

Make sure that the following jars are on your classpath:

 * junixsocket-core-2.1.2.jar
 * junixsocket-common-2.1.2.jar
 * junixsocket-native-common-2.1.2.jar
 * junixsocket-mysql-2.1.2.jar
 * mysql-connector-java-8.0.13.jar (earlier versions should work, too)
 * (optionally, if you have a custom architecture) junixsocket-native-custom-2.1.2.jar

Use the following connection properties (along with `user`, `password`, and other properties you may have).

| property name | value | remarks |
| ------------- | ----- | ------- |
| `socketFactory` | `org.newsclub.net.mysql.AFUNIXDatabaseSocketFactory` | (1) |
| `junixsocket.file` | `/tmp/mysql.sock` | (2) |
| `sslMode` | `DISABLED` | (3)(4) |

(1) This SocketFactory currently implements the "old" Connector/J SocketFactory interface.
This may change in the future. For the time being, you can use a value of
`org.newsclub.net.mysql.AFUNIXDatabaseSocketFactoryCJ`
to forcibly use the new "CJ"-style SocketFactory.

(2) This is the full path to the MySQL socket. The location on your system may be different. Try `/var/lib/mysql.sock`
if you can't find it in /tmp.

(3) This is optional, and forcibly disables the use of SSL. Since the connection is local, there's
no point in encrypting the communication. Disabling SSL may improve performance.

(4) `sslMode` is only available with mysql-connector-java 8.0.13 or newer. Try `useSSL` with a value of `false`
to disable SSL with older versions of Connector/J.
 
# Compatibility of future versions
 
We have documented some [Compatibility Considerations](compatibility.html), please keep them
in mind before updating your dependencies.
