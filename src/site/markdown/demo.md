# Command-line demo

On the [junixsocket releases page](https://github.com/kohlschutter/junixsocket/releases),
you can find an archive of junixsocket's release jars along with a script to run the demos from
the `junixsocket-demo` artifact.

junixsocket-dist comes either as a `.tar.gz` or `.zip` archive. Get either one of them.

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
    
    # Runs the MySQL demo
    ./run-demo.sh -j "<path-to-mysql-connector-jar>" -- -DmysqlSocket=/tmp/mysql.sock org.newsclub.net.mysql.demo.AFUNIXDatabaseSocketFactoryDemo
    
    Other flags:
     -m Use the Java module-path instead of the classpath (Java 9 or higher)
     -j <jar> Add the given jar to the beginning of the classpath/modulepath
     -- Separate the run-demo flags from the Java JVM flags
         
For the server/client demos, you may want to run each part (the server and the client, respectively)
in a separate terminal.
