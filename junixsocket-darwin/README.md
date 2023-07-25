# junixsocket-darwin

Darwin (macOS)-specific sockets from Java!

[junixsocket](https://kohlschutter.github.io/junixsocket/) is an Apache 2.0-licensed Java/JNI library that allows the use of
[Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets), and
other address/protocol families (such as [TIPC](http://tipc.io/)), from Java.

## AF_SYSTEM

`AF_SYSTEM` is one way to communicate with the macOS (Darwin) Kernel from userspace.

Most often, you still have to be root to connect, however you may of course send a file descriptor
to a lesser privileged process and continue the work there.

One feature junixsocket exposes is `UTUN_CONTROL`, which lets you set up PtP tunnel interfaces (`utun`)
from userspace. This can be used to implement VPNs, for example. AF_SYSTEM with UTUN_CONTROL is
the macOS pendant to Linux's `tun` functionality.

### Examples

* [UtunTest — Run a VPN tunnel from Java](https://kohlschutter.github.io/junixsocket/junixsocket-darwin/xref-test/org/newsclub/net/unix/darwin/system/UtunTest.html)
* Also see [junixsocket-darwin's unit tests](https://kohlschutter.github.io/junixsocket/junixsocket-darwin/xref-test/index.html)

## AF_NDRV

This is currently not supported, but it's pretty much a "Fake Ethernet" driver.

It's the macOS pendant to Linux's `tap` functionality.

A future version of junixsocket may add support for this domain.

## AF_MULTIPATH

This is an Apple-private domain to implement Multipath-TCP/IP.

A future version of junixsocket may add support for this domain.

## Maven dependencies

Add the following dependency to your project (replace X.Y.Z. with the
[latest junixsocket version](https://kohlschutter.github.io/junixsocket/changelog.html)).

    <dependency>
        <groupId>com.kohlschutter.junixsocket</groupId>
        <artifactId>junixsocket-darwin</artifactId>
        <version>X.Y.Z</version>
    </dependency>
    
## References

* [New OSX Book Volume 1 Chapter 16 — Нет-Work: Darwin Networking](http://newosxbook.com/bonus/vol1ch16.html)
* [XNU kernel source](https://github.com/apple-oss-distributions/xnu)
