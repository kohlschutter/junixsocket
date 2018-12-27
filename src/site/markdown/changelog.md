### Noteworthy changes

  * _(2018-12-26)_ **junixsocket 2.1.1**
  
    - Add missing library for Linux 64-bit to junixsocket-native-common (Maven only)

  * _(2018-12-26)_ **junixsocket 2.1.0**
  
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


  * _(2014-09-29)_ **junixsocket 2.0.1**

    - **Bugfix:** Add byte array bounds checking to read/write methods.
    - Fix C compiler warnings
    - Remove synchronized byte[] array for single-byte reads/writes.



  * _(2014-09-28)_ **junixsocket 2.0.0**
  
    - Move from *Google Code* to *GitHub*.
    - Use Maven as the build system, code is distributed to the *Maven Central* repository.
    - Build native C code using *nar-maven-plugin*, and load JNI libraries *native-lib-loader*


See the commit log for details.

The changelog for 1.x release is archived on [Google Code](https://code.google.com/archive/p/junixsocket/) 
