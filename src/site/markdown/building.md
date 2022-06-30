# Building from source

Here's how you build junixsocket from the source.

## Prerequisites
 
 1. Make sure you have a 64-bit Intel machine running macOS or Linux.
 
    If you have a different platform or architecture, [continue here](customarch.html).
 
 2. Install the Java JDK 9 or newer (preferably Java 11), Maven 3.6 or newer, and junixsocket.
 
    Even though junixsocket can run on Java 8, you need Java 9 or better to build it, so we can
    support the new Java module system on newer Java versions.
 
 3. Install a development environment so you can compile C code (preferably clang/llvm).
 
    On macOS, this means installing Xcode.
    For development purposes, you may want to use the Xcode project defined in `junixsocket-native/junixsocket-native.xcodeproj`.
    However, this is not required. Running Maven (see below) is the authoritative way of building the native code.

    Also see [here](crosscomp.html) for instructions how to cross-compile for all supported architectures.

    Be sure to install `bash`, `gcc`,`clang`, `ld`/`binutils`, C headers (`glibc-devel`/`libc-dev`, `musl-dev`, etc.), and, optionally, for TIPC on Linux, Linux headers (e.g, `linux-headers`).

    For example, on Alpine Linux, run the following command:

		sudo apk add git maven clang gcc binutils bash musl-dev libc-dev linux-headers

## Building with Maven

Build and test junixsocket.

    cd junixsocket
    mvn clean install

That's it!

## Build options

While the default build options are a good start, you may want to change the level of scrutiny performed at build time.

Here's how to make building stricter (more potential errors are found):

    mvn clean install -Pstrict

Here's how to make building less strict (this turns off several code quality checkers but will dramatically shorten build times):

    mvn clean install -Dignorant=true

If some tests fail, you may try

    mvn clean install -DskipTests=true

If you're having problems with building the native library, you can skip directly to building the Java code via

    mvn clean install -rf :junixsocket-common

You can also try to build the full release version of junixsocket (which will include all cross-compile destinations) -- see the [release instructions](release.html) for details:

    mvn clean install -Pstrict -Prelease

## Issues

If you don't have clang, try compiling with gcc. You may need to specify the compiler/linker at the command line:

    mvn clean install -Djunixsocket.native.default.linkerName=gcc
