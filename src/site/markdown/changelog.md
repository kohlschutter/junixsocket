# Noteworthy changes

### General remarks

Please tend to always use the latest version; the existing API should always be backwards compatible between minor releases (e.g., 2.4.0 -> 2.5.1) unless explicitly mentioned in the changelog below (e.g., dropping Java 7 support in 2.5.0)

When upgrading from versions older than 2.4.0, please note that junixsocket-core is now a POM-only
artifact (`<type>pom</type>`); see [Add junixsocket to your project](dependency.html) for details.

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


#### _(2018-12-29)_ **junixsocket 2.1.2**      
    
- Add AFUNIXSocketFactory, support for PostgreSQL
- Add support for new MySQL Connector/J SocketFactory
- Prevent a case of file descriptor leakage
- Handle EINTR errors from system calls


#### _(2018-12-26)_ **junixsocket 2.1.1**
  
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


#### _(2014-09-29)_ **junixsocket 2.0.1**

- **Bugfix:** Add byte array bounds checking to read/write methods.
- Fix C compiler warnings
- Remove synchronized byte[] array for single-byte reads/writes.



#### _(2014-09-28)_ **junixsocket 2.0.0**
  
- Move from *Google Code* to *GitHub*.
- Use Maven as the build system, code is distributed to the *Maven Central* repository.
- Build native C code using *nar-maven-plugin*, and load JNI libraries *native-lib-loader*


#### 

See the commit log for more details.

The changelog for 1.x release is archived on [Google Code](https://code.google.com/archive/p/junixsocket/) 
