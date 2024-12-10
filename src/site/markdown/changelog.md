
# Changelog

### General remarks

Please try to use the latest version.

When upgrading from versions older than 2.4.0, please note that `junixsocket-core` is now a POM-only
artifact (`<type>pom</type>`); see [Add junixsocket to your project](dependency.html) for details.

## Noteworthy changes

**Users of junixsocket are strongly advised to upgrade to version 2.10.1 or newer**

### _(XXXX-XX-XX)_ **junixsocket 2.11.0**

- Add junixsocket-memory, a new Java 22+ module to support Shared Memory in a platform-agnostic way via MemorySegment, including
support for Linux memfd_secret, Futex-based Mutexes, and even Windows.
- Fix an unecessary exception being thrown for `TIPC_GROUP_LEAVE` (AFTIPCSocket)
- Improve support for casting FileDescriptor to FileChannel; allow specifying open mode.
- Building now requires Java 22 or newer
- Code cleanup

### _(2024-09-23)_ **junixsocket 2.10.1**

- Fix left-over temporary library files on Windows
- Fix duplicate file descriptors being received sporadically for non-blocking sockets and upon error
- Fix a flaky selftest when VSOCK is not supported
- Fix a flaky selftest with GraalvM 17
- Fix a flaky selftest with old-style finalizers
- Improve interoperability with exotic Linux/Java combinations
- Add support for loongarch64 Linux
- Add more tests
- Code cleanup

### _(2024-07-08)_ **junixsocket 2.10.0**

- Fix compatibility with Java 7
- Fix error handling for non-blocking operations on Windows
- Fix interoperability of junixsocket-mysql and GraalVM native-image
- Fix socket-closed state upon exceptions indicating a closed socket descriptor
- Fix exceptions for channels: Throw ClosedChannelException instead of SocketClosedException, etc.
- Fix a flaky selftest when VSOCK is not supported
- Improve compatibility and performance with Virtual Threads in Java 21 or newer (JEP 444)
- Improve interopability with Java 15 UnixDomainSocketAddress and StandardProtocolFamily.UNIX
- Improve selftest stability and logging, more tests
- Add support for ServerSocketChannel.bind(null) for AF_UNIX socket addresses.
- Add more tests for mysql interoperability, optionally include mysql tests in selftest
- Add NotConnectedSocketException, NotBoundSocketException
- Building now requires Java 21, Maven 3.8.8 or newer
- Update build-time dependencies
- Code cleanup

### _(2024-04-05)_ **junixsocket 2.9.1**

- Fix ignored timeouts for Mysql-specific AFUNIXDatabaseSocketFactoryCJ
- Fix GraalVM configuration, support AFUNIXSocketFactory for native-image
- Fix compatibility with jetty 12.0.7
- Fix unnecessary failures in some tests, error handling in selftest
- Improve SocketException handling (throw SocketClosedException subclass upon accept error)
- Make native library code compile on Minix
- Add availability check of abstract namespace on emulated Linux environments (BSD)
- Add junixsocket-demo-jpackagejlink artifact to show how to use jpackage/jlink with junixsocket
- Update crossclang scripts, fix compatibility with Xcode 15.3
- Code cleanup

### _(2024-02-14)_ **junixsocket 2.9.0**

- Add generic socket fallback for FileDescriptors received from other processes
- Add "dup"/"dup2" support via FileDescriptorCast.duplicating
- Add listen/accept support to AFDatagramSocket, so we can serve SEQPACKETs
- Add more SocketException subclasses (such as BrokenPipe-/ConnectionResetSocketException)
- Add support to make shutdown-upon-close configurable
- Add support for undocumented "ECLOSED" (errno 3417) condition on IBM i PASE
- Add test for the "close-during-accept" condition
- Fix native library loading for AIX/IBM i on Java 15 and newer
- Fix blocking state when using FileDescriptorCast
- Fix module-info.java: Don't mark requirements transient (annotations, mysql connector)
- Fix TIPC tests on some old environments (which didn't time out)
- Fix compilation for z/OS 32-bit
- Fix AFServerSocketChannel.getLocalAddress to return AFSocketAddress subclass
- Fix unnecessary failures in some tests, error handling in selftest
- Fix "force override" path parsing for native library on Windows
- Update build/plugin/test/demo dependencies
- Update crossclang scripts; no longer requires root to install Xcode components
- Improve error handling on broken Java VMs (e.g., IBM Semeru 8.0.7 and older)
- Improve demo code, use slf4j-simple for logging
- Code cleanup

Backwards-incompatible change: Some AF*Socket* classes are now final or no longer declare constructor exceptions.

### _(2023-11-12)_ **junixsocket 2.8.3**

- Fix concurrency issue with AFSocketServerConnector, AFSelectionKey; take two
- Fix regression introduced in 2.8.2 that leaked FileDescriptors
- Reduce allocation overhead during select

### _(2023-10-27)_ **junixsocket 2.8.2** (do not use; has a regression)

- Fix concurrency issue with AFSocketServerConnector, AFSelectionKey

### _(2023-09-29)_ **junixsocket 2.8.1**

- Fix UnsatisfiedLinkError with noexec temporary directory on RHEL 9 and others

### _(2023-09-28)_ **junixsocket 2.8.0**

- Java 7 support is back! (junixsocket-common only, as it was before version 2.5.0)
- Fix AFSocket shutdown to ignore InvalidSocketException upon setTimeout
- Fix two potential hangs in selftest
- Fix loading of the native library when running under macOS Rosetta 2
- Fix a potential exception when trying to serialize an AFRMISocketFactory
- Fix a potential race condition when working with native addresses
- Fix a potential crash in TIPC code when compiling the native library against an old Linux SDK
- Improve AFSocketServer, add new methods
- Improve crossclang to support Xcode 15
- Enable RMI support for GraalVM native-image; selftest now passes without issues
- Add junixsocket-ssl, to simplify securing junixsocket connections
- Requires Java 17 to build (and JDK 8 if Java 7 support is desired); build instructions have changed

### _(2023-09-15)_ **junixsocket 2.7.2**

- Fix SelectionKey logic (regression introduced in 2.7.1)
- Fix selftest-android dependency (some tests would always fail)
- Improve AFSocketAddress creation, skip DNS resolution on Android
- Add demo code for interacting with Apache Mina and Netty
- Code cleanup

### _(2023-09-09)_ **junixsocket 2.7.1**

- Fix openDatagramChannelPair (was using STREAM instead of DGRAM), add AFSocketType support
- Fix availability of AF_SYSTEM capability on Darwin
- Improve exception handling for "EPROTOTYPE" error on z/OS
- Improve SelectionKey logic, reduce locking/GC overhead
- Improve "unsupported operation" handling in native code
- Improve handling of "test aborted, but not really an issue"
- Add unit tests for Jetty 12
- Code cleanup, update dependencies

### _(2023-08-04)_ **junixsocket 2.7.0**

- New supported platform (out of the box): Android (aarch64, arm-linux-androideabi, x86_64, i686)
- New platforms that can be used when building the native library from source: Haiku, IBM z/TPF
- Add selftest GUI app for Android ("junixsocket-selftest-android")
- Add ability to get native file descriptor number via FileDescriptorCast
- Add AFSocketCapability for "large port numbers" (larger than 65535)
- Add support to convert UnixDomainSocketAddress to AFUNIXSocketAddress (Java 16+)
- Add support for AF_SYSTEM (macOS), which allows creating your own VPN via utun, for example.
- Add initial support for SOCK_SEQPACKET/SOCK_RDM/SOCK_RAW
- Fix potential data corruption when sending non-direct ByteBuffers larger than 8192 bytes
- Fix potential data corruption when receiving non-direct ByteBuffers with non-zero position
- Fix intermittent hangs when sending datagrams on macOS, BSD
- Fix "org.eclipse.jdt.annotation" JPMS module: no longer required at runtime
- Fix Exception messages from native code being empty in some Linux environments
- Fix selftest failing for vsock in some Linux environments
- Improve JNI library lookup; load from user.home/user.dir if tmpfs is mounted with noexec
- Improve concurrency performance for RMI over Unix domain sockets
- Improve reliability on exotic environments
- Update dependencies, improve tests, selftest, build scripts
- Require Java 16 to build (still supports running on Java 8 and newer)
- Last but not least: junixsocket finally has a logo!

### _(2022-02-08)_ **junixsocket 2.6.2**

- Add socket connectors for AF_VSOCK, Firecracker
- Add mayStopServerForce to AFSocketServerConnector
- Fix false-positive selftest failure on slow machines
- Fix potential hang in send
- Fix support for TIPC/VSOCK when building GraalVM native images
- Fix serialization of AFSocketAddresses
- Update dependencies, especially use the new mysql.connector.j

### _(2022-10-26)_ **junixsocket 2.6.1**

 - Add AFSocket.checkConnectionClosed to probe connection status
 - Fix connection status checks and error handling
 - Fix bind behavior on Windows, support re-bind with reuseAddress
 - Fix and improve unit tests/selftests, remove several false-positive errors found in the wild (Azure Cloudshell/Microsoft CBL-Mariner 2.0, Amazon EC2, OpenBSD, etc.)
 - Fix SimpleTestServer demo, actually counting now to 5, not 6.
 - Make builds reproducible, align timestamps with git commit

### _(2022-10-14)_ **junixsocket 2.6.0**

 - Add support for GraalVM native-image
 - Add support for native-image selftest
 - Add support for AF_VSOCK (on Linux, and some macOS VMs)
 - Reintroduce deprecated legacy constructors for AFUNIXSocketAddress that were removed in 2.5.0.
 - Parent POM has been renamed from junixsocket-parent to junixsocket

### _(2022-10-06)_ **junixsocket 2.5.2**

 - Fix address handling in the Abstract Namespace
 - Fix support for very large datagrams (> 1MB)
 - Fix InetAddress-wrapping of long addresses
 - Update Xcode support script, crossclang
 - Bump postgresql version in demo code
 - Fix dependency for custom architecture artifact

### _(2022-07-01)_ **junixsocket 2.5.1**

 - Add support for IBM z/OS (experimental, binary not included)
 - Add support for building from source on arm64-Linux
 - Add junixsocket support for jetty via [junixsocket-jetty](http://kohlschutter.github.io/junixsocket/junixsocket-jetty/)
 - Fix Selector logic (more bug fixes)
 - Documentation updates

### _(2022-06-06)_ **junixsocket 2.5.0**

 - New supported platforms: AIX 7 Power64, IBM i Power64, Windows ARM64, Windows Server 2019 & 2022
 - Generic rework to support more than just Unix Domain sockets
 - Add support for AF_TIPC (on Linux)
 - Add support for using sockets passed as standard input
 - Add support for address-specific, non-standard URIs (for example
   unix:// and tipc://), as well as socat addresses
 - Add support for using FileDescriptor for ProcessBuilder Redirects (Java 9+)
 - Add support for peer credentials (PID) on Windows
 - Fix Selector logic
 - Fix cross-compilation on Apple Silicon
 - Fix a file descriptor leak (regression in 2.4.0)
 - Improve behavior on partially unsupported platforms and allow loading of Windows 10 native
   library on other Windows versions (e.g., Windows Server 2022, Windows 8.1).
 - Javadoc improvements, Code cleanup
 - Deprecate AFUNIXSocketCapability in favor of AFSocketCapability
 - Drop support for Java 7

 Backwards-incompatible change: `new AFUNIXSocketAddress(File)` constructor has been dropped; use 2.6.0 or newer if you can't change the source code.

### _(2021-07-30)_ **junixsocket 2.4.0**

 - New supported platforms: NetBSD, OpenBSD, DragonFlyBSD (AMD64-builds included by default)
 - Add support for Datagram sockets
 - Add support for non-blocking I/O, Java NIO SocketChannel, DatagramChannel, ByteBuffer, Selector, Pipe, etc.
 - Add support for socketpair (with IP-based emulation on Windows)
 - Add support for casting FileDescriptors to Socket, etc.
 - Add support for Peer Credentials on Solaris/Illumos
 - Improved creation and reuse of AFUNIXSocketAddress instances
 - Add basic support to wrap an AFUNIXSocketAddress as an InetAddress
 - Add fast-path for single-byte read/write
 - Significant internal code refactoring and cleanup (both Java and native C code)
 - AFUNIXRegistry lookup can now take a timeout parameter (to coordinate between starting processes)
 - Replaced the "finalize" logic with a custom Cleaner for Java 9 and above
 - Fixed compatibility issue with OkHttpClient, add demo code
 - Fixed a race condition when connecting to a registry that's just starting up.
 - More test cases, increased unit test code coverage
 - Simplified local compilation on non-macOS/Linux systems.
 - Improved selftest
 - Maven Dependency: junixsocket-core is now a POM-only artifact (`<type>pom</type>`).

### _(2021-05-30)_ **junixsocket 2.3.4**

 - Fix bind/stat on glibc-based Linux on aarch64 and RISC-V (regression from 2.3.2)

### _(2021-04-15)_ **junixsocket 2.3.3**

 - Add support for aarch64 on Apple Silicon Macs
 - Improved throughput by up to 40% by removing some JNI overhead (now on par with JEP380)
 - Fix "undefined symbol: stat" error on older Linux machines
 - Fix self-test on Windows; add more system information to selftest
 - Fix LICENSE/NOTICE files (no license change, just reorganization)
 - Fix build issues with RISC-V (use LLVM 9)
 - Fix build issues on macOS
 - Improve handling of ancillary receive buffers
 - Add a new system property to control what happens when the library override fails to load
 - Add an Xcode project to simplify cross-compilation; improved crossclang

### _(2020-03-08)_ **junixsocket 2.3.2**

 - Add support for FreeBSD (Intel 64-bit)
 - Fix an NPE when junixsocket is on the bootstrap classloader
 - Fix AcceptTimeoutTest#testCatchTimeout (selftest would sometimes erroneously fail)

### _(2020-01-16)_ **junixsocket 2.3.1**

 - Add support for Java 15
 - Increase minimum version requirement for macOS to 10.9 to comply with notarization requirements
 - Improved error reporting upon trying to connect an already closed socket
 - Improved error reporting when a compiler binary is missing for cross-compilation

### _(2019-12-26)_ **junixsocket 2.3.0**

 - Add support for Java 14
 - Add support for Java 7 (core/server package only)
 - Add support for ppc64le (POWER) Linux
 - Add support for RISC-V 64-bit Linux
 - Add support for s390x Linux
 - Add support for Solaris x86/OpenIndiana (Intel 64-bit)
 - Add support to send FileDescriptors (FileInputStream, FileOutputStream) via RMI
 - Add support to retrieve peer credentials for RMI connections
 - Add new self-test functionality to verify that junixsocket works on a given platform
 - New demo code: HTTP Server over Unix sockets (using NanoHTTPD)
 - macOS: Use poll for read; fix read timeout not being honored
 - musl-libc: Workaround for segmentation fault in CMSG_NXTHDR (ancillary messages).
 - Several other bug fixes and improvements

### _(2019-11-14)_ **junixsocket 2.2.1**

 - Add support for Java 13

### _(2019-02-17)_ **junixsocket 2.2.0**

 - New supported platform: Linux ARM 32-bit and 64-bit (e.g., Raspberry Pi)
 - New supported platform: Windows 10 AMD64
 - Add support for receiving peer credentials
 - Add support for sending and receiving file descriptors
 - Add support for the abstract namespace on Linux and Windows
 - Add AFUNIXSocketServer, a multi-threaded UNIX domain server implementation
 - Introduced AFUNIXSocketCapabilities to check which capabilities are supported on your platform
 - AFUNIXServerSocket#setReuseAddress can now control whether reusing an existing socket is permitted
 - Exception handling: No longer wrap SocketExceptions, throw SocketTimeoutException upon EAGAIN for read
 - Improve handling of closed sockets
 - Improved native library loader code, better error handling
 - Improved handling of libc alternatives, such as musl
   (we now build two different libraries, one that's linked against glibc, and one that isn't)
 - JNI: Fix some missing return-after-throw, use-after-free in bind
 - Properly close file descriptors upon errors in native code; unlink before bind
 - Ask to not raise SIGPIPE
 - Introduced "crossclang": junixsocket can now be cross-compiled with clang/LLVM
 - Improved demo code, new demo client

### _(2018-12-29)_ **junixsocket 2.1.2**

- Add AFUNIXSocketFactory, support for PostgreSQL
- Add support for new MySQL Connector/J SocketFactory
- Prevent a case of file descriptor leakage
- Handle EINTR errors from system calls

### _(2018-12-26)_ **junixsocket 2.1.1**

- Support for Java 8, 9, 10 and 11
- Building junixsocket requires Java 9 or later
- Jigsaw module support
- New Native library loading mechanism
- Validate that socket exists before trying to connect
- Replaced AFUNIXSocketException with SocketException
- Throw InterruptedIOException upon interrupt during write
- Properly discard reference to file descriptor upon close
- Add system property to override the default directory for RMI sockets
- Properly handle timeout during ServerSocket#accept
- Additional range checks for array offsets
- Script to run the demos from the command-line
- Documentation updates
- Updated Maven dependencies

### _(2014-09-29)_ **junixsocket 2.0.1**

- **Bugfix:** Add byte array bounds checking to read/write methods.
- Fix C compiler warnings
- Remove synchronized byte[] array for single-byte reads/writes.

### _(2014-09-28)_ **junixsocket 2.0.0**

- Move from *Google Code* to *GitHub*.
- Use Maven as the build system, code is distributed to the *Maven Central* repository.
- Build native C code using *nar-maven-plugin*, and load JNI libraries *native-lib-loader*

### _(2013-02-20)_ **junixsocket 1.4**

- Fix InetSocketAddress.port issue with recent JDK
- Fix setOption: SO_RCVBUF and SO_SNDBUF take integers
- Fix native library load issue on RHEL4 32-bit
- Fix some test failures

### _(2010-08-11)_ **junixsocket 1.3**

- Solaris support
- InputStream#available() may now return values > 0
- Added explicit mapping of java.net.SocketOptions values
- Fixed "protocol not available" and "invalid argument" errors occuring in rare cases
- Improved some warnings and error messages
- Improved build process, can now skip building for 32/64 bit

### _(2010-04-22)_ **junixsocket 1.2**

- Bugfixes and improvements
- MySQL unix socket factory now available as a separate jar (and with demo code)
- Now compiles under FreeBSD
- Initial support for Tru64
- Improved handling of stale socket files

### _(2009-12-03)_ **junixsocket 1.1**

- Bugfixes and improvements

### _(2009-08-28)_ **junixsocket 1.0**

- Initial release

####

See the [commit log](https://github.com/kohlschutter/junixsocket/commits/main/) for more details.
