# Add junixsocket to your project

Below are instructions how to add junixsocket via Maven, Gradle, or plain JAR files to your project.

There are also examples how to add junixsocket to your JDBC connection, so you can connect to your
database via unix sockets.

## Maven dependencies

Add the following dependency to your Maven project

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-core</artifactId>
      <version>2.5.1</version>
      <type>pom</type>
    </dependency>

> **NOTE:** In junixsocket versions older than 2.4.0, the `<type>pom</type>` declaration must be omitted.

[See here](customarch.html) how to add support for custom architectures that aren't supported out of the box.
    
If you're going to use AFUNIXSocketServer code, add the following dependency:
    
    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-server</artifactId>
      <version>2.5.1</version>
    </dependency>
    
If you're going to use RMI over Unix sockets, add the following dependency:
    
    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-rmi</artifactId>
      <version>2.5.1</version>
    </dependency>

If you're going to use the mySQL Connector for Unix sockets, add the following dependency:

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-mysql</artifactId>
      <version>2.5.1</version>
    </dependency>
 
If you're going to use TIPC, add the following dependency:

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-tipc</artifactId>
      <version>2.5.1</version>
    </dependency>
  
If you're going to use the Jetty connectors, add the following dependency:

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-jetty</artifactId>
      <version>2.5.1</version>
    </dependency>
 
## Gradle
 
 Minimum requirement:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-core:2.5.1'
 
 For RMI support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-rmi:2.5.1'
 
 For MySQL support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-mysql:2.5.1'
 
 For TIPC support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-tipc:2.5.1'
 
 For Jetty support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-jetty:2.5.1'

## jars only

If you don't use Maven or Gradle, etc., simply download the binary distribution from the
[junixsocket releases page](https://github.com/kohlschutter/junixsocket/releases).

junixsocket-dist comes either as a `.tar.gz` or `.zip` archive. Get either one of them. The jars
are in the `lib` directory.

## JDBC

junixsocket makes it easy to connect to a local database directly via Unix domain sockets.

There is a special implementation for MySQL, and a generic SocketFactory that can be used for
databases such as PostgreSQL.

### MySQL

Make sure that the following jars are on your classpath:

 * junixsocket-core-2.5.1.jar
 * junixsocket-common-2.5.1.jar
 * junixsocket-mysql-2.5.1.jar
 * mysql-connector-java-8.0.14.jar (or newer; earlier versions should work, too)
 * (typically, omit if you use the custom library below) junixsocket-native-common-2.5.1.jar
 * (optionally, if you have a custom architecture) junixsocket-native-custom-2.5.1.jar

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
 
### PostgreSQL

Make sure that the following jars are on your classpath:

 * junixsocket-core-2.5.1.jar
 * junixsocket-common-2.5.1.jar
 * postgresql-42.2.5.jar (or newer; earlier versions should work, too)
 * (typically, omit if you use the custom library below) junixsocket-native-common-2.5.1.jar
 * (optionally, if you have a custom architecture) junixsocket-native-custom-2.5.1.jar


Use the following connection properties (along with `user`, `password`, and other properties you may have).

There are currently three distinct ways (socket factories) how to configure the connection to your database:

 1. **org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg**
 
    You provide the socket path via the `socketFactoryArg` JDBC property.

    The connection hostname must be set to "localhost", any other value will not work.
    
    Connection URL: `jdbc:postgresql://localhost/postgres`
    
    | property name | value | remarks |
    | ------------- | ----- | ------- |
    | `socketFactory` | `org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg` | |
    | `socketFactoryArg` | `/tmp/.s.PGSQL.5432` | (1) |
    | `sslMode` | `disable` | (2) |

    
 2. **org.newsclub.net.unix.AFUNIXSocketFactory$SystemProperty**
 
    You provide the socket path via the system property `org.newsclub.net.unix.socket.default`

    The connection hostname must be set to "localhost", any other value will not work.
 
    Connection URL: `jdbc:postgresql://localhost/postgres`
    
    | property name | value | remarks |
    | ------------- | ----- | ------- |
    | `socketFactory` | `org.newsclub.net.unix.AFUNIXSocketFactory$SystemProperty` | |
    | `sslMode` | `disable` | (2) |
 
 3. **org.newsclub.net.unix.AFUNIXSocketFactory$URIScheme**
 
    You provide the socket path via the connection URL's hostname.

    The connection "hostname" can be specified as a file: URL (e.g., `file:///tmp/.s.PGSQL.5432`).

    Since file URLs aren't valid hostnames and contain forward slashes, we have to encode them
    somehow to pacify PostgreSQL's connection parser. This can be achieved using URL encoding:
    If the plain URL is `file:///tmp/.s.PGSQL.5432`, then the URL-encoded version is `file%3A%2F%2F%2Ftmp%2F.s.PGSQL.5432`.
    
    **Performance trick:** By placing a square bracket (`[`) in front of the host name, we can prevent
    any attempt to lookup the hostname in DNS. This is trick not necessary, however.
    
    Connection URL: `jdbc:postgresql://[file%3A%2F%2F%2Ftmp%2F.s.PGSQL.5432/postgres`
    
    | property name | value | remarks |
    | ------------- | ----- | ------- |
    | `socketFactory` | `org.newsclub.net.unix.AFUNIXSocketFactory$URIScheme` | |
    | `sslMode` | `disable` | (2) |


(1) This is the full path to the PostgreSQL socket. The location on your system may be different.
    Try `/var/run/postgresql/.s.PGSQL.5432` if you can't find it in /tmp.

(2) This is optional, and forcibly disables the use of SSL. Since the connection is local, there's
no point in encrypting the communication. Disabling SSL may improve performance.


# Compatibility of future versions
 
We have documented some [Compatibility Considerations](compatibility.html), please keep them
in mind before updating your dependencies.
