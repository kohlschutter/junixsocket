# Building from source

Here's how you build junixsocket from the source.

## Prerequisites
 
 1. Make sure you have a 64-bit machine running macOS or Linux.
 
    If you have a different platform or architecture, [continue here](customarch.html).
 
 2. Install the Java JDK 17 or newer, Maven 3.8.8 or newer, and junixsocket.
 
    Even though junixsocket can run on Java 8 (and even on Java 7 for the common part), you need Java 17 or better to build it, so we can
    support the new Java module system on newer Java versions as well as features that are only
    available on newer versions (e.g., UnixDomainSocketAddress).
 
 3. Install a development environment so you can compile C code (preferably clang/llvm).
 
    On macOS, this means installing Xcode.
    For development purposes, you may want to use the Xcode project defined in `junixsocket-native/junixsocket-native.xcodeproj`.
    However, this is not required. Running Maven (see below) is the authoritative way of building the native code.

    Also see [here](crosscomp.html) for instructions how to cross-compile for all supported architectures.

    Be sure to install `bash`, `gcc`,`clang`, `ld`/`binutils`, C headers (`glibc-devel`/`libc-dev`, `musl-dev`, etc.), and, optionally, for TIPC on Linux, Linux headers (e.g, `linux-headers`).

    For example, on Alpine Linux, run the following command:

		sudo apk add git maven clang gcc binutils bash musl-dev libc-dev linux-headers

4. Install the Java JDK 8 as well (or exclude a config when building; see below)

    Since version 2.8.0, we support Java 7 again. This works through a plugin called "retrolambda",
    which translates Java 8 bytecode in a way that it works on Java 7 again. This plugin stopped
    working with newer Java versions, so we need to run Java 8 in a forked process.

    In order for the plugin to find the Java 8 installation, make sure that you have a `$HOME/.m2/toolchains.xml` file
    with at least the following configuration:

        <toolchains>
          <toolchain>
            <type>jdk</type>
            <provides>
              <version>1.8</version>
            </provides>
            <configuration>
              <jdkHome>/Library/Java/JavaVirtualMachines/1.8.0.jdk/Contents/Home/</jdkHome>
            </configuration>
          </toolchain>
        </toolchains>

    Replace the path at `jdkHome` with your system's configuration. For the purposes of building
    junixsocket, you can have a version `1.8` or `8`; both will work.

    If you cannot/do not want to install JDK 8, add the following parameter to the `mvn`
    command below: `-Dretrolambda=false`.

## Building with Maven

Build and test junixsocket.

    cd junixsocket
    mvn clean install
    # or:
    # mvn clean install -Dretrolambda=false

That's it!

### SNAPSHOT builds

Development versions may need SNAPSHOT versions of dependencies. Use the following command to build:

    mvn clean install -Duse-snapshots

## Build options

While the default build options are a good start, you may want to change the level of scrutiny performed at build time.

Here's how to make building stricter (more potential errors are found):

    mvn clean install -Dstrict

Here's how to make building less strict (this turns off several code quality checkers but will dramatically shorten build times):

    mvn clean install -Dignorant

If some tests fail, you may try

    mvn clean install -DskipTests

If you're having problems with building the native library, you can skip directly to building the Java code via

    mvn clean install -rf :junixsocket-common

You can also try to build the full release version of junixsocket (which will include all cross-compile destinations) -- see the [release instructions](release.html) for details:

    mvn clean install -Dstrict -Drelease

## Issues and Workarounds

### clang/gcc

If you don't have clang, try compiling with gcc. You may need to specify the compiler/linker at the command line:

    mvn clean install -Djunixsocket.native.default.linkerName=gcc

### Failure to find com.kohlschutter.junixsocket:junixsocket-native-custom:jar:default:...-SNAPSHOT

You're building a SNAPSHOT version, skipping over native artifacts, and access to some native
artifacts is missing. Try building with the "use-snapshot" config first:

    mvn clean install -Duse-snapshot -rf :junixsocket-native-custom

If that doesn't work, try ignoring junixsocket-native-custom as an optional dependency for testing:

    mvn clean install -Dnative-custom.skip -rf :junixsocket-common
