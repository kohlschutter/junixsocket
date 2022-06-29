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
	Class<T> desiredClass = ...;
	T instance = FileDescriptorCast.using(fd).as(desiredClass);

For example, if you want to simply read from this file descriptor, use:

	InputStream in = FileDescriptorCast.using(fd).as(InputStream.class);

If the file descriptor is a Socket, you can use:

	Socket sock = FileDescriptorCast.using(fd).as(Socket.class);
	// or:
	AFSocket sock = FileDescriptorCast.using(fd).as(AFSocket.class);
	// or:
	AFUNIXSocket sock = FileDescriptorCast.using(fd).as(AFUNIXSocket.class);
	// etc.

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
