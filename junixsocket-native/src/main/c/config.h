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

#define DEBUG 1

CK_IGNORE_UNUSED_MACROS_BEGIN
#define _GNU_SOURCE 1
CK_IGNORE_UNUSED_MACROS_END

#include "jni/jni.h"

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
#include <sys/param.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

CK_IGNORE_UNUSED_MACROS_BEGIN
#define junixsocket_have_sun_len // might be undef'ed below
#define junixsocket_have_ancillary // might be undef'ed below
CK_IGNORE_UNUSED_MACROS_END

#if !defined(uint64_t) && !defined(_INT64_TYPE) && !defined(_UINT64_T)
#  ifdef _LP64
typedef unsigned long uint64_t;
#  else
typedef unsigned long long uint64_t;
#  endif
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
CK_IGNORE_UNUSED_MACROS_END

#  if !defined(clock_gettime) // older time.h
#  define junixsocket_clock_gettime_impl 1
int clock_gettime(int ignored CK_UNUSED, struct timespec *spec);
# endif

#else // not windows:
#  include <sys/ioctl.h>
#  include <sys/socket.h>
#  include <sys/uio.h>
#  include <sys/un.h>
// #  define SOCKET int
// #  define INVALID_SOCKET -1
#  define WIN32_NEEDS_CHARP
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

// Linux
#ifdef __linux__
#undef junixsocket_have_sun_len

#if !defined(JUNIXSOCKET_HARDEN_CMSG_NXTHDR)
// workaround for systems using musl libc
#  define JUNIXSOCKET_HARDEN_CMSG_NXTHDR 1
#endif

#endif

// Solaris
#if defined(__sun) || defined(__sun__)
#undef junixsocket_have_sun_len
#define junixsocket_use_poll_for_accept
#define junixsocket_use_poll_for_read
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

#if defined(__MACH__) || defined(__FreeBSD__)
#  define junixsocket_use_poll_for_accept
//#define junixsocket_use_poll_interval_millis    1000
#  define junixsocket_use_poll_for_read
#  include <sys/ucred.h>
#  include <sys/poll.h>
#  include <sys/user.h>
#  if !defined(SOL_LOCAL)
#    define SOL_LOCAL               0
#  endif
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
#else
#  define socket_errno errno
#endif

#if __GLIBC__
#  if __CROSSCLANG_NODEPS__
// nothing to do
#  else
// This allows us to link against older glibc versions
#    define memcpy memmove
#if defined (__aarch64__)
#  define junixsocket_STAT_VER 0
#elif defined (__x86_64__)
#  define junixsocket_STAT_VER 1
#else
#  define junixsocket_STAT_VER 3
#endif
#    define stat(...) __xstat(junixsocket_STAT_VER, __VA_ARGS__)
#  endif
#endif

#if defined(MSG_DONTWAIT)
#  define junixsocket_have_MSG_DONTWAIT 1
#else
#  define junixsocket_have_MSG_DONTWAIT 0
#endif

#include "org_newsclub_net_unix_NativeUnixSocket.h"

#endif /* config_h */
