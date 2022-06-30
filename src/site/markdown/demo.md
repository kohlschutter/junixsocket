# Command-line demo

On the [junixsocket releases page](https://github.com/kohlschutter/junixsocket/releases),
you can find an archive of junixsocket's release jars along with a script to run the demos from
the `junixsocket-demo` artifact.

junixsocket-dist comes either as a `.tar.gz` or `.zip` archive. Get either one of them.

## Selftest

Before we start running demos, let's make sure that junixsocket works on the current platform
as expected.

junixsocket-dist provides a self-contained jar that performs this [selftest](selftest.html):

    java -jar junixsocket-selftest-2.5.0-jar-with-dependencies.jar

The last line should read "Selftest PASSED", and you're good to go.

If not, please [file a bug report](https://github.com/kohlschutter/junixsocket/issues) with the
output of the selftest.

## Running the demos

Simply run

    ./run-demos.sh

from the junixsocket-dist-...-bin directory.

You will see a couple of examples on how to invoke the individual demos.

    $ ./run-demo.sh 
    Syntax: ./run-demo.sh [-m] [-j jar]+ [-- [java opts]*] <classname>
    
    Example:
    # Runs the demo server
    ./run-demo.sh org.newsclub.net.unix.demo.SimpleTestServer
    # Runs the demo client
    ./run-demo.sh org.newsclub.net.unix.demo.SimpleTestClient
    
    # Runs the demo RMI server
    ./run-demo.sh org.newsclub.net.unix.demo.rmi.SimpleRMIServer
    # Runs the demo RMI client
    ./run-demo.sh org.newsclub.net.unix.demo.rmi.SimpleRMIClient
    
    # Runs the demo server. Replace "(demo)" with the desired demo.
    ./run-demo.sh -- -Ddemo=(demo) org.newsclub.net.unix.demo.server.AFUNIXSocketServerDemo
    # Runs the demo client. Replace "(demo)" with the desired demo, and "(socket)" with the socket to connect to.
    ./run-demo.sh -- -Ddemo=(demo) -Dsocket=(socket) org.newsclub.net.unix.demo.client.DemoClient
    
    # Runs the MySQL demo
    ./run-demo.sh -j (path-to-mysql-connector-jar) -- -DmysqlSocket=/tmp/mysql.sock org.newsclub.net.mysql.demo.AFUNIXDatabaseSocketFactoryDemo
    
    # Runs the PostgreSQL demo
    ./run-demo.sh -j (path-to-postgresql-jar) -- -DsocketPath=/tmp/.s.PGSQL.5432 org.newsclub.net.unix.demo.jdbc.PostgresDemo
    
    # Runs the HTTP Server
    ./run-demo.sh -j (path-to-nanohttpd-jar) -- org.newsclub.net.unix.demo.nanohttpd.NanoHttpdServerDemo
    
    Other flags:
     -m Use the Java module-path instead of the classpath (Java 9 or higher)
     -j <jar> Add the given jar to the beginning of the classpath/modulepath
     -- Separate the run-demo flags from the Java JVM flags

         
For the server/client demos, you may want to run each part (the server and the client, respectively)
in a separate terminal.

## Demo server and demo client

The packages `org.newsclub.net.unix.demo.server` and `org.newsclub.net.unix.demo.client` contain a series of
demos that can be launched using the `run-demos.sh` script. Be sure to add the correct `-Ddemo=...` parameter
before the classname (and right after the `--`).

Currently, the following demos exist:


*   Server
    * echo: Echoes all input, byte per byte.
    * null: Reads all input, byte per byte, not doing anything else with it.
    * zero: Writes null-bytes, and does not attempt to read anything.
    * chargen: A TCP-style character generator compliant with [RFC 864](https://tools.ietf.org/html/rfc864).
    * send-fd: Opens a file and sends its file descriptor via ancillary message.
     
     
*   Client
    * read: Reads all input, echoing to stdout
    * read-write: Reads all input, echoing to stdout, and sends all data from stdin to the other side.
    * read-fd: Reads the content of any file descriptor sent via ancillary message. The in-band data is ignored.

> **NOTE:** Some demos require additional parameters. These are shown upon invocation, and can be
specified as Java system properties (`-Dkey=value`).
