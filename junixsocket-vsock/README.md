# junixsocket-vsock

Use AF_VSOCK sockets from Java!

[junixsocket](https://kohlschutter.github.io/junixsocket/) is an Apache 2.0-licensed Java/JNI
library that allows the use of [Unix Domain
Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets), and other
address/protocol families (such as [TIPC](http://tipc.io/) and VSOCK), from Java.

## What is AF_VSOCK?

Virtual sockets ("Vsock") allow communication between virtual machines (VMs, "guests") and their
hosts, using practically the same API that is used for Internet (AF_INET), Unix Domain (AF_UNIX),
etc., sockets.

### Addressing

The addressing schemee, in C it's `struct sockaddr_vm`, mainly has two address parameters, the port
(an unsigned 32-bit integer value) and a CID (Context identifier, also a 32-bit integer).

Ports between 0 and 1023 are privileged ports, so they can only be created with the correct set of
permissions.  There is a wildcard port "any" (`VMADDR_PORT_ANY`), indicating that a random port
should be bound to.

There are some special CID values, such as "hypervisor" (the programming running the VM,
`VMADDR_CID_HYPERVISOR`) and "host" (the machine the hypervisor runs on, `VMADDR_CID_HOST`), as well
as a wildcard CID ("any available CID", `VMADDR_CID_ANY`).

Some implementations, such as Linux, provide a "local-only" CID for loopback testing
(`VMADDR_CID_RESERVED`), other systems, such as macOS, allow retrieving the CID of the guest in lieu
of a constant (junixsocket provides wrapper code for macOS to handle a local-only CID transparently
to the user).

### Limitations

`AF_VSOCK` sockets are not available on all Operating systems, and may only be available from within
a VM.  Moreover, not all connection types (such as datagrams) may be available.

Generally, support for `AF_VSOCK` may differ from a system that is not set up for virtualization, a
system that is a host running a hypervisor, and a system being run as a guest on such a host.

On Linux, depending on kernel version and configuration, no support may be available, some support
(stream sockets only), or both streams and datagrams.  Host kernel and guest kernel implementations
may be different (VHOST vs VIRTIO).  Permissions may prevent a user from accessing VSOCK (e.g., when
`/dev/vsock` is inaccessible).

On macOS, `AF_VSOCK` is currently only available from within a virtual machine, and, when using
`Virtualization.framework`, a `VZVirtioSocketDeviceConfiguration` must be present.  In that case,
the communication with the outside world is implementation-specific (via a `VZVirtioSocketDevice`
configured for a `VZVirtualMachine`.).  Communication with the guest from the host is then
facilitated via custom means, for example via a shared host-side `AF_UNIX` socket that acts as a
proxy to the guest-side `AF_VSOCK` sockets.

When using qemu (from Linux), add `-device vhost-vsock-pci,guest-cid=3` to enable VSOCK in the guest
VM (optionally, change 3 to the desired guest CID).  Make sure that, on the host, `/dev/vhost-vsock`
is accessible by your process, and maybe use `modprobe vhost_vsock` to ensure that the host-side
vsock implementation is loaded,

## How can I use `AF_VSOCK` sockets in Java with junixsocket?

### Selftest

First of all, please run the [junixsocket
selftest](https://kohlschutter.github.io/junixsocket/selftest.html) to make sure everything works as
expected.

### Maven dependencies

Add the following dependency to your project (replace X.Y.Z.  with the [latest junixsocket
version](https://kohlschutter.github.io/junixsocket/changelog.html)).

    <dependency>
        <groupId>com.kohlschutter.junixsocket</groupId>
        <artifactId>junixsocket-core</artifactId>
        <version>X.Y.Z</version> <type>pom</type>
    </dependency>
    
    <dependency>
        <groupId>com.kohlschutter.junixsocket</groupId>
        <artifactId>junixsocket-vsock</artifactId>
        <version>X.Y.Z</version>
    </dependency>

### Working with `AF_VSOCK` sockets: `AFVSOCKSocketAddress`

One starting point to using VSOCK with junixsocket is a custom `SocketAddress` class,
`AFVSOCKSocketAddress`.

Having such an address lets you work with with `AFVSOCKSocket`, `AFVSOCKServerSocket`,
`AFVSOCKSocketChannel`, etc.

There are several ways of creating an `AFVSOCKSocketAddress`, such as:

1.  `AFVSOCKSocketAddress.ofPortAndCID` (takes a `port` and `CID` value, as well as an optional,
preceding `javaPort`). CID constants are available through `AFVSOCKSocketAddress`.

2.  `AFVSOCKSocketAddress.ofHostPort` (`ofHypervisorPort`, `ofLocalPort`, `ofPortWithAnyCID`)
(takes a `port` and uses the corresponding CID).

3.  `AFVSOCKSocketAddress.ofAnyPort` (takes a random port for binding)

`ofPortAndCID` allows specifying a `javaPort` value, which emulates an `InetSocketAddress` port
very much like `AFUNIXSocketAddress` does, to enable using these addresses in Java programs that
rely on port numbers.  Such `javaPort` values are not carried over to the file descriptor.

Please file a [New Issue](https://github.com/kohlschutter/junixsocket/issues) if you encounter any
problems.

### "Local CID"

Inspired by support in modern Linux kernels, a special CID of `VMADDR_CID_HOST` (=`1`) is available
in junixsocket.

If no native support for this CID is available in the kernel, an attempt is made to use the system's
local CID instead (which is resolved upon initialization of the native library).

The resolved local CID can be obtained via `AFVSOCKSocket.getLocalCID()`.

If the system's local CID cannot be resolved, `VMADDR_CID_ANY` (=`-1`) is used as a fallback, unless
access to `/dev/vsock` was denied, which implies `VMADDR_CID_HOST` (=`2`).

### Capabilities, Exceptions

junixsocket provides capability checks (via `AFSocketCapability`, `CAPABILITY_VSOCK` and
`CAPABILITY_VSOCK_DGRAM`) to ensure that the environment the program runs in is well-understood
before execution.

Moreover, if some known issues are detected during `connect` or `bind`, a custom
`InvalidSocketException` may be thrown.

## Security aspects, known vulnerabilities

`AF_VSOCK` connections were not designed to be reachable from the public Internet.

There are known security vulnerabilities in older Linux kernels and hypervisors related to `VSOCK`,
but not to junixsocket's adaptation.

It is generally recommended to use up-to-date versions of the kernel/hypervisors.

Recent known Linux Kernel / qemu issues related to `VSOCK` (list is non-exhausive):

* CVE-2022-26354: memory leakage in vhost-vsock :— Affected QEMU versions <= 6.2.0.

* CVE-2021-26708: local privilege escalation in Linux before 5.10.13

* CVE-2019-18960: buffer overflow in Firecracker version 0.18/0.19.0.

* CVE-2018-14625: uncontrolled kernel read from VM guest in Linux kernel

> **NOTE**: These security issues are not related to junixsocket's implementation.  They refer to
vulnerabilities in older versions of the Linux/qemu/Firecracker kernel.

## References

* [vsock(7)](https://man7.org/linux/man-pages/man7/vsock.7.html) Linux manual

* [VSOCK(4)](https://keith.github.io/xcode-man-pages/vsock.4.html) macOS Device Drivers Manual
(xcode man-page, mirror)

* [Features/VirtioVsock](https://wiki.qemu.org/Features/VirtioVsock) QEMU Wiki

* [junixsocket-vsock javadoc](https://kohlschutter.github.io/junixsocket/junixsocket-vsock/apidocs/org.newsclub.net.unix.vsock/org/newsclub/net/unix/vsock/package-summary.html)

* [junixsocket-vsock source code](https://kohlschutter.github.io/junixsocket/junixsocket-vsock/xref/index.html)

* [junixsocket-vsock unit tests](https://kohlschutter.github.io/junixsocket/junixsocket-vsock/xref-test/index.html)

* [AF_VSOCK: nested VMs and loopback support available](https://stefano-garzarella.github.io/posts/2020-02-20-vsock-nested-vms-loopback/)
   ... since Linux 5.5/5.6

## See also

* [Hyper-V](https://de.wikipedia.org/wiki/Hyper-V) (related technology); Wikipedia page
