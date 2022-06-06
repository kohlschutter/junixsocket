# Peer Credentials

Unix Domain sockets enables a simple two-way authentication to ensure the other end is entitled to
communicate.

A call to `AFUNIXSocket#getPeerCredentials()` returns an object with information about the peer (the other
end).

> **NOTE:** Depending on your operating system, not all properties are available (and therefore `null` or `-1`).

    AFUNIXSocket socket = ...
    
    // Once the socket is connected, you can obtain the credentials:
    AFUNIXSocketCredentials credentials = socket.getPeerCredentials();
    
    credentials.getPid(); // Returns the PID (Process ID), -1 means "could not retrieve".
    credentials.getUid(); // Returns the UID (User ID), 0 means "root", -1 means "could not retrieve".
    credentials.getGids(); // Returns the GIDs (Group IDs), the first one is the "primary" GID, null means "could not retrieve".
    credentials.getGid(); // Returns the primary GIDs (Group ID), same as the first entry in getGids(); -1 means "could not retrieve".
    credentials.getUUID(); // Returns the process binary's unique ID, null means "could not retrieve"

> **LIMITATION:** The UUID is currently only supported on macOS. It is the identifier created by the linker,
which means it may change upon recompilation, but should remain stable otherwise.
> 
> You can use `otool` to inspect the UUID for a binary: `otool -l <path-to-binary> | grep uuid`
> 
> **IMPORTANT:** All Java programs using the same JVM binary share the same UUID. 

## Authenticating RMI connections with Peer Credentials

`junixsocket-rmi` not only allows RMI connections over AF_UNIX sockets, it also makes it very easy
to authenticate these connections with the help of peer credentials:

    // Returns the peer credentials of a client calling our RMI method
    // Helps answering the question "is the caller authorized"?
    AFUNIXSocketCredentials credentials = RemotePeerInfo.remotePeerCredentials();

    // Returns the peer credentials of a server providing the given remote object
    // Helps answering the question "is the service trusted"?
    AFUNIXSocketCredentials credentials = RemotePeerInfo.remotePeerCredentials(someRemoteObject);

## Not supported by all platforms

Not all platforms support file descriptors over AF_UNIX. Make sure they're available using
> `AFUNIXSocket.supports(AFUNIXSocketCapability.CAPABILITY_PEER_CREDENTIALS)`.
