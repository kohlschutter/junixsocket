# Compatibility Considerations

## Versioning

junixsocket versions consist of three parts: major, minor and patch (for example, 2.1.2).

`-SNAPSHOT` builds are not considered releases, but merely previews of a future release.

### junixsocket 2.2

junixsocket 2.2 is compatible with Java 8 and newer (tested up to Java 12).

It has been tested with Oracle's Java 8 JDK, and OpenJDK for newer versions.

## Supported Architectures

The minimum set of supported platforms and processor architectures currently is

* macOS Intel 64-bit
* Linux x86_64
* Linux ARM 32-bit
* Linux ARM 64-bit (aarch64)

The native-common binaries are built on recent release versions of either platform.  

Support for [custom architectures](customarch.html) can be added by compiling a custom native binary
on the target machine, or by [cross-compiling](crosscomp.html) using clang/LLVM on a suitable host.
