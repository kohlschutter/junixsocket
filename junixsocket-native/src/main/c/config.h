/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef config_h
#define config_h

#include "ckmacros.h"

#if !defined(DEBUG)
#define DEBUG 1
#endif

CK_IGNORE_UNUSED_MACROS_BEGIN
#define _GNU_SOURCE 1
CK_IGNORE_UNUSED_MACROS_END

#if defined(__NetBSD__) && !defined(_NETBSD_SOURCE)
#  define _NETBSD_SOURCE
#endif

CK_IGNORE_RESERVED_IDENTIFIER_BEGIN
#include "jni/jni.h"
CK_IGNORE_RESERVED_IDENTIFIER_END

#if defined(_WIN32)
#  define WIN32_LEAN_AND_MEAN
#  undef WINVER
#  undef _WIN32_WINNT
#  define WINVER 0x0A00
#  define _WIN32_WINNT 0x0A00 // Target Windows 10
#  define _POSIX_SOURCE
#endif

#include <stddef.h>
#include <errno.h>
#if __TOS_MVS__
// z/OS doesn't have sys/param.h
#else
#include <sys/param.h>
#endif
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <limits.h>

CK_IGNORE_UNUSED_MACROS_BEGIN
#define junixsocket_have_sun_len // might be undef'ed below
#define junixsocket_have_ancillary // might be undef'ed below
#define junixsocket_have_pipe2 // might be undef'ed below
CK_IGNORE_UNUSED_MACROS_END

#if !defined(false)
#  define false JNI_FALSE
#  define true JNI_TRUE
#  define bool jboolean
#endif

#if __TOS_MVS__
#  undef junixsocket_have_ancillary
#  undef junixsocket_have_pipe2
#  define junixsocket_use_poll_for_read
#  define junixsocket_use_poll_for_accept
#endif

#if defined(_AIX)
#  define junixsocket_use_poll_for_accept
#  undef junixsocket_have_pipe2
#endif
#if defined(_OS400)
#  define JUNIXSOCKET_HARDEN_CMSG_NXTHDR 1
#endif

#if !defined(uint64_t) && !defined(_INT64_TYPE) && !defined(_UINT64_T) && !defined(_UINT64_T_DEFINED_)
#  ifdef _LP64
typedef unsigned long uint64_t;
#  else
typedef unsigned long long uint64_t;
#  endif
#endif

#if !defined(SOCKLEN_MAX)
    // from ruby's ext/socket/rubysocket.h
    #define SOCKLEN_MAX \
    (0 < (socklen_t)-1 ? \
    ~(socklen_t)0 : \
    (((((socklen_t)1) << (sizeof(socklen_t) * CHAR_BIT - 2)) - 1) * 2 + 1))
#endif

#if defined(_WIN32)

#  include <windows.h>
#  include <winsock2.h>
#  include <ws2tcpip.h>
#  include <time.h>

#  undef junixsocket_have_sun_len
#  undef junixsocket_have_ancillary

#  define junixsocket_use_poll_for_accept
#  define junixsocket_use_poll_interval_millis 1000

#  define WIN32_NEEDS_CHARP (char *)

#  if !defined(sockaddr_un) // afunix.h
#    define UNIX_PATH_MAX 108
typedef struct sockaddr_un
{
    ADDRESS_FAMILY sun_family;
    char sun_path[UNIX_PATH_MAX];
}sockaddr_un;
#  endif

#  if !defined(SIO_AF_UNIX_GETPEERPID)
// https://microsoft.github.io/windows-docs-rs/doc/windows/Win32/Networking/WinSock/constant.SIO_AF_UNIX_GETPEERPID.html
// https://www.magnumdb.com/search?q=IOC_VENDOR
// https://www.mail-archive.com/mingw-w64-public@lists.sourceforge.net/msg18854.html
#    define SIO_AF_UNIX_GETPEERPID _WSAIOR(IOC_VENDOR, 256)
#  endif

// Redefining these errors simplifies WinSock error handling
// make sure you're not using these error codes for anything not WinSock-related
CK_IGNORE_UNUSED_MACROS_BEGIN
#  undef ENOTCONN
#  define ENOTCONN WSAENOTCONN
#  undef EINVAL
#  define EINVAL WSAEINVAL
#  undef EADDRINUSE
#  define EADDRINUSE WSAEADDRINUSE
#  undef EWOULDBLOCK
#  define EWOULDBLOCK WSAEWOULDBLOCK
#  undef ECONNREFUSED
#  define ECONNREFUSED WSAECONNREFUSED
#  undef ENOTSOCK
#  define ENOTSOCK WSAENOTSOCK
CK_IGNORE_UNUSED_MACROS_END

#  if !defined(clock_gettime) // older time.h
#  define junixsocket_clock_gettime_impl 1
int clock_gettime(int ignored CK_UNUSED, struct timespec *spec);
# endif

#  if !defined(WSA_FLAG_NO_HANDLE_INHERIT) // older Windows API
#    define WSA_FLAG_NO_HANDLE_INHERIT 0x80
#  endif

#else // not windows:
#  include <sys/ioctl.h>
#  include <sys/socket.h>
#  include <sys/uio.h>
#  include <sys/un.h>
// #  define SOCKET int
// #  define INVALID_SOCKET -1
#  define WIN32_NEEDS_CHARP
#endif

#if __TOS_MVS__
// z/OS XLC doesn't have __has_include
#else
#  if __has_include(<sys/cdefs.h>)
#    include <sys/cdefs.h>
#  endif
#  if __has_include(<sys/ucred.h>)
#    include <sys/ucred.h>
#  endif
#endif

#if !defined(JUNIXSOCKET_HARDEN_CMSG_NXTHDR) && defined(__BSD_VISIBLE)
// OpenBSD: use our harden logic to get rid of an alignment warning
#  define JUNIXSOCKET_HARDEN_CMSG_NXTHDR 1
#endif

#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <stdbool.h>

#ifndef FIONREAD
#include <sys/filio.h>
#endif
#ifdef __cplusplus
extern "C" {
#endif

#if defined(SOCK_CLOEXEC)
#define junixsocket_have_accept4
#define junixsocket_have_socket_cloexec
#endif

// Linux
#ifdef __linux__
#undef junixsocket_have_sun_len

#if !defined(JUNIXSOCKET_HARDEN_CMSG_NXTHDR)
// workaround for systems using musl libc
#  define JUNIXSOCKET_HARDEN_CMSG_NXTHDR 1
#endif

#include <linux/tipc.h>
#include <arpa/inet.h>
#define junixsocket_have_tipc 1

#endif

// Solaris
#if defined(__sun) || defined(__sun__)
#undef junixsocket_have_sun_len
#define junixsocket_use_poll_for_accept
#define junixsocket_use_poll_for_read

#undef DEBUG // prevent unresolved symbol "__iob" (triggered by referencing stderr)

#endif

// Tru64
#ifdef __osf__
#undef junixsocket_have_sun_len
#undef  recv
#undef  send
#define recv(a,b,c,d)   recvfrom(a,b,c,d,0,0)
#define send(a,b,c,d)   sendto(a,b,c,d,0,0)
typedef unsigned long socklen_t; /* 64-bits */
#endif

#if defined(__BSD_VISIBLE)
#  if defined(__MACH__) || defined(__FreeBSD__) || defined(__NetBSD__) || defined(__DragonFly__)
// not OpenBSD
#  else
// probably OpenBSD
#  define junixsocket_accept_infinite_timeout_workaround
#  endif
#endif

#if defined(__MACH__)
#  if !defined(_DARWIN_C_SOURCE)
#    define _DARWIN_C_SOURCE 1
#  endif
#endif

#if defined(__MACH__) || defined(__FreeBSD__) || defined(__NetBSD__) || defined(__DragonFly__) || defined(__BSD_VISIBLE)
#  define junixsocket_use_poll_for_accept
//#define junixsocket_use_poll_interval_millis    1000
#  define junixsocket_use_poll_for_read
#  include <sys/ucred.h>
#  include <sys/poll.h>
#  if !defined(__NetBSD__)
#    include <sys/user.h>
#  endif
#  if !defined(SOL_LOCAL)
#    define SOL_LOCAL               0
#  endif
#endif

#if defined(__MACH__) || defined(__sun__)
#undef junixsocket_have_pipe2
#endif

#if !defined(_WIN32)
#  include <poll.h>
#endif
#include <limits.h>
#include <time.h>

#if defined(LOCAL_PEEREUUID)
#  include <uuid/uuid.h>
#endif

// Windows requires us fetching errno for socket-related errors
#if defined(_WIN32)
#  define socket_errno (errno = WSAGetLastError())
#  define ssize_t int
#elif defined(_OS400)
CK_VISIBILITY_INTERNAL
int jux_mangleErrno(int);
#   define socket_errno (errno = jux_mangleErrno(errno))
#else
#  define socket_errno errno
#endif

#if __GLIBC__
#  if __CROSSCLANG_NODEPS__
// nothing to do
#  else
// This allows us to link against older glibc versions
#    define memcpy memmove
#    ifndef _STAT_VER
#        if defined(__aarch64__) || defined(__riscv)
#            define _STAT_VER 0
#        elif defined(__x86_64__)
#            define _STAT_VER 1
#        else
#            define _STAT_VER 3
#        endif
#    endif
#    if !defined(__xstat)
extern int __xstat (int __ver, const char *__filename,
                    struct stat *__stat_buf) __THROW __nonnull ((2, 3));
#    endif
#    define stat(...) __xstat(_STAT_VER, __VA_ARGS__)
#  endif
#endif

#if defined(MSG_DONTWAIT)
#  define junixsocket_have_MSG_DONTWAIT 1
#else
#  define junixsocket_have_MSG_DONTWAIT 0
#endif

#if !defined(MIN)
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#endif

#include "org_newsclub_net_unix_NativeUnixSocket.h"

#endif /* config_h */
