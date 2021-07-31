# Compatibility Considerations

## Versioning

junixsocket versions consist of three parts: major, minor and patch (for example, 2.4.0).

`-SNAPSHOT` builds are not considered releases, but merely previews of a future release.

### junixsocket 2.4.0

junixsocket 2.4.0 is fully compatible with Java 8 and newer (tested up to Java 16).

There is limited functionality for Java 7 systems: the core functionality (junixsocket-common and
junixsocket-server) is supported, but RMI, selftest and demos aren't.

junixsocket has been tested to work with Oracle's Java 8 JDK, and OpenJDK for newer versions.

## Supported Architectures

The minimum set of supported (out of the box) platforms and processor architectures currently is:

* macOS Intel 64-bit and ARM 64-bit / Apple Silicon
* Linux x86_64
* Linux ARM 32-bit (armhf)
* Linux ARM 64-bit (aarch64)
* Linux s390x
* Linux RISC-V 64-bit (rv64ifd / lp64d)
* Linux ppc64le (POWER 64-bit Little Endian)
* Solaris x86 64-bit / OpenIndiana
* Windows 10 Intel 64-bit
* FreeBSD (amd64)
* OpenBSD (amd64)
* NetBSD (amd64)
* DragonFlyBSD (amd64)

Linux builds should be compatible with all major distributions, including the
ones using musl (e.g., Alpine Linux).

The native-common binaries for the above platforms are built on recent release versions of
either platform.  

Support for [custom architectures](customarch.html) can be added by compiling a custom native binary
on the target machine, or by [cross-compiling](crosscomp.html) using clang/LLVM on a suitable host.
