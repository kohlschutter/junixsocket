# junixsocket-tipc

Use TIPC sockets from Java!

[junixsocket](https://kohlschutter.github.io/junixsocket/) is an Apache 2.0-licensed Java/JNI library that allows the use of
[Unix Domain Sockets](https://en.wikipedia.org/wiki/Unix_domain_socket) (AF_UNIX sockets), and
other address/protocol families (such as [TIPC](http://tipc.io/)), from Java.

## What is TIPC

From [TIPC's page on Wikipedia](https://en.wikipedia.org/wiki/Transparent_Inter-process_Communication):

> Transparent Inter Process Communication (TIPC) is an Inter-process communication (IPC) service in Linux designed for cluster-wide operation. It is sometimes presented as Cluster Domain Sockets, in contrast to the well-known Unix Domain Socket service; the latter working only on a single kernel.

(see the Wikipedia page for more details).

What makes TIPC special is that it allows communication between services in a cluster using  _service addresses_  
instead of  _machine-specific_  addresses. This enables high-availability setups where more than one
machine can answer requests for a particular service. Failure discovery is relatively quick, and
setup requirements are minimal.

You can also use TIPC as broker-less message bus. Datagram and connection-oriented setups are supported. 

TIPC can run directly over Ethernet and also on top of UDP, enabling multi-site setups.

## How can I use TIPC sockets in Java with junixsocket?

### Linux Kernel with TIPC

First, you need an environment that supports TIPC. Right now, TIPC is mostly a Linux-only endeavor;
thankfully it is included with the Linux kernel, and many distributions provide a kernel module
by default. To load the module, run the following command:

    sudo modprobe tipc

Depending on your setup, you may need to install packages to use the `tipc` command-line tool. It
is usually included in the `iproute2` or `iproute2-rdma` package.

You also need to enable one or more "bearers". In order to enable TIPC over Ethernet (eth0), use

    tipc bearer enable media eth device eth0

See [TIPC.io: Getting Started](http://tipc.io/getting_started.html) for details.

Once configured, please run the [junixsocket selftest](https://kohlschutter.github.io/junixsocket/selftest.html)
to make sure everything works as expected.

### Maven dependencies

Add the following dependency to your project (replace X.Y.Z. with the
[latest junixsocket version](https://kohlschutter.github.io/junixsocket/changelog.html)).

    <dependency>
        <groupId>com.kohlschutter.junixsocket</groupId>
        <artifactId>junixsocket-tipc</artifactId>
        <version>X.Y.Z</version>
    </dependency>

### Working with AF_TIPC sockets: AFTIPCSocketAddress

One starting point to using TIPC with junixsocket is a custom `SocketAddress` class, `AFTIPCSocketAddress`.

Having such an address lets you work with with `AFTIPCSocket`, `AFTIPCServerSocket`, `AFTIPCSocketChannel`, etc.

There are three main ways of creating an `AFTIPCSocketAddress`:

    1. `AFTIPCSocketAddress.ofService` (takes a `type` and `instance` value, as well as an optional `scope`)
    2. `AFTIPCSocketAddress.ofServiceRange`  (takes a `type` and `instance` range values, as well as an optional `scope`)
    3. `AFTIPCSocketAddress.ofSocket` (takes a `ref` and `node` value).

All three options allow specifying a `javaPort` value, which emulates an `InetSocketAddress` port
very much like `AFUNIXSocketAddress` does, to enable using these addresses in Java programs that rely
on port numbers. Such `javaPort` values are not carried over to the file descriptor.

junixsocket's TIPC implementation also provides access to TIPC's custom socket options, via
`AFTIPCSocketOptions`. It also has a TIPC topology watcher (`AFTIPCTopologyWatcher`), and provides
access to the `DestName` (`AFTIPCDestName`) and `ErrInfo` (`AFTIPCErrInfo`) data sent via ancillary messages.

Please file a [New Issue](https://github.com/kohlschutter/junixsocket/issues) if you encounter any problems.

## Security aspects, known vulnerabilities

TIPC connections are not designed to be reachable from the public Internet.

There are known security vulnerabilities in older Linux kernels related to TIPC. It is recommended
to use newer kernel versions (fixed around February 2022)

Recent known TIPC Linux Kernel issues (list is non-exhausive):

    * CVE-2021-43267: Remote Linux Kernel Heap Overflow | TIPC Module Allows Arbitrary Code Execution
    * CVE-2022-0435: Remote Linux Kernel Stack Overflow

> **NOTE**: These security issues are not related to junixsocket's implementation. They refer to
vulnerabilities in older versions of the Linux kernel.

## More examples

* [OkHttpClientTIPCDemo — Using a TIPC cluster as a high-availability HTTP serving environment](https://kohlschutter.github.io/junixsocket/junixsocket-demo/xref/org/newsclub/net/unix/demo/okhttp/OkHttpClientTIPCDemo.html)
* Also see [junixsocket-tipc's unit tests](https://kohlschutter.github.io/junixsocket/junixsocket-tipc/xref-test/index.html)

## References

* [TIPC homepage](http://tipc.io/)
* [TIPC Wikipedia article](https://en.wikipedia.org/wiki/Transparent_Inter-process_Communication)
* [TIPC documentation in the Linux kernel](https://www.kernel.org/doc/html/latest/networking/tipc.html)
* [TIPC IETF draft](https://datatracker.ietf.org/doc/html/draft-maloy-tipc-01.txt)

* [junixsocket-tipc javadoc](https://kohlschutter.github.io/junixsocket/junixsocket-tipc/apidocs/org.newsclub.net.unix.tipc/org/newsclub/net/unix/tipc/package-summary.html)
* [junixsocket-tipc source code](https://kohlschutter.github.io/junixsocket/junixsocket-tipc/xref/index.html)
* [junixsocket-tipc unit tests](https://kohlschutter.github.io/junixsocket/junixsocket-tipc/xref-test/index.html)
