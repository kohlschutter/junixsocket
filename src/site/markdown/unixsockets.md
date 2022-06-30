# Unix socket reference

Unix sockets, UNIX-domain sockets, Unix domain protocol family, POSIX local inter-process communication socket, POSIX local IPC socket, `AF_UNIX`, `PF_UNIX`, `PF_LOCAL`, `AF_FILE`, `PF_FILE` —
all these terms more or less refer to the same concept — a host-internal protocol and addressing
scheme that is more or less like socket communication between computers on the Internet.
Typically, but not always, Unix sockets are referenced by pathnames on the file system.

Unix sockets are available on a variety of platforms (including macOS, Windows and Linux), and they all
differ a little in how they work.

Here are some references that may be useful to you.

## man pages

man pages are a great reference to know more about what your particular system understands and supports.

Try the following commands on your machine:

* `man unix`       (or: `man 4 unix`, `man 7 unix`)
* `man socket`     (or: `man 2 socket`)
* `man socketpair`
* `man af_local`

Make sure you follow the references to other man pages in the `SEE ALSO` sections of each man page.

You can also search for references to `unix` and `socket` in the man pages using the `apropos` command:

* `apropos socket`
* `apropos unix`

Here are some man pages on the web for different platforms:

* Linux: [unix(7)](http://man7.org/linux/man-pages/man7/unix.7.html), [socket(2)](http://man7.org/linux/man-pages/man2/socket.2.html)
* macOS: [unix(4)](https://www.unix.com/man-page/mojave/4/unix/), [socket(2)](https://www.unix.com/man-page/mojave/2/socket/)
* Illumos/OpenSolaris: [socket(3SOCKET)](https://illumos.org/man/3socket/socket), [socket(3HEAD)](https://illumos.org/man/3HEAD/socket).
* FreeBSD: [unix(4)](https://www.freebsd.org/cgi/man.cgi?query=unix&sektion=4), [socket(2)](https://www.freebsd.org/cgi/man.cgi?query=socket&sektion=2)
  * The FreeBSD website also covers other operating systems. Just select the desired operating system from the drop-down
   menu (e.g., NetBSD, OpenBSD, HP-UX, 4.3BSD NET/2
* z/OS: [Addressing within the AF_UNIX domain](https://www.ibm.com/support/knowledgecenter/en/SSLTBW_2.3.0/com.ibm.zos.v2r3.cbcpx01/adiucvd.htm), [Using z/OS UNIX sockets](https://www.ibm.com/support/knowledgecenter/SSLTBW_2.3.0/com.ibm.zos.v2r3.cbcpx01/ssda.htm)
* POSIX: [7P unix](https://www.unix.com/man-page/all/7P/unix/), [3POSIX socket](https://www.unix.com/man-page/all/3posix/socket/), [7POSIX un.h](https://www.unix.com/man-page/all/7posix/un.h/)
 * Also check out the [man page repository](https://www.unix.com/man-page-repository.php) on the unix.com website, which provides entire man page sets for different operating systems.
* Windows: Since Build 17063 (Fall Creators Update?), even Microsoft Windows 10 supports AF_UNIX:
[AF_UNIX comes to Windows](https://blogs.msdn.microsoft.com/commandline/2017/12/19/af_unix-comes-to-windows/), [Windows/WSL Interop with AF_UNIX](https://blogs.msdn.microsoft.com/commandline/2018/02/07/windowswsl-interop-with-af_unix/) — check availability using `sc query afunix`.
The driver is `afunix.sys`, the headers are in `<afunix.h>`. 
* QNX Neutrino RTOS: [unix_proto](http://www.qnx.com/developers/docs/7.0.0/index.html#com.qnx.doc.neutrino.lib_ref/topic/u/unix_proto.html), [socket](http://www.qnx.com/developers/docs/7.0.0/index.html#com.qnx.doc.neutrino.lib_ref/topic/s/socket.html)


## Headers

You can't go wrong by looking at the system headers (maybe under `/usr/include`, or elsewhere on your system).

Look for the following userspace headers:

* `<sys/un.h>`
* `<sys/socket.h>`.

Here are some platform-specific references:

* Linux:
	* userspace headers are defined in glibc/dietlibc/musl, etc.
	* [kernel source](https://github.com/torvalds/linux/blob/master/net/unix/af_unix.c)
  
  You may need to `#define _GNU_SOURCE` before importing these headers to unlock GNU-specific extensions.
  
* macOS: [sys/un.h](http://newosxbook.com/src.jl?tree=xnu&file=/bsd/sys/un.h), [sys/socket.h](http://newosxbook.com/src.jl?tree=xnu&file=/bsd/sys/socket.h)

  You may need to `#define _DARWIN_C_SOURCE` before importing these headers
  to unlock Darwin/macOS-specific extensions.

* Open Group Base Specification: [sys/un.h](http://pubs.opengroup.org/onlinepubs/009604499/basedefs/sys/un.h.html), [sys/socket.h](http://pubs.opengroup.org/onlinepubs/009604499/basedefs/sys/socket.h.html)
* Windows: `<afunix.h>`

## Port numbers

Unlike Internet socket addresses, Unix domain socket addresses do not support a numeric *port* component.

[junixsocket](https://kohlschutter.github.io/junixsocket/) provides an API that shims in support for ports, in order to support Java RMI over Unix sockets, for example.

## Other references

   * junixsocket's [JNI code](https://github.com/kohlschutter/junixsocket/tree/master/junixsocket-native/src/main/c)
   * Wikipedia: [Unix domain socket](https://en.wikipedia.org/wiki/Unix_domain_socket)

## Platform-specific limits and extensions

### Length of a socket path (`sun_path`)

Some operating systems, such as HP/UX, limit `sun_path` to only be up to 92 bytes. macOS, QNX and 4.4 BSD limit them to 104. Linux, 4.3 BSD and z/OS have a limit of 108 bytes. A (sometimes) mandatory terminating null byte is included in this calculation.

### Socket types

Usually, all platforms support `SOCK_STREAM` (stream sockets), most support `SOCK_DGRAM` (datagram sockets), and some also support `SOCK_SEQPACKET`
(sequenced packet socket with preserved message boundaries).

### Abstract namespace

Some platforms, such as Linux, QNX and Windows 10, support a namespace for sockets that is not connected to the filesystem. A socket is in the abstract namespace if it starts with a null byte — the remainder of the data structure is part of the name, and null bytes may have no special meaning.

`unlink` upon destruction is typically not necessary for such sockets, and `chmod`/`chown` has no effect.

`SUN_LEN` probably shouldn't be used here to determine the length of the `sockaddr_un` since it uses `strlen`.

Some platforms (Linux only?) can automatically assign names in the abstract namespace by means of "autobind", whereas the name commonly consists of the starting null byte followed by 5 bytes using hexadecimal characters (0-9, a-f).

### Unnamed sockets

A pair of sockets can be created using `socketpair`, which may not always be supported.

### Transmit file descriptors, credentials

Ancillary messages / ancillary data allows passing special information over these sockets that is not just binary data. These messages are usually transmitted via `sendmsg`/`recvmsg`, and they're practically all very platform-specific.

* `SCM_RIGHTS`: Send or receive file descriptors (or more generically, "access rights").
  
  The used data structures vary (e.g., `struct cmsgcred`, `struct cmsghdr`).
  
  Since on UNIX, practically everything can be referenced through a file descriptor, this feature can be very powerful (e.g., see [Android AHardwareBuffer Shared Memory over Unix Domain Sockets](https://medium.com/@spencerfricke/android-ahardwarebuffer-shared-memory-over-unix-domain-sockets-7b27b1271b36)).

* `SCM_CREDENTIALS`/`SCM_UCRED`: Send process credentials (e.g., UID, GID, etc.), which can be used to authenticate a caller.
  
  In order to receive these credentials, the socket must have a `SO_PASSCRED`/`SO_RECVUCRED` option enabled. Also see the `SOL_LOCAL`-level socket options on FreeBSD and macOS, such as `LOCAL_CRED`, `LOCAL_PEERCRED`, `LOCAL_PEERPID`,  `LOCAL_PEEREPID`, `LOCAL_PEERUUID`, `LOCAL_PEEREUUID`, etc.
  
  Note that authenticating using a particular PID may be insecure as PIDs may be reused for new processes.

  The used data structures vary (e.g., `struct xucred`, `struct ucred`, `ucred_t`)
 
### Socket options
 
* `SOL_SOCKET` level:
	* `SO_RCVBUF` has no effect.
	* `SO_SNDBUF` imposes an upper limit for outgoing datagrams.
	* `SO_PASSCRED`/`SO_RECVUCRED` enables the receipt of file descriptors
	* `SO_NOSIGPIPE` disable raising the `SIGPIPE` signal upon broken pipe.
	
* `SOL_LOCAL` level (FreeBSD, macOS):
	* `LOCAL_CRED`, `LOCAL_PEERCRED`, `LOCAL_PEERPID`,  `LOCAL_PEEREPID`, `LOCAL_PEERUUID`, `LOCAL_PEEREUUID`: Receive peer credentials

### ioctls

* `FIONREAD`/`SIOCINQ`: For stream sockets, returns the amount of unread bytes in the receive buffer. For datagram sockets, returns the size of either the next pending datagram, or all pending data. Note that the datagram size could be 0 bytes, so this is probably not a good replacement for `select`.

## Command-line tools

> **NOTE:** Not all commands and options are available on all platforms.

### Enumerate available sockets

> **NOTE:** On Linux, when showing abstract namespace paths, null bytes are converted to `@`. Older tool versions may not handle zero bytes properly.

#### lsof

List unix sockets:

	lsof -U

Also show peer processes/endpoints (Linux-only):

	lsof +E -U

Combine with other parameters (and `-a`) to restrict to a particular user, process, etc., e.g., `sudo lsof -a -U -u root`

#### ss (Linux)

List unix sockets:

	ss -x

Also show peer processes/endpoints (it appears that your kernel needs to have `UNIX_DIAG` enabled for this.):

	ss -xp

#### netstat

Show listening sockets:

	netstat --unix -l

Show connected sockets:

	netstat --unix

#### Filesystem

On Linux, you can get a list of all UNIX sockets:

	cat /proc/net/unix
	
On Android, many sockets are defined in `/dev/socket/`.

### Communicate with Unix sockets

#### netcat / nc

Connect to an existing stream socket

	nc -U /path/to/socket

Create a listening stream socket

	nc -lU /path/to/socket

Datagrams

  * Some versions of netcat support datagram sockets by adding another `-u` parameter.

#### socat

socat allows forwarding between two different sockets, as well as piping from and to STDIN, etc. (see `man socat`).

Check out the address types `UNIX-CONNECT`, `UNIX-LISTEN`, `UNIX-SENDTO`, `UNIX-RECVFROM`, `UNIX-RECV`, `UNIX-CLIENT`. On Linux, replace `UNIX-` by `ABSTRACT-`, and you can access sockets in the abstract namespace.

Example:

	socat - UNIX-CONNECT:/path/to/socket

#### websocat

The socat-like tool for WebSockets also [has support](https://github.com/vi/websocat/blob/master/doc.md) for unix sockets.

Examples

* Websocket to Unix socket:

		websocat ws-l:127.0.0.1:8088 unix:the_socket

* Listen socket forwarding to Websocket:

		websocat --unlink unix-l:the_socket ws://127.0.0.1:8089


#### curl

curl can send HTTP requests over Unix sockets. This is useful for communication with [Docker](https://www.docker.com/), for example:

	curl --unix-socket /var/run/docker.sock http://localhost/images/json
	
## Servers

### Webservers

Web servers like [nginx](https://www.nginx.com/) provide support to serve content over unix sockets, and curl can be used to test them.

### Databases

MySQL and PostgreSQL provide access to the database via Unix Sockets. Since the communication over TCP/IP usually requires SSL, this encryption can be disabled on the Unix domain socket connection, which should improve performance.

[junixsocket](https://kohlschutter.github.io/junixsocket/) provides JDBC adapters for both.

## Security

### File permissions

As a good practice, place unix sockets in a directory with tight access controls. On systems that support file-level controls on the socket itself, use `chown`/`chmod` to further restrict access, but do not rely on this alone.

Independent of directory-level access controls, the names of all unix sockets may be available through `/proc/net/unix` or similar APIs.

### Abstract namespace

Since there are no filesystem-level permission enforced upon socket names in the abstract namespace, one should consider them less secure than those specified by file path. Checking peer credentials upon establishing a connection (in _both_ directions!) could make up for this, but that doesn't come for free.

### Android

The wild west. Applications with the `INTERNET` permission to access the Internet also have access to Unix domain sockets. A lot of sockets are unsecured.

See [Yuru et al., The Misuse of Android Unix Domain Sockets and Security Implications](http://web.eecs.umich.edu/~yurushao/pubs/sinspector_ccs2016.pdf) (with [presentation slides](http://web.eecs.umich.edu/~yurushao/pubs/sinspector_ccs2016_slides.pdf)) for an analysis of what can go wrong.

Also see [Jiang et al., Unix Domain Sockets Applied in Android Malware Should Not Be Ignored](https://pdfs.semanticscholar.org/4c06/fab7c351851f5e13305ede29fd73cf1cf031.pdf).

## Anything missing or wrong?

Feel free to reach out by email, <christian@kohlschutter.com>, or file an [issue](https://github.com/kohlschutter/junixsocket/issues) or [pull request](https://github.com/kohlschutter/junixsocket/pulls) with junixsocket.
