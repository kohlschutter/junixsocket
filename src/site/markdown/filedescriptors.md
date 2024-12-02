# File Descriptors

File descriptors are identifiers (handles) for files, sockets, pipes or other resources.

To work with file descriptors, Java provides us with [java.io.FileDescriptor](https://docs.oracle.com/javase/8/docs/api/java/io/FileDescriptor.html). junixsocket elevates the use of `java.io.FileDescriptor` by

1. allowing to send and receive them between processes (via UNIX domain sockets), and
2. providing a sophisticated casting mechanism to access the referenced resource via Java classes such as `Socket`, `InputStream`, etc.
3. passing file descriptors to new processes via `ProcessBuilder.Redirect`.

## Getting a FileDescriptor reference

Anything that implements the junixsocket interface `FileDescriptorAccess` (all AFSockets, for example), can return the corresponding `FileDescriptor`:

	AFSocket socket = ...;
	FileDescriptor fd = socket.getFileDescriptor();

You can also get the file descriptor from some classes in the Java API, for example:

	FileInputStream in = ...;
	FileDescriptor fd = in.getFD();

	RandomAccessFile raf = ...;
	FileDescriptor fd = in.getFD();

	// Standard file descriptors
	FileDescriptor stdin = FileDescriptor.in;
	FileDescriptor stdout = FileDescriptor.out;
	FileDescriptor stderr = FileDescriptor.err;

### Getting a FileDescriptor from a native fd integer value

You may use a somewhat "unsafe" operation to convert a system-native file descriptor, described as an integer value, to a `FileDescriptor` (or other types, see below), via `FileDescriptorCast.unsafeUsing(fdVal).as(FileDescriptor.class)`.

This functionality may not be available in all environments (e.g., on Windows, or when manually disabled by setting the system property `-Dorg.newsclub.net.unix.library.disable.CAPABILITY_UNSAFE=true`).

## Sending and receiving File Descriptors

A very useful feature of Unix Domain Sockets is the ability to send and receive file descriptors
between processes.

This, for example, enables non-privileged processes to access otherwise restricted files:
A privileged process (e.g., running as root, or as a UID with special access rights) opens the
restricted file, and then exposes the file handle to another process via AF_UNIX sockets.

File descriptors are sent as so-called "ancillary messages" along with regulary payload.
They cannot be sent alone, so make sure that you send at least some data, even if it's just one byte.

### Sending file descriptors

    AFUNIXSocket socket = ...

    FileInputStream fin = new FileInputStream(file);
    socket.setOutboundFileDescriptors(fin.getFD());
    // you can also send more than one FD at the same time, just make sure they're all part of the same call to setOutboundFileDescriptors. 

    // Ancillary messages are sent _along_ regular in-band messages, so we have to send something here.
    os.write("Some message".getBytes("UTF-8"));

### Receiving file descriptors

    AFUNIXSocket socket = ...

    // set ancillary receive buffer to a reasonable size (disabled by default!)
    socket.setAncillaryReceiveBufferSize(1024);

    InputStream in = socket.getInputStream();
    // do you regular socket IO here
    in.read(...)
    
    // If there were any file descriptors sent as ancillary messages, check them right after a call to read
    FileDescriptor[] descriptors = socket.getReceivedFileDescriptors();
    if (descriptors != null) {
      for (FileDescriptor fd : descriptors) {
        FileInputStream fin = new FileInputStream(fd);
        // do something with the stream
      }
    }
    
Also see `FileDescriptorCast` below for how to better handle file descriptors that describe sockets and other resources.

### Even easier with RMI

If you're using `junixsocket-rmi` for inter-process communication, you can simply wrap streams with
`RemoteFileInput`/`RemoteFileOutput` (or the generic `RemoteFileDescriptor`) and not worry about the
technicalities:

    FileInputStream fin = ...;

    RemoteFileInput rfi = new RemoteFileInput(socketFactory, fin);
    // rfi can now be used for inter-process communication via RMI:
    someRMIService.someMethod(rfi);

    // on the receiving side:
    public void someMethod(RemoteFileInput rfi) throws IOException {
        FileInputStream fin = rfi.asFileInputStream();
        // ...
        fin.close(); // closes the stream for this process only (different file handle).
    } 

### Due diligence
 
 It is recommended to authenticate the connection, since you certainly don't want unauthorized callers
 to obtain access to potentially secret information.
 
 You may either use your own SSL/TLS authentication atop the Socket, or simply use [peer credentials](peercreds.html).

### Not supported by all platforms

 Not all platforms support file descriptors over AF_UNIX. Make sure they're available using
> `AFSocket.supports(AFSocketCapability.CAPABILITY_FILE_DESCRIPTORS)`.

## "Casting" FileDescriptor objects to Socket etc.

When you receive a FileDescriptor from another process, you want to use it as if it was created locally. While the Java standard API does not provide us with much more than a FileDescriptor-taking constructor for `java.io.FileInputStream`/`FileOutputStream`, junixsocket gives you more control via [`FileDescriptorCast`](junixsocket-common/apidocs/org.newsclub.net.unix/org/newsclub/net/unix/FileDescriptorCast.html):

First, you instantiate a `FileDescriptorCast` instance using the FileDescriptor of your choice, then you specify as what class you want to access it:

	FileDescriptor fd = ...;
	// NOTE: check `fd.valid()` or an `IOException` may be thrown.
	Class<T> desiredClass = ...;
	T instance = FileDescriptorCast.using(fd).as(desiredClass);

For example, if you want to simply read from this file descriptor, use:

	InputStream in = FileDescriptorCast.using(fd).as(InputStream.class);

If you want to get a FileChannel, you need to use our custom FileChannelSupplier, which has specific subclasses to indicate read-only, write-only, and read-write access modes, for example:

    FileChannel fc = FileDescriptorCast.using(fd).as(FileChannelSupplier.ReadOnly.class).get();

If the file descriptor is a Socket, you can use:

	Socket sock = FileDescriptorCast.using(fd).as(Socket.class);
	// or:
	AFSocket sock = FileDescriptorCast.using(fd).as(AFSocket.class);
	// or:
	AFUNIXSocket sock = FileDescriptorCast.using(fd).as(AFUNIXSocket.class);
	// etc.

If you want to access the native file descriptor value as an integer (only where supported), you can use:

    int fdVal = FileDescriptorCast.using(fd).as(Integer.class); // won't work for all types on Windows

Note that if the specified `FileDescriptor` is incompatible with the target class, a `ClassCastException` is thrown. Also be aware that this technically isn't a cast, since a different object reference is returned.

In order to check if a file descriptor can be "cast" to a particular class, you can use `FileDescriptorCast#isAvailable` and `FileDescriptorCast#availableTypes`:

	FileDescriptorCast fdc = FileDescriptorCast.using(fd);
	if (fdc.isAvailable(Socket.class)) {
		Socket socket = fdc.as(Socket.class);
	} else {
		throw new IllegalStateException("Cannot cast to Socket, only to: " + fdc.availableTypes());
	}

## Sending a FileDescriptor via ProcessBuilder.Redirect

You can pass a FileDescriptor as standard input ("stdin") to a process launched with `ProcessBuilder`:

	FileDescriptor fd = ...;
	ProcessBuilder pb = ...;
	pb.redirectInput(FileDescriptorCast.using(fd).as(ProcessBuilder.Redirect.class));
	Process p = pb.start();

### Not supported by all platforms

Note that this functionality is only available if the environment supports it (Java 9 or newer, and not on Windows). Moreover, the feature currently uses Java SDK internals that may change/disappear.

Be sure to check availability with

	if (AFSocket.supports(AFSocketCapability.CAPABILITY_FD_AS_REDIRECT)) {...}

## Important considerations

- On some platforms (e.g., Solaris, Illumos) you may need to re-apply a read timeout (e.g.,
  using `Socket#setSoTimeout(int)` after obtaining the socket.
- You may lose Java port information for `AFSocketAddress` implementations that do not
  encode this information directly (such as `AFUNIXSocketAddress` and `AFTIPCSocketAddress`)
- The "blocking" state of a socket may be forcibly changed to "blocking" when performing the
  cast, especially when casting to `Socket`, `DatagramSocket` or `ServerSocket`
  and any of their subclasses where "blocking" is the expected state.
- When calling `FileDescriptorCast#using(FileDescriptor)` for a `FileDescriptor` obtained from
  another socket or other resource in the same JVM (i.e., not from another process), especially for
  sockets provided by junixsocket itself, there is a chance that the garbage collector may clean up
  the original socket at an opportune moment, thereby closing the resource underlying the shared
  file descriptor prematurely.
  
  This is considered an edge-case, and deliberately not handled automatically for performance and
  portability reasons: We would have to do additional reference counting on all FileDescriptor
  instances, either through patching `FileCleanable` or a shared data structure.
  
  The issue can be prevented by keeping a reference to the original object, such as keeping it in
  an enclosing try-with-resources block or as a member variable, for example. Alternatively, using
  a "duplicate" file descriptor (via `FileDescriptorCast#duplicating(FileDescriptor)` circumvents this
  problem, at the cost of using additional system resources.
- As a consequence of the previous point: For `FileDescriptorCast#using(FileDescriptor)`: when casting file
  descriptors that belong to a junixsocket-controlled socket, the target socket is configured in a
  way such that garbage collection will not automatically close the target's underlying file
  descriptor (but still potentially any file descriptors received from other processes via
  ancillary messages).
- The same restrictions as for `#using(FileDescriptor)` apply to `#unsafeUsing(int) as well.
