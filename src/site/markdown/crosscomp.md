# Cross-compiling junixsocket

Thanks to clang-llvm and junixsocket's custom "crossclang" script, we can now compile the native
JNI code for both macOS and Linux, on _either_ platform.

## Prerequisites

In order to cross-compile, our development machine (the one where we do the compilation), needs
to have clang and llvm.

On Mac, the Xcode version of clang is not sufficient. You have to install llvm from Homebrew:

    brew install llvm

## Setting up the target SDKs

First, we need to setup the SDK of all supported target platforms, so we can build our code one
a single machine. SDKs are not included with junixsocket. You need to either acquire them somehow
in prepared form or create an SDK from a machine running your target platform.

In our terminology, an SDK consists of the headers and libraries that can be compiled against, and
some instructions for the compiler to know how to add these together.

To simplify this process, junixsocket provides the a script that can be used to automatically generate
an SDK from a target machine:

    junixsocket-native/crossclang/bin/prepare-target-sdk

Run this command on all target platforms, including your development machine.

Each target machine creates an SDK directory under `junixsocket-native/crosslang/target-sdks/`.

Copy all target SDKs to the same directory on your development machine, so you can use them.
To avoid duplication, it is encouraged that you store the target SDKs in a shared directory named
`/opt/crossclang/target-sdks` (or `$HOME/.crossclang/target-sdks`).

By default, the name of the SDK is a _triple_ identifier that is compatible with clang, etc.
For example:

* x86_64-apple-darwin18.2.0 (corresponding to macOS Mojave 10.14.2)
* x86_64-pc-linux-gnu

The contents of the SDK directory are:

* The directory structure for the headers (e.g., `usr/include`)
* The directory structure for the libraries (e.g., `usr/lib`, `lib`, etc.) — this may be optional.
* A configuration file (`target.conf`) that contains information about how these headers and libraries are to be used, etc.
* A header file (`target.h`) that gets included by default when building for this target

`prepare-target-sdk` takes care of building these folders, but if it doesn't work out on your target
machine, you can create one yourself or modify an existing one to your liking. 

If you want to create specific SDKs that use the same compiler triple but otherwise are different
enough to warrant being separate, simply rename the directory to a name of your liking. You can
also make use symbolic links.

## Cross-compiling junixsocket for supported platforms.

By default, junixsocket's Maven environment cross-compiles for both x86_64 Linux and macOS.

All you need to do is to specify a flag when building the _junixsocket_ parent project:

    cd junixsocket
    mvn clean install -Dcross=true

Cross-compilation is also enabled when building with the "release profile" (`-Prelease`), unlesss
it's explicitly disabled with `-Dcross=false`.

If you want to extend support beyond these platforms, check the `pom.xml` files in _junixsocket_,
_junixsocket-native_, junixsocket-native-cross, and junixsocket-native-common.

> **NOTE:** On Linux, the junixsocket library is not always linked against glibc.
Some distributions use alternative libc implementations, such as musl-libc (on Alpine Linux, for
example). In order to support these environments, the native library loader tries to load the
junixsocket version for glibc first, and if that doesn't work, it falls back to the ".nodeps.so"
version, which does not have glibc linked.

## Using crossclang from the command-line

_crossclang_ (in `junixsocket-native/crossclang/bin/clang`) is just a small wrapper around `clang` to
simplify configuring the compiler (and linker) to a specific target environment.

It forwards arguments to the actual `clang` binary, and additionally configures it depending on the
value specified with the `-target` argument. If no target triple (or `current`) is specified, the
crossclang infers the triple of the current architecture, and looks for the corresponding SDK in
`target-sdks`. If `-target default` is specified, the call is forwarded to the upstream clang compiler.
If you specify a target that starts with a slash (`/`), the target is considered an absolute path
to a specific target SDK.

crossclang automatically configures clang to use the correct sysroot, system include paths,
system library paths, system framework paths and the proper linker suitable for the platform (e.g.,
ld64.lld for Mac and ld.lld for Unix).

## Testing crossclang

Let's use a very simple example program, `test.c`:

    #include <stdio.h>
    
    int main(int argc, char *argv[]) {
        printf("Hello world, crossclang style!\n");
        return 0;
    }

With crossclang, cross-compilation is as easy as this:

    # Compile (and link) for x86_64 Linux
    crossclang/bin/clang -o test-linux64 test.c -target x86_64-pc-linux-gnu


    # Compile (and link) for x86_64 macOS
    crossclang/bin/clang -o test-mac64 test.c -target x86_64-apple-darwin18.2.0
    

    # Compile (and link) for ARMv5 Linux
    crossclang/bin/clang -o test-linuxarmv5 test.c -target armv5tel-unknown-linux-gnueabi


    # Compile (and link) for the current architecture, no matter what it is
    crossclang/bin/clang -o test test.c -target current


    # Compile (and link) using the default clang for the current architecture
    crossclang/bin/clang -o test test.c -target default

For junixsocket, specifically, you can speed-up your porting development by directly compiling
the JNI code as follows:

    cd junixsocket/junixsocket-native
    crossclang/bin/clang src/main/c/*.c \
        -target (target-platform) \
        -Isrc/main/c -Isrc/main/c/jni \
        -shared (additional flags) \
        -olibjunixtest.so

## Limitations

Right now, crossclang only supports C and C++. However, adding support other languages should
be relatively straightforward.
