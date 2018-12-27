# Building from source

Here's how you build junixsocket from the source.

## Prerequisites
 
 1. Make sure you have a 64-bit Intel machine running macOS or Linux.
 
    If you have a different platform or architecture, [continue here](customarch.html).
 
 2. Install the Java JDK 9 or newer (preferably Java 11), Maven 3 or newer (tested: 3.6.0) and junixsocket.
 
    Even though junixsocket can run on Java 8, you need Java 9 or better to build it, so we can
    support the new Java module system on newer Java versions.
 
 3. Install a development environment so you can compile C code (e.g., gcc, clang, etc.)
 
    On macOS, this means installing Xcode.

## Building with Maven

Build and test junixsocket.

    cd junixsocket
    mvn clean install

That's it!
