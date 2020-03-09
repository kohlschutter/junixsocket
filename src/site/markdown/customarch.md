# Custom architectures: Building and running

There's a chance you want to run junixsocket on a platform that's not supported by our release
binaries in *junixsocket-native-common*.

Here's how you build your custom artifacts.

## Prerequisites
 
 1. Make sure you have a machine running your target platform. 
 2. Install the Java JDK 9 or newer (preferably Java 11), Maven 3 or newer (tested: 3.6.0) and junixsocket.
 3. Install a development environment so you can compile C code (e.g., gcc, clang, etc.)
 
> **NOTE** You may also be able to cross-compile code for your target platform, on your development
machine. See [Cross-compiling junixsocket](crosscomp.html) for details. 
 
## Step 1: Build the native library for the current architecture

Make sure we can build junixsocket's JNI library.

If this step fails, you're mostly on your own for now; closely inspect the error log and consider filing a bug report against junixsocket. Also check the [Unix socket reference](unixsockets.html), which may reveal some
hints how to get it compile on your platform.

    cd junixsocket
    ( cd junixsocket-native ; mvn clean install )

## Step 2: Find the classifier of the native library artifact

The classifier is the "AOL" identifier plus "-jni". The following script can find the available classifiers.

    cd junixsocket
    # replace 2.x.y with the version of junixsocket you're trying to build.
    junixsocket-native-prebuilt/bin/list-available-in-m2repo.sh -c 2.x.y

On x86_64 Linux, this script should show the following line:

    amd64-Linux-gpp-jni

## Step 3: Build the junixsocket-native-custom artifact

Let's roll this native library into our junixsocket-native-custom jar, as well as build and test
the entire project:

    cd junixsocket
    mvn clean install -Djunixsocket.custom.arch=amd64-Linux-gpp-jni

## Step 4: Keep the junixsocket-native-custom jar.

If all goes well, the junixsocket-native-custom jar that contains our target platform's native binary
is stored in the local Maven repository cache.

Use the following script to find the path:

    cd junixsocket 
    # replace 2.x.y with the version of junixsocket you're trying to build.
    junixsocket-native-prebuilt/bin/list-native-custom.sh 2.x.y
    
Copy the file somewhere else where you need it (e.g., your local artifact manager).

To simplify deployment without an artifact manager, you can use the following script to copy the jar
back to the local repository cache on another machine:

    cd junixsocket 
    junixsocket-native-prebuilt/bin/install-to-m2repo.sh <path-to-junixsocket-native-custom-*.jar>

## Step 5: Use the junixsocket-native-custom jar.

Simply add the jar to the classpath. Make sure you don't add any other junixsocket-native-custom jar
to it.

If you use Maven for dependency management in your project, you could add the following dependency
for development and testing purposes:

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-native-custom</artifactId>
      <version>2.3.2</version>
      <classifier>amd64-Linux-gpp-jni</classifier>
    </dependency>

Replace the value for `classifier` with the one from Step 2.

However, you cannot have more than one junixsocket-native-custom artifact in your dependency graph.
Therefore, this dependency would make your code platform-specific, and since not everyone might
have your custom artifact, there's a chance it wouldn't even build on other people's machines.

An alternative is to directly add the junixsocket-native-custom jar to the classpath whenever you
invoke the Java VM (e.g., your web server, etc.), for example:

    java -cp junixsocket-native-custom-2.3.2-amd64-Linux-gpp-jni.jar:*(...)* *YourMainClass*

## If that doesn't work...

There may be reasons why all this doesn't work, and you simply want to specify the location of
the native library yourself.

Simply set the system property `org.newsclub.net.unix.library.override` to the location of the native
library. For example:

    java -Dorg.newsclub.net.unix.library.override=junixsocket-native-2.3.2 (...)
