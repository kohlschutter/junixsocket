# Noteworthy changes

### _(unreleased)_ **junixsocket 2.2.0**

 - Add support for receiving peer credentials
 - Add support for sending and receiving file descriptors
 - Add AFUNIXSocketServer, a multi-threaded UNIX domain server implementation
 - New supported platform: Linux ARM 32-bit (e.g., Raspberry Pi)
 - AFUNIXServerSocket#setReuseAddress can now control whether reusing an existing socket is permitted
 - Exception handling: No longer wrap SocketExceptions, throw SocketTimeoutException upon EAGAIN for read
 - Improve handling of closed sockets
 - Improved native library loader code, better error handling
 - Improved handling of libc alternatives, such as musl
   (we now build two different libraries, one that's linked against glibc, and one that isn't).
 - JNI: Fix some missing return-after-throw, use-after-free in bind
 - Properly close file descriptors upon errors in native code; unlink before bind
 - Ask to not raise SIGPIPE
 - Introduced "crossclang": junixsocket can now be cross-compiled with clang/LLVM
 - Improved demo code, new demo client
 - Code cleanup


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
- Code cleanup and other bug fixes


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
