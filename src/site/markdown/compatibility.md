# Compatibility Considerations

## Versioning

junixsocket versions consist of three parts: major, minor and patch (for example, 2.5.1).

"Minor version" updates (e.g., 2.4.0 -> 2.5.0) can still bring "major" new features but they
should be backwards compatible to releases of the same "major version" (e.g., 2.x).

`-SNAPSHOT` builds are not considered releases, but merely previews of a future release.

### junixsocket 2.5.1

junixsocket 2.5.1 is fully compatible with Java 8 and newer (tested up to Java 19).

junixsocket has been tested to work with Oracle's Java 8 JDK, and OpenJDK for newer versions.

## Supported Platforms

The minimum set of supported (out of the box) platforms and processor architectures currently is:

* macOS Intel 64-bit and ARM 64-bit / Apple Silicon
* Linux x86_64
* Linux ARM 32-bit (armhf)
* Linux ARM 64-bit (aarch64)
* Linux s390x
* Linux RISC-V 64-bit (rv64ifd / lp64d)
* Linux ppc64le (POWER 64-bit Little Endian)
* Solaris x86 64-bit / OpenIndiana
* Windows 10 Intel 64-bit and ARM 64-bit
* Windows Server 2019
* Windows Server 2022
* FreeBSD (amd64)
* OpenBSD (amd64)
* NetBSD (amd64)
* DragonFlyBSD (amd64)
* IBM AIX 7 (POWER 64-bit)
* IBM i 7 (POWER 64-bit)

Linux builds should be compatible with all major distributions, including the
ones using musl (e.g., Alpine Linux).

The native-common binaries for the above platforms are built on recent release versions of
either platform.

AIX support verified with IBM AIX 7.1 (7100-05-09), IBM AIX 7.2 (7200-05-03) and IBM AIX 7.3
(7300-00-01) on s922 and e980, using IBM J9 VMs (Java 8 and Java 11).

IBM i support verified with 7.1 (IBM i 7R1 / 71-11-2984-4), 7.2 (IBM i 7R2 / 72-09-2984-5),
7.3 (IBM i 7R3 / 73-07-001), 7.4 (IBM i 7R4 / 74-05-2984-1) on s922 and e980, using IBM J9 64-bit
VMs (Java 8, and Java 11 where available).

IBM z/OS is supported with caveats (not all [selftests](selftest.html) currently pass, and you have
to compile from source with XLC).

Support for [custom architectures](customarch.html) can be added by compiling a custom native binary
on the target machine, or by [cross-compiling](crosscomp.html) using clang/LLVM on a suitable host.

## Marginally supported platforms

The following platforms can successfully load the JNI library, but since they do not provide
support for AF_UNIX, the functionality is currently too limited to call these platforms "supported":

* Windows 8.1 64-bit
* Windows 10 before build 17061
* OSv Unikernel (tested with v0.56 on qemu and firecracker); socketpair works.

This classification may change with the addition of new features beyond Unix domain sockets.
Please reach out if you have some feature in mind that may be supported on these platforms.

## Additional Platforms and Architectures

Upon request, support for additional systems, platforms and architectures may be added,
such as OpenVMS, FUJITSU BS2000/OSD, Unisys ClearPath OS 2200, unQNX, VxWorks, HP-UX, Haiku, etc.

If you are interested in using junixsocket on another platform, or willing to sponsor development
(by providing access to such platforms, covering licensing costs, etc.), feel free to
[file a ticket](https://github.com/kohlschutter/junixsocket/issues/new/choose)
or [contact Christian Kohlschütter via email](mailto:christian@kohlschutter.com).

## Selftest

A reliable way to ensure that junixsocket works in your environment is to run the "[selftest](selftest.html)".

    java -jar junixsocket-selftest-2.5.1-jar-with-dependencies.jar

The last line should say "Selftest PASSED", and you're good to go.

If not, please [file a bug report](https://github.com/kohlschutter/junixsocket/issues) with the
output of the selftest.

Note: If your target platform supports both 32-bit and 64-bit Java VMs, make sure to use the
64-bit version first.

## Workarounds/testing for broken setups

There is a chance that something is subtly wrong with the combination of junixsocket and your system.

To completely disable junixsocket (as if you were on a system for which no native library is available),
you can set the following System property: 

    -Dorg.newsclub.net.unix.library.disable=true
  
To selectively disable a junixsocket capability, you can specify a System property as follows:

    -Dorg.newsclub.net.unix.library.disable.CAPABILITY_XYZ=true
  
For example, in order to disable the support for passing file descriptors via ancillary messages,
specify:

    -Dorg.newsclub.net.unix.library.disable.CAPABILITY_FILE_DESCRIPTORS=true

The set of available capabilities is enumerated in the `AFSocketCapability` enum.
