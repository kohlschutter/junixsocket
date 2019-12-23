# Sending and receiving File Descriptors

A very useful feature of Unix Domain Sockets is the ability to send and receive file descriptors
between processes.

This, for example, enables non-privileged processes to access otherwise restricted files:
A privileged process (e.g., running as root, or as a UID with special access rights) opens the
restricted file, and then exposes the file handle to another process via AF_UNIX sockets.

File descriptors are sent as so-called "ancillary messages" along with regulary payload.
They cannot be sent alone, so make sure that you send at least some data.

## Sending file descriptors

    AFUNIXSocket socket = ...
    
    FileInputStream fin = new FileInputStream(file);
    socket.setOutboundFileDescriptors(fin.getFD());
    // you can also send more than one FD at the same time, just make sure they're all part of the same call to setOutboundFileDescriptors. 
    
    // Ancillary messages are sent _along_ regular in-band messages, so we have to send something here.
    os.write("Some message".getBytes("UTF-8"));

## Receiving file descriptors

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
    
## Even easier with RMI

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

## Due diligence
 
 It is recommended to authenticate the connection, since you certainly don't want unauthorized callers
 to obtain access to potentially secret information.
 
 You may either use your own SSL/TLS authentication atop the Socket, or simply use [peer credentials](peercreds.html).

## Not supported by all platforms

 Not all platforms support file descriptors over AF_UNIX. Make sure they're available using
> `AFUNIXSocket.supports(AFUNIXSocketCapability.CAPABILITY_FILE_DESCRIPTORS)`.
