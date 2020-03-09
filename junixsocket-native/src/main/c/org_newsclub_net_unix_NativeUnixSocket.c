/**
 * Copyright 2009-2019 Christian Kohlschütter
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

///
/// The native C part of the AFUnixSocket implementation.
///
/// @author Christian Kohlschuetter
///
#define _GNU_SOURCE 1

#if defined(_WIN32)
#  define WIN32_LEAN_AND_MEAN
#  undef WINVER
#  undef _WIN32_WINNT
#  define WINVER 0x0A00
#  define _WIN32_WINNT 0x0A00 // Target Windows 10
#  define _POSIX_SOURCE
#endif

#include "org_newsclub_net_unix_NativeUnixSocket.h"
#include <errno.h>
#include <sys/param.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#define junixsocket_have_sun_len // might be undef'ed below
#define junixsocket_have_ancillary // might be undef'ed below

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

#  if !defined(clock_gettime) // older time.h
int clock_gettime(int ignored, struct timespec *spec) {
    __int64 time;
    GetSystemTimeAsFileTime((FILETIME*)&time);
    time -= 116444736000000000LL; // EPOCHFILETIME
    spec->tv_sec = time / 10000000LL;
    spec->tv_nsec = time % 10000000LL * 100;
    return 0;
}
#    define CLOCK_MONOTONIC 1
#  endif

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

#else // not windows:
#  include <sys/ioctl.h>
#  include <sys/socket.h>
#  include <sys/uio.h>
#  include <sys/un.h>
#  define SOCKET int
#  define INVALID_SOCKET -1
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

#if defined(__MACH__)
#  define junixsocket_use_poll_for_accept
//#define junixsocket_use_poll_interval_millis    1000
#  define junixsocket_use_poll_for_read
#  include <sys/ucred.h>
#endif

#if defined(__FreeBSD__)
#  define junixsocket_use_poll_for_accept
//#define junixsocket_use_poll_interval_millis    1000
#  define junixsocket_use_poll_for_read
#  include <sys/ucred.h>
#  if !defined(SOL_LOCAL)
#    define SOL_LOCAL               0
#  endif
#endif

#if defined(junixsocket_use_poll_for_accept) || defined(junixsocket_use_poll_for_read)
#  if !defined(_WIN32)
#    include <poll.h>
#  endif
#  include <limits.h>
#  include <time.h>
#endif

#if defined(LOCAL_PEEREUUID)
#  include <uuid/uuid.h>
#endif

// Windows requires us fetching errno for socket-related errors
#if defined(_WIN32)
#  define socket_errno (errno = WSAGetLastError())
#else
#  define socket_errno errno
#endif

typedef enum {
    kExceptionSocketException = 0,
    kExceptionSocketTimeoutException,
    kExceptionIndexOutOfBoundsException,
    kExceptionIllegalStateException,
    kExceptionNullPointerException,
    kExceptionMaxExcl
} ExceptionType;

// NOTE: The exceptions must all be either inherit from IOException or RuntimeException/Error
static const char *kExceptionClasses[kExceptionMaxExcl] = {
        "java/net/SocketException", // kExceptionSocketException
        "java/net/SocketTimeoutException", // kExceptionSocketTimeoutException
        "java/lang/IndexOutOfBoundsException", // kExceptionIndexOutOfBoundsException
        "java/lang/IllegalStateException" // kExceptionIllegalStateException
                "java/lang/NullPointerException" // kExceptionNullPointerException
        };

static int _closeFd(JNIEnv * env, jobject fd, int handle);

// see AFUNIXSocketCapability.java in junixsocket-common
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-variable"
static int CAPABILITY_PEER_CREDENTIALS = (1 << 0);
static int CAPABILITY_ANCILLARY_MESSAGES = (1 << 1);
static int CAPABILITY_FILE_DESCRIPTORS = (1 << 2);
static int CAPABILITY_ABSTRACT_NAMESPACE = (1 << 3);
#pragma GCC diagnostic pop

static void org_newsclub_net_unix_NativeUnixSocket_throwException(JNIEnv* env,
        ExceptionType exceptionType, char* message)
{
    if((int)exceptionType < 0 || exceptionType >= kExceptionMaxExcl) {
        exceptionType = kExceptionIllegalStateException;
    }
    const char *exceptionClass = kExceptionClasses[exceptionType];

    jclass exc = (*env)->FindClass(env, exceptionClass);
    jmethodID constr = (*env)->GetMethodID(env, exc, "<init>",
            "(Ljava/lang/String;)V");

    jstring str;
    if(message == NULL) {
        message = "Unknown error";
    }
    str = (*env)->NewStringUTF(env, message);

    jthrowable t = (jthrowable)(*env)->NewObject(env, exc, constr, str);
    (*env)->Throw(env, t);
}

static void org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(
        JNIEnv* env, int errnum, jobject fdToClose)
{
    ExceptionType exceptionType;
    switch(errnum) {
    case EAGAIN:
    case ETIMEDOUT:
        exceptionType = kExceptionSocketTimeoutException;
        break;
    case EPIPE:
    case EBADF:
    case ECONNRESET:
        // broken pipe, etc. -> close socket fd, so Socket#isClosed returns true
        if(fdToClose != NULL) {
            _closeFd(env, fdToClose, -1);
        }

        // fall-through
    default:
        exceptionType = kExceptionSocketException;
    }

    size_t buflen = 255;
    char *message = calloc(1, buflen + 1);

#ifdef __linux__
    char *otherBuf = strerror_r(errnum, message, buflen);
    if(otherBuf != NULL) {
        strncpy(message, otherBuf, buflen);
    }
#elif defined(_WIN32)
    if(errnum >= 10000) {
        // winsock error
        FormatMessage (FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                NULL, errnum, MAKELANGID (LANG_NEUTRAL, SUBLANG_DEFAULT),
                message, buflen, NULL);
    } else if(errnum == 138) {
        strcpy(message, "Permission to access the network was denied.");
    } else {
        strncpy(message, strerror(errnum), buflen);
    }
#elif !defined(strerror_r)
    strncpy(message, strerror(errnum), buflen);
#else
    strerror_r(errnum, message, buflen);
    if(message[0] == 0) {
        sprintf(message, "error code %i", errnum);
    }
#endif

#if DEBUG
    char *message1 = calloc(1, buflen);
    snprintf(message1, buflen, "%s; errnox=%i", message, errnum);
    free(message);
    message = message1;
#endif

    org_newsclub_net_unix_NativeUnixSocket_throwException(env, exceptionType,
            message);

    free(message);
}

static void org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
        JNIEnv* env, int errnum, jobject fd)
{
    // when setsockopt returns an error with EINVAL, it means the socket was shut down already
    if(errnum == EINVAL) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException, "Socket closed");
        return;
    }

    org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum,
            fd);
}

static void handleFieldNotFound(JNIEnv *env, jobject instance, char *fieldName)
{

    jmethodID classMethodId = (*env)->GetMethodID(env,
            (*env)->GetObjectClass(env, instance), "getClass",
            "()Ljava/lang/Class;");
    jobject classObject = (*env)->CallObjectMethod(env, instance,
            classMethodId);

    jmethodID methodId = (*env)->GetMethodID(env,
            (*env)->GetObjectClass(env, classObject), "getSimpleName",
            "()Ljava/lang/String;");
    jstring className = (jstring)(*env)->CallObjectMethod(env, classObject,
            methodId);
    const char* classNameStr = (*env)->GetStringUTFChars(env, className, NULL);
    if(classNameStr == NULL) {
        return; // OOME
    }

    char *template = "Cannot find '%s' in class %s";
    size_t buflen = strlen(template) + strlen(fieldName) + strlen(classNameStr);
    char *message = calloc(1, buflen);
    snprintf(message, buflen, template, fieldName, classNameStr);
    (*env)->ReleaseStringUTFChars(env, className, classNameStr);

    org_newsclub_net_unix_NativeUnixSocket_throwException(env,
            kExceptionSocketException, message);
    free(message);
}

static void callObjectSetter(JNIEnv *env, jobject instance, char *methodName,
        char *methodSignature, jobject value)
{
    jclass instanceClass = (*env)->GetObjectClass(env, instance);
    if(instanceClass == NULL) {
        return;
    }

    jmethodID methodId = (*env)->GetMethodID(env, instanceClass, methodName,
            methodSignature);
    if(methodId == NULL) {
        handleFieldNotFound(env, instance, methodName);
        return;
    }

    jobject array[] = {value};
    (*env)->CallObjectMethodA(env, instance, methodId, (jvalue*)array);
}

static void setObjectFieldValue(JNIEnv *env, jobject instance, char *fieldName,
        char *fieldType, jobject value)
{
    jclass instanceClass = (*env)->GetObjectClass(env, instance);
    if(instanceClass == NULL) {
        return;
    }
    jfieldID fieldID = (*env)->GetFieldID(env, instanceClass, fieldName,
            fieldType);
    if(fieldID == NULL) {
        handleFieldNotFound(env, instance, fieldName);
        return;
    }
    (*env)->SetObjectField(env, instance, fieldID, value);
}

#if !defined(_WIN32)
static void setLongFieldValue(JNIEnv *env, jobject instance, char *fieldName,
        jlong value)
{
    jclass instanceClass = (*env)->GetObjectClass(env, instance);
    jfieldID fieldID = (*env)->GetFieldID(env, instanceClass, fieldName, "J");
    if(fieldID == NULL) {
        handleFieldNotFound(env, instance, fieldName);
        return;
    }
    (*env)->SetLongField(env, instance, fieldID, value);
}
#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_init(
        JNIEnv *env, jclass clazz)
{
#if defined(_WIN32)
    WSADATA wsaData;
    int ret = WSAStartup(MAKEWORD(2,2), &wsaData);
    if(ret != 0) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, socket_errno, NULL);
        return;
    }
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    destroy
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_destroy(
        JNIEnv *env, jclass clazz)
{
#if defined(_WIN32)
    WSACleanup();
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    capabilities
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_capabilities(
        JNIEnv *env, jclass clazz)
{

    int capabilities = 0;

#if defined(LOCAL_PEERCRED) || defined(LOCAL_PEEREPID) || defined(LOCAL_PEEREUUID) || defined(SO_PEERCRED)
    capabilities |= CAPABILITY_PEER_CREDENTIALS;
#endif

#if defined(junixsocket_have_ancillary)
    capabilities |= CAPABILITY_ANCILLARY_MESSAGES;
    capabilities |= CAPABILITY_FILE_DESCRIPTORS;
#endif

#if defined(_WIN32) || defined(__linux__)
    capabilities |= CAPABILITY_ABSTRACT_NAMESPACE;
#endif

    return capabilities;
}

#if defined(junixsocket_have_ancillary)

#if JUNIXSOCKET_HARDEN_CMSG_NXTHDR
static struct cmsghdr* junixsocket_CMSG_NXTHDR (struct msghdr *mhdr, struct cmsghdr *cmsg)
{
    if ((size_t)cmsg->cmsg_len >= sizeof(struct cmsghdr)) {
        cmsg = (struct cmsghdr*)((unsigned char*) cmsg + CMSG_ALIGN (cmsg->cmsg_len));
        if ((unsigned char*)cmsg < ((unsigned char*) mhdr->msg_control + mhdr->msg_controllen)) {
            return CMSG_NXTHDR(mhdr, cmsg);
        }
    }
    return NULL;
}
#else
#  define junixsocket_CMSG_NXTHDR CMSG_NXTHDR
#endif

#endif

static int org_newsclub_net_unix_NativeUnixSocket_getFD(JNIEnv * env,
        jobject fd)
{
    jclass fileDescriptorClass = (*env)->GetObjectClass(env, fd);
    jfieldID fdField = (*env)->GetFieldID(env, fileDescriptorClass, "fd", "I");
    if(fdField == NULL) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException,
                "Cannot find field \"fd\" in java.io.FileDescriptor. Unsupported JVM?");
        return 0;
    }
    return (*env)->GetIntField(env, fd, fdField);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    getFD
 * Signature: (Ljava/io/FileDescriptor;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_getFD(
        JNIEnv *env, jclass clazz, jobject fd)
{
    return org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
}

static void org_newsclub_net_unix_NativeUnixSocket_initFD(JNIEnv * env,
        jobject fd, int handle)
{
    jclass fileDescriptorClass = (*env)->GetObjectClass(env, fd);
    jfieldID fdField = (*env)->GetFieldID(env, fileDescriptorClass, "fd", "I");
    if(fdField == NULL) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException,
                "Cannot find field \"fd\" in java.io.FileDescriptor. Unsupported JVM?");
        return;
    }
    (*env)->SetIntField(env, fd, fdField, handle);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    initFD
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_initFD(
        JNIEnv * env, jclass clazz, jobject fd, jint handle)
{
    org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, handle);
}

#if defined(junixsocket_use_poll_for_accept) || defined(junixsocket_use_poll_for_read)

static uint64_t timespecToMillis(struct timespec* ts) {
    return (uint64_t)ts->tv_sec * 1000 + (uint64_t)ts->tv_nsec / 1000000;
}

/*
 * Waits until the connection is ready to read/accept.
 *
 * Returns -1 if an exception was thrown, 0 if a timeout occurred, 1 if ready.
 */
static jint pollWithTimeout(JNIEnv * env, jobject fd, int handle, int timeout) {
#if defined(_WIN32)
    DWORD optVal;
#else
    struct timeval optVal;
#endif
    socklen_t optLen = sizeof(optVal);
    int ret = getsockopt(handle, SOL_SOCKET, SO_RCVTIMEO, WIN32_NEEDS_CHARP &optVal, &optLen);

    uint64_t millis = 0;
    if(ret != 0) {
        org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
                env, socket_errno, fd);
        return -1;
    }
#if defined(_WIN32)
    if(optLen >= (socklen_t)sizeof(optVal)) {
        millis = optVal;
    }
#else
    if(optLen >= sizeof(optVal) && (optVal.tv_sec > 0 || optVal.tv_usec > 0)) {
        millis = ((uint64_t)optVal.tv_sec * 1000) + (uint64_t)(optVal.tv_usec / 1000);
    }
#endif

    if(timeout > 0 && millis < (uint64_t)timeout) {
        // Some platforms (Windows) may not support SO_TIMEOUT, so let's override the timeout with our own value
        millis = (uint64_t)timeout;
    }

    if(millis <= 0) {
        return 1;
    }

    if(millis > INT_MAX) {
        millis = INT_MAX;
    }
    struct pollfd pfd;
    pfd.fd = handle;
    pfd.events = (POLLIN);
    pfd.revents = 0;

    int millisRemaining = (int)millis;

    struct pollfd fds[] = {pfd};

    struct timespec timeStart;
    struct timespec timeEnd;

    if(clock_gettime(CLOCK_MONOTONIC, &timeEnd) == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, NULL);
        return -1;
    }

    while (millisRemaining > 0) {
        // FIXME: should this be in a loop to ensure the timeout condition is met?

        timeStart = timeEnd;

        int pollTime = millisRemaining;
#  if defined(junixsocket_use_poll_interval_millis)
        // Since poll doesn't abort upon closing the socket,
        // let's simply poll on a frequent basis
        if(pollTime > junixsocket_use_poll_interval_millis) {
            pollTime = junixsocket_use_poll_interval_millis;
        }
#  endif

#  if defined(_WIN32)
        ret = WSAPoll(fds, 1, pollTime);
#  else
        ret = poll(fds, 1, pollTime);
#  endif
        if(ret == 1) {
            if((pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) == 0) {
                break;
            } else {
                // timeout
                return 0;
            }
        }
        int errnum = socket_errno;
        if(clock_gettime(CLOCK_MONOTONIC, &timeEnd) == -1) {
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum, NULL);
            return -1;
        }
        int elapsed = (int)(timespecToMillis(&timeEnd) - timespecToMillis(&timeStart));
        if(elapsed <= 0) {
            elapsed = 1;
        }
        millisRemaining -= elapsed;
        if(millisRemaining <= 0) {
            // timeout
            return 0;
        }

        if(ret == -1) {
            if(errnum == EAGAIN) {
                // try again
                continue;
            }

            if(errnum == ETIMEDOUT) {
                return 0;
            } else {
                org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum, fd);
                return -1;
            }
        }
    }

    return 1;
}


#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    accept
 * Signature: ([BLjava/io/FileDescriptor;Ljava/io/FileDescriptor;JI)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_accept(
        JNIEnv * env, jclass clazz, jbyteArray addr, jobject fdServer,
        jobject fd, jlong expectedInode, int timeout)
{
    struct sockaddr_un su;
    const int maxLen = sizeof(su.sun_path);

    socklen_t addrLen = (socklen_t)(*env)->GetArrayLength(env, addr);
    if((int)addrLen <= 0 || addrLen >= maxLen) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException,
                "Socket address length out of range");
        return;
    }

    const char* socketFile = (char*)(void*)(*env)->GetByteArrayElements(env,
            addr, NULL);
    if(socketFile == NULL) {
        return; // OOME
    }

    int serverHandle = org_newsclub_net_unix_NativeUnixSocket_getFD(env,
            fdServer);

    su.sun_family = AF_UNIX;
    memset(su.sun_path, 0, maxLen);
    memcpy(su.sun_path, socketFile, addrLen);
    (*env)->ReleaseByteArrayElements(env, addr, (jbyte*)(void*)socketFile, 0);
    socketFile = NULL;

#ifdef junixsocket_have_sun_len
    su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + addrLen);
#endif

    socklen_t suLength = (socklen_t)(addrLen + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
            + (unsigned char)sizeof(su.sun_len)
#endif
            );

    if(expectedInode > 0) {
        struct stat fdStat;

        // It's OK when the file's gone, but not OK if it refers to another inode.
        int statRes = stat(su.sun_path, &fdStat);
        if(statRes == 0) {
            ino_t statInode = fdStat.st_ino;

            if(statInode != (ino_t)expectedInode) {
                // inode mismatch -> someone else took over this socket address
                _closeFd(env, fdServer, serverHandle);
                org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
                ECONNABORTED, NULL);
                return;
            }
        }
    }

#if defined(junixsocket_use_poll_for_accept)
    {
        int ret = pollWithTimeout(env, fdServer, serverHandle, timeout);
        if(ret == 0) {
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, ETIMEDOUT, fdServer);
            return;
        } else if(ret < 0) {
            return;
        }
    }
#endif

    int socketHandle;
    int errnum;
    do {
        socketHandle = accept(serverHandle, (struct sockaddr *)&su, &suLength);
    } while(socketHandle == -1 && (errnum = socket_errno) == EINTR);
    if(socketHandle < 0) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum,
                fdServer);
        return;
    }

    org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, socketHandle);
    return;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    bind
 * Signature: ([BLjava/io/FileDescriptor;I)J
 */
JNIEXPORT jlong JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_bind(
        JNIEnv * env, jclass clazz, jbyteArray addr, jobject fd, jint options)
{
    struct sockaddr_un su;
    const int maxLen = sizeof(su.sun_path);

    socklen_t addrLen = (socklen_t)(*env)->GetArrayLength(env, addr);
    if((int)addrLen <= 0 || addrLen >= maxLen) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException,
                "Socket address length out of range");
        return -1;
    }

    const char* socketFile = (char*)(void*)(*env)->GetByteArrayElements(env,
            addr, NULL);

    if(socketFile == NULL) {
        return -1; // OOME
    }

    su.sun_family = AF_UNIX;
    memset(su.sun_path, 0, maxLen);
    memcpy(su.sun_path, socketFile, addrLen);

    (*env)->ReleaseByteArrayElements(env, addr, (jbyte*)(void*)socketFile, 0);
    socketFile = NULL;

#ifdef junixsocket_have_sun_len
    su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + addrLen);
#endif

    socklen_t suLength = (socklen_t)(addrLen + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
            + sizeof(su.sun_len)
#endif
            );

#if defined(_WIN32)
    int serverHandle = socket(PF_UNIX, SOCK_STREAM, 0);
    if(serverHandle == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
                socket_errno, fd);
        return -1;
    }

    if(su.sun_path[0] != 0) {
        DeleteFileA(su.sun_path);
    }

    int bindRes;
    bindRes = bind(serverHandle, (struct sockaddr *)&su, suLength);
    int myErr = socket_errno;
    org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, serverHandle);

    if(bindRes < 0) {
        _closeFd(env, fd, serverHandle);
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr,
                NULL);
        return -1;
    } else {
        return 0;
    }
#else
    bool reuse = (options == -1);
    bool useSuTmp = false;
    struct sockaddr_un suTmp;
    if(reuse) {
        suTmp.sun_family = AF_UNIX;
        suTmp.sun_path[0] = 0;
#ifdef junixsocket_have_sun_len
        suTmp.sun_len = (unsigned char)(sizeof(suTmp) - sizeof(suTmp.sun_path)
                + strlen(suTmp.sun_path));
#endif
    }

    int myErr;

    int serverHandle = 0;
    for(int attempt = 0; attempt < 2; attempt++) {
        myErr = 0;

        if(serverHandle != 0) {
#if defined(_WIN32)
            closesocket(serverHandle);
#else
            close(serverHandle);
#endif
        }
        serverHandle = socket(PF_UNIX, SOCK_STREAM, 0);
        if(serverHandle == -1) {
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
            socket_errno, fd);
            return -1;
        }

        int ret;
        int optVal = 1;

        if(reuse) {
            // reuse address

            // This block is only prophylactic, as SO_REUSEADDR seems not to affect AF_UNIX sockets
            ret = setsockopt(serverHandle, SOL_SOCKET, SO_REUSEADDR, &optVal,
                    sizeof(optVal));
            if(ret == -1) {
                org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
                        env, socket_errno, fd);
                return -1;
            }
        }

#if defined(SO_NOSIGPIPE)
        // prevent raising SIGPIPE
        ret = setsockopt(serverHandle, SOL_SOCKET, SO_NOSIGPIPE, &optVal, sizeof(optVal));
        if(ret == -1) {
            org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env, socket_errno, fd);
            return -1;
        }
#endif

        int bindRes;
        if(attempt == 0 && !reuse) {
            // if we're not going to reuse the socket, let's try to connect first.
            // This avoids changing file metadata (e.g. ctime!)
            bindRes = -1;
            errno = 0;
        } else {
            bindRes = bind(serverHandle, (struct sockaddr *)&su, suLength);
        }

        myErr = socket_errno;

        if(bindRes == 0) {
            break;
        } else if(attempt == 0 && (!reuse || myErr == EADDRINUSE)) {
            if(reuse) {
#if defined(_WIN32)
                closesocket(serverHandle);
#else
                close(serverHandle);
#endif

                if(su.sun_path[0] == 0) {
                    // nothing to be done in the abstract namespace
                } else {
                    // if we're reusing the socket, it's better to move away the existing
                    // socket, bind ours to the correct address, and then connect to
                    // the temporary file (to unblock the accept), and finally delete
                    // the temporary file
                    strcpy(suTmp.sun_path, "/tmp/junixsocket.XXXXXX");
                    mkstemp(suTmp.sun_path);

                    int renameRet = rename(su.sun_path, suTmp.sun_path);
                    if(renameRet == -1) {
                        if(socket_errno != ENOENT) {
                            // ignore failure
                        }
                    } else {
                        useSuTmp = true;
                    }
                }
            }

            if(useSuTmp) {
                // we've moved the existing socket, let's first bind and then let the old server know!
                _closeFd(env, fd, serverHandle);
                continue;
            }

            int errnum;

            // if the given file exists, but is not a socket, ENOTSOCK is returned
            // if access is denied, EACCESS is returned
            do {
                ret = connect(serverHandle, (struct sockaddr *)&su, suLength);
            } while(ret == -1 && (errnum = socket_errno) == EINTR);

            if(ret == 0) {
                // if we can successfully connect, the address is in use
                errnum = EADDRINUSE;
                if(!reuse) {
                    myErr = EADDRINUSE;
                }
            } else if(errnum == ENOENT) {
                continue;
            }

            if(ret == 0
                    || (ret == -1
                            && (errnum == ECONNREFUSED || errnum == EADDRINUSE))) {
                // assume existing socket file
                _closeFd(env, fd, serverHandle);

                if(reuse || errnum == ECONNREFUSED) {
                    // either reuse existing socket, or take over a no longer working socket
                    if(su.sun_path[0] == 0) {
                        // no need to unlink in the abstract namespace
                        continue;
                    } else if(unlink(su.sun_path) == -1) {
                        if(errno == ENOENT) {
                            continue;
                        }

                        myErr = errno;
                    } else {
                        continue;
                    }
                }
            }
        }

        _closeFd(env, fd, serverHandle);
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr,
        NULL);
        return -1;
    }

    if(su.sun_path[0] == 0) {
        // nothing to be done for the abstract namespace
    } else {
#if !defined(_WIN32)
        int chmodRes = chmod(su.sun_path, 0666);
        if(chmodRes == -1) {
            myErr = errno;
            _closeFd(env, fd, serverHandle);
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
                    myErr,
                    NULL);
            return -1;
        }
#endif
    }

    org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, serverHandle);

    struct stat fdStat;
    ino_t inode;

    if(su.sun_path[0] == 0) {
        // no inodes in the abstract namespace
        inode = 0;
    } else {
#if !defined(_WIN32)
        int statRes = stat(su.sun_path, &fdStat);
        if(statRes == -1) {
            myErr = errno;
            _closeFd(env, fd, serverHandle);
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
                    myErr,
                    NULL);
            return -1;
        }
        inode = fdStat.st_ino;
#else
        inode = 0;
#endif
    }

    if(useSuTmp) {
        // now that we've bound our socket, let the previously listening server know.
        // We've moved their socket to a safe place, which we'll have to unlink, too!

        socklen_t suTmpLength = (socklen_t)(
                strlen(suTmp.sun_path) + sizeof(suTmp.sun_family)
#ifdef junixsocket_have_sun_len
                        + (unsigned char)sizeof(suTmp.sun_len)
#endif
                        );

        int tmpHandle = socket(PF_UNIX, SOCK_STREAM, 0);
        if(tmpHandle != -1) {
            int ret;
            do {
                ret = connect(tmpHandle, (struct sockaddr *)&suTmp,
                        suTmpLength);
            } while(ret == -1 && socket_errno == EINTR);

            ret = shutdown(tmpHandle, SHUT_RDWR);
#if defined(_WIN32)
            ret = closesocket(tmpHandle);
#else
            ret = close(tmpHandle);
#endif
        }

        if(suTmp.sun_path[0] == 0) {
            // no need to unlink in the abstract namespace
        } else if(unlink(suTmp.sun_path) == -1) {
            if(errno != ENOENT) {
                org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
                errno, NULL);
                return -1;
            }
        }
    }

    return (jlong)inode;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    listen
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_listen(
        JNIEnv * env, jclass clazz, jobject fd, jint backlog)
{
    int serverHandle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

    int listenRes = listen(serverHandle, backlog);
    if(listenRes == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
        socket_errno, fd);
        return;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    connect
 * Signature: ([BLjava/io/FileDescriptor;J)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_connect(
        JNIEnv * env, jclass clazz, jbyteArray addr, jobject fd,
        jlong expectedInode)
{
    struct sockaddr_un su;
    const int maxLen = sizeof(su.sun_path);

    socklen_t addrLen = (socklen_t)(*env)->GetArrayLength(env, addr);
    if((int)addrLen <= 0 || addrLen >= maxLen) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException,
                "Socket address length out of range");
        return;
    }

    const char* socketFile = (char*)(void*)(*env)->GetByteArrayElements(env,
            addr, NULL);
    if(socketFile == NULL) {
        return; // OOME
    }

    SOCKET socketHandle = socket(PF_UNIX, SOCK_STREAM, 0);
    if(socketHandle == INVALID_SOCKET) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
        socket_errno, fd);
        return;
    }

    su.sun_family = AF_UNIX;
    memset(su.sun_path, 0, maxLen);
    memcpy(su.sun_path, socketFile, addrLen);

    (*env)->ReleaseByteArrayElements(env, addr, (jbyte*)(void*)socketFile, 0);
    socketFile = NULL;

#ifdef junixsocket_have_sun_len
    su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + addrLen);
#endif

    socklen_t suLength = (socklen_t)(addrLen + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
            + sizeof(su.sun_len)
#endif
            );

    if(expectedInode > 0) {
        struct stat fdStat;

        // It's OK when the file's gone, but not OK if it refers to another inode.
        int statRes = stat(su.sun_path, &fdStat);
        if(statRes == 0) {
            ino_t statInode = fdStat.st_ino;

            if(statInode != (ino_t)expectedInode) {
                // inode mismatch -> someone else took over this socket address
                _closeFd(env, fd, socketHandle);
                org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
                ECONNABORTED, NULL);
                return;
            }
        }
    }

    int myErr = errno;

    int ret;
    do {
        ret = connect(socketHandle, (struct sockaddr *)&su, suLength);
    } while(ret == -1 && (myErr = socket_errno) == EINTR);

    if(ret == -1) {
        _closeFd(env, fd, socketHandle);
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr,
        NULL);
        return;
    }

    org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, socketHandle);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    read
 * Signature: (Lorg/newsclub/net/unix/AFUNIXSocketImpl;Ljava/io/FileDescriptor;[BIILjava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_read(
        JNIEnv * env, jclass clazz, jobject impl, jobject fd, jbyteArray jbuf,
        jint offset, jint length, jobject ancBuf)
{
    jsize bufLen = (*env)->GetArrayLength(env, jbuf);
    if(offset < 0 || length < 0 || offset >= bufLen) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException, "Illegal offset or length");
        return -1;
    }
    jbyte *buf = (*env)->GetByteArrayElements(env, jbuf, NULL);
    if(buf == NULL) {
        return -1; // OOME
    }

    jint maxRead = bufLen - offset;
    if(length > maxRead) {
        length = maxRead;
    }

    int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

#if defined(junixsocket_use_poll_for_read)
    int ret = pollWithTimeout(env, fd, handle, 0);
    if(ret < 1) {
        return -1;
    }
#endif

    ssize_t count;

#if defined(junixsocket_have_ancillary)
    socklen_t controlLen = (socklen_t)(*env)->GetDirectBufferCapacity(env, ancBuf);

    if((jsize)controlLen <= 0) {
        do {
            count = recv(handle, &(((char*)buf)[offset]), (size_t)length, 0);
        } while(count == -1 && socket_errno == EINTR);
    } else {
        jbyte *control = (*env)->GetDirectBufferAddress(env, ancBuf);

        struct iovec iov = {.iov_base = &(buf[offset]), .iov_len = (size_t)length};
        struct sockaddr_un sender;
        struct msghdr msg = {.msg_name = (struct sockaddr*)&sender, .msg_namelen =
                sizeof(sender), .msg_iov = &iov, .msg_iovlen = 1, .msg_control =
                control, .msg_controllen = controlLen, };

        do {
            count = recvmsg(handle, &msg, 0);
        } while(count == (ssize_t)-1 && socket_errno == EINTR);

        if((msg.msg_flags & MSG_CTRUNC) != 0) {
            if(count >= 0) {
                count = -1;
                errno = ENOBUFS;
            }
            goto readEnd;
        }

        if(msg.msg_controllen > 0) {
            for(struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg); cmsg != NULL; cmsg =
                    junixsocket_CMSG_NXTHDR(&msg, cmsg)) {
                if(cmsg->cmsg_level == SOL_SOCKET
                        && cmsg->cmsg_type == SCM_RIGHTS) {
                    char *endBytes = (char*)cmsg + cmsg->cmsg_len;
                    char *controlEnd = (char*)control + controlLen;
                    if(controlEnd < endBytes) {
                        endBytes = controlEnd;
                    }

                    int *data = (int*)CMSG_DATA(cmsg);
                    int *end = (int*)endBytes;
                    int numFds = (int)(end - data);

                    if(numFds > 0) {
                        jintArray fdArray = (*env)->NewIntArray(env, numFds);
                        jint *fdBuf = (*env)->GetIntArrayElements(env, fdArray,
                        NULL);

                        for(int i = 0; i < numFds; i++) {
                            fdBuf[i] = data[i];
                        }

                        (*env)->ReleaseIntArrayElements(env, fdArray, fdBuf, 0);

                        callObjectSetter(env, impl, "receiveFileDescriptors",
                                "([I)V", fdArray);
                    }
                } else {
    #if DEBUG
                    fprintf(stderr, "NativeUnixSocket_read: Unexpected cmsg level:%i type:%i\n", cmsg->cmsg_level, cmsg->cmsg_type);
                    fflush(stderr);
    #endif
                }
            }
        }
    }

#else
    do {
        count = recv(handle, &(((char*)buf)[offset]), (size_t)length, 0);
    } while(count == -1 && socket_errno == EINTR);
#endif

#if !defined(_WIN32)
    readEnd:
#endif
    (*env)->ReleaseByteArrayElements(env, jbuf, buf, 0);

    if(count == 0) {
        // read(2) returns 0 on EOF. Java returns -1.
        return -1;
    } else if(count == -1) {
        // read(2) returns -1 on error. Java throws an Exception.

// Removed since non-blocking is not yet supported
//        if(errno == EAGAIN || errno == EWOULDBLOCK) {
//            return 0;
//        }
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno,
                fd);
        return -1;
    }

    return (jint)count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    write
 * Signature: (Lorg/newsclub/net/unix/AFUNIXSocketImpl;Ljava/io/FileDescriptor;[BII[I)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_write(
        JNIEnv * env, jclass clazz, jobject impl, jobject fd, jbyteArray jbuf,
        jint offset, jint length, jintArray ancFds)
{
    jbyte *buf = (*env)->GetByteArrayElements(env, jbuf, NULL);
    if(buf == NULL) {
        return -1; // OOME
    }
    jsize bufLen = (*env)->GetArrayLength(env, jbuf);
    if(offset < 0 || length < 0 || (length > (bufLen - offset))) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionIndexOutOfBoundsException,
                "Illegal offset or length");
        return -1;
    }

    int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

#if defined(junixsocket_have_ancillary)
    struct iovec iov = {.iov_base = &buf[offset], .iov_len = (size_t)length};
    struct msghdr msg = {.msg_name = NULL, .msg_namelen = 0, .msg_iov = &iov,
            .msg_iovlen = 1, };

    char *control = NULL;
    if(ancFds != NULL) {
        jsize ancFdsLen = (*env)->GetArrayLength(env, ancFds);
        msg.msg_controllen = (socklen_t)CMSG_SPACE((socklen_t)ancFdsLen * sizeof(jint));
        control = msg.msg_control = malloc(msg.msg_controllen);

        socklen_t controlLen = 0;
        struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        controlLen += (cmsg->cmsg_len = (socklen_t)CMSG_LEN((socklen_t)ancFdsLen * sizeof(jint)));
        int *data = (int*)CMSG_DATA(cmsg);

        jint *ancBuf = (*env)->GetIntArrayElements(env, ancFds, NULL);
        for(int i = 0; i < ancFdsLen; i++) {
            data[i] = ancBuf[i];
        }
        cmsg = junixsocket_CMSG_NXTHDR(&msg, cmsg);
        if(cmsg == NULL) {
            // FIXME: not enough space in header?
        }

        msg.msg_controllen = controlLen;

        (*env)->ReleaseIntArrayElements(env, ancFds, ancBuf, 0);

        callObjectSetter(env, impl, "setOutboundFileDescriptors", "([I)V",
        NULL);
    }

    errno = 0;
    ssize_t count;
    do {
        count = sendmsg(handle, &msg, 0);
    } while(count == -1 && errno == EINTR);
    int myErr = socket_errno;

    if(control) {
        free(control);
    }

#else
    errno = 0;
    ssize_t count;
    do {
        count = send(handle, &((char*)buf)[offset], (size_t)length, 0);
    } while(count == -1 && socket_errno == EINTR);

    int myErr = errno;
#endif

    (*env)->ReleaseByteArrayElements(env, jbuf, buf, 0);

    if(count == -1) {
        if(errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0;
        }

        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr,
                fd);
        return -1;
    }

    return (jint)count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    close
 * Signature: (Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_close(
        JNIEnv * env, jclass clazz, jobject fd)
{
    if(fd == NULL) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionNullPointerException, "fd");
        return;
    }
    (*env)->MonitorEnter(env, fd);
    int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
    org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, -1);
    (*env)->MonitorExit(env, fd);

    int ret = _closeFd(env, fd, handle);
    if(ret == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno,
        NULL);
        return;
    }
}

// Close a file descriptor. fd object and numeric handle must either be identical,
// or only one of them be valid.
//
// fd objects are marked closed by setting their fd value to -1.
static int _closeFd(JNIEnv * env, jobject fd, int handle)
{
    int ret = 0;
    if(handle > 0) {
        shutdown(handle, SHUT_RDWR);
#if defined(_WIN32)
        ret = closesocket(handle);
#else
        ret = close(handle);
#endif
    }
    if(fd == NULL) {
        return ret;
    }
    (*env)->MonitorEnter(env, fd);
    int fdHandle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
    org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, -1);
    (*env)->MonitorExit(env, fd);

    if(handle > 0) {
        if(fdHandle > 0 && handle != fdHandle) {
#if DEBUG
            fprintf(stderr, "NativeUnixSocket_closeFd inconsistency: handle %i vs fdHandle %i\n", handle, fdHandle);
            fflush(stderr);
#endif
        }
    } else if(fdHandle > 0) {
        shutdown(fdHandle, SHUT_RDWR);
#if defined(_WIN32)
        ret = closesocket(fdHandle);
#else
        ret = close(fdHandle);
#endif
    }

    return ret;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    shutdown
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_shutdown(
        JNIEnv * env, jclass clazz, jobject fd, jint mode)
{
    int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
    int ret = shutdown(handle, mode);
    if(ret == -1) {
        int errnum = socket_errno;
        if(errnum == ENOTCONN) {
            // ignore
            return;
        }
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum,
                fd);
        return;
    }
}

jint convertSocketOptionToNative(jint optID)
{
    switch(optID) {
    case 0x0008:
        return SO_KEEPALIVE;
    case 0x0080:
        return SO_LINGER;
    case 0x1005:
        return SO_SNDTIMEO;
    case 0x1006:
        return SO_RCVTIMEO;
    case 0x1002:
        return SO_RCVBUF;
    case 0x1001:
        return SO_SNDBUF;
    default:
        return -1;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    getSocketOptionInt
 * Signature: (Ljava/io/FileDescriptor;I)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_getSocketOptionInt(
        JNIEnv * env, jclass clazz, jobject fd, jint optID)
{
    int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

    optID = convertSocketOptionToNative(optID);
    if(optID == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException, "Unsupported socket option");
        return -1;
    }
#if !defined(_WIN32)
    if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
        struct timeval optVal;
        socklen_t optLen = sizeof(optVal);
        int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
        if(ret == -1) {
            org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
                    env, socket_errno, fd);
            return -1;
        }
        return (jint)(optVal.tv_sec * 1000 + optVal.tv_usec / 1000);
    } else
#endif
    if(optID == SO_LINGER) {
        struct linger optVal;
        socklen_t optLen = sizeof(optVal);

        int ret = getsockopt(handle, SOL_SOCKET, optID,
                WIN32_NEEDS_CHARP &optVal, &optLen);
        if(ret == -1) {
            org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
                    env, socket_errno, fd);
            return -1;
        }
        if(optVal.l_onoff == 0) {
            return -1;
        } else {
            return optVal.l_linger;
        }
    }

    int optVal;
    socklen_t optLen = sizeof(optVal);

    int ret = getsockopt(handle, SOL_SOCKET, optID, WIN32_NEEDS_CHARP &optVal,
            &optLen);
    if(ret == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env,
        socket_errno, fd);
        return -1;
    }

    return optVal;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setSocketOptionInt
 * Signature: (Ljava/io/FileDescriptor;II)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setSocketOptionInt(
        JNIEnv * env, jclass clazz, jobject fd, jint optID, jint value)
{
    int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

    optID = convertSocketOptionToNative(optID);
    if(optID == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException, "Unsupported socket option");
        return;
    }

#if !defined(_WIN32)
    if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
        // NOTE: SO_RCVTIMEO == SocketOptions.SO_TIMEOUT = 0x1006
        struct timeval optVal;
        optVal.tv_sec = value / 1000;
        optVal.tv_usec = (value % 1000) * 1000;
        int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal,
                sizeof(optVal));

        if(ret == -1) {
            org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
                    env, socket_errno, fd);
            return;
        }
        return;
    } else
#endif
    if(optID == SO_LINGER) {
        struct linger optVal;

        optVal.l_onoff = value >= 0;
        optVal.l_linger = value >= 0 ? value : 0;

        int ret = setsockopt(handle, SOL_SOCKET, optID,
                WIN32_NEEDS_CHARP &optVal, sizeof(optVal));
        if(ret == -1) {
            org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
                    env, socket_errno, fd);
            return;
        }
        return;
    }

    int optVal = (int)value;

    int ret = setsockopt(handle, SOL_SOCKET, optID, WIN32_NEEDS_CHARP &optVal,
            sizeof(optVal));
    if(ret == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env,
        socket_errno, fd);
        return;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    available
 * Signature: (Ljava/io/FileDescriptor;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_available(
        JNIEnv * env, jclass clazz, jobject fd)
{
    int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

    // the following would actually block and keep the peek'ed byte in the buffer
    //ssize_t count = recv(handle, &buf, 1, MSG_PEEK);

    int ret;
#if defined(_WIN32)
    u_long count;
    ret = ioctlsocket(handle, FIONREAD, &count);
#else
    int count;
    ret = ioctl(handle, FIONREAD, &count);
#endif
    if((int)count == -1 || ret == -1) {
        org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env,
        socket_errno, fd);
        return -1;
    }

    return count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    peerCredentials
 * Signature: (Ljava/io/FileDescriptor;Lorg/newsclub/net/unix/AFUNIXSocketCredentials;)Lorg/newsclub/net/unix/AFUNIXSocketCredentials;
 */
JNIEXPORT jobject JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_peerCredentials(
        JNIEnv *env, jclass clazz, jobject fdesc, jobject creds)
{
#if defined(LOCAL_PEERCRED) || defined(LOCAL_PEEREPID) || defined(LOCAL_PEEREUUID) ||defined(SO_PEERCRED)
    int fd = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fdesc);

#  if defined(LOCAL_PEERCRED)
    {
        struct xucred cr;
        socklen_t len = sizeof(cr);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEERCRED, &cr, &len) < 0) {
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, socket_errno, NULL);
            return NULL;
        } else {
            jlongArray gidArray = (*env)->NewLongArray(env, cr.cr_ngroups);
            jlong *gids = (*env)->GetLongArrayElements(env, gidArray, 0);
            for (int i=0,n=cr.cr_ngroups;i<n;i++) {
                gids[i] = (jlong)cr.cr_groups[i];
            }
            (*env)->ReleaseLongArrayElements(env, gidArray, gids, 0);

            setLongFieldValue(env, creds, "uid", cr.cr_uid);
            setObjectFieldValue(env, creds, "gids", "[J", gidArray);
        }
    }
#  endif
#  if defined(LOCAL_PEEREPID)
    {
        pid_t pid = (pid_t) -1;
        socklen_t len = sizeof(pid);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEEREPID, &pid, &len) < 0) {
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, socket_errno, NULL);
            return NULL;
        }
        setLongFieldValue(env, creds, "pid", (jlong)pid);
    }
#  endif
#  if defined(LOCAL_PEEREUUID)
    {
        uuid_t uuid;
        socklen_t len = sizeof(uuid);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEEREUUID, &uuid, &len) < 0) {
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, socket_errno, NULL);
            return NULL;
        } else {
            uuid_string_t uuidStr;
            uuid_unparse(uuid, uuidStr);

            jobject uuidString = (*env)->NewStringUTF(env, uuidStr);
            callObjectSetter(env, creds, "setUUID", "(Ljava/lang/String;)V", uuidString);
        }
    }
#  endif
#  if defined(SO_PEERCRED)
    {
        struct ucred cr;
        socklen_t len = sizeof(cr);
        if(getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cr, &len) < 0) {
            org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, socket_errno, NULL);
            return NULL;
        } else {
            jlongArray gidArray = (*env)->NewLongArray(env, 1);
            jlong *gids = (*env)->GetLongArrayElements(env, gidArray, 0);
            gids[0] = cr.gid;
            (*env)->ReleaseLongArrayElements(env, gidArray, gids, 0);

            setLongFieldValue(env, creds, "uid", cr.uid);
            setLongFieldValue(env, creds, "pid", cr.pid);
            setObjectFieldValue(env, creds, "gids", "[J", gidArray);
        }
    }
#  endif

#endif
    return creds;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    initServerImpl
 * Signature: (Lcom/newsclub/net/unix/AFUNIXServerSocket;Lcom/newsclub/net/unix/AFUNIXSocketImpl;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_initServerImpl(
        JNIEnv * env, jclass clazz, jobject serverSocket, jobject impl)
{
    setObjectFieldValue(env, serverSocket, "impl", "Ljava/net/SocketImpl;",
            impl);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setCreated
 * Signature: (Lcom/newsclub/net/unix/AFUNIXSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setCreated(
        JNIEnv * env, jclass clazz, jobject socket)
{
    jclass socketClass = (*env)->GetObjectClass(env, socket);

    jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setCreated",
            "()V");
    if(methodID != NULL) {
        (*env)->CallVoidMethod(env, socket, methodID);
        return;
    }
    (*env)->ExceptionClear(env);

    jfieldID fieldID = (*env)->GetFieldID(env, socketClass, "created", "Z");
    if(fieldID != NULL) {
        (*env)->SetBooleanField(env, socket, fieldID, JNI_TRUE);
        return;
    }
    (*env)->ExceptionClear(env);

    org_newsclub_net_unix_NativeUnixSocket_throwException(env,
            kExceptionSocketException,
            "Cannot find method \"setCreated\" or field \"created\" in java.net.Socket. Unsupported JVM?");
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setConnected
 * Signature: (Lcom/newsclub/net/unix/AFUNIXSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setConnected(
        JNIEnv * env, jclass clazz, jobject socket)
{
    jclass socketClass = (*env)->GetObjectClass(env, socket);

    jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setConnected",
            "()V");
    if(methodID != NULL) {
        (*env)->CallVoidMethod(env, socket, methodID);
        return;
    }
    (*env)->ExceptionClear(env);

    jfieldID fieldID = (*env)->GetFieldID(env, socketClass, "connected", "Z");
    if(fieldID != NULL) {
        (*env)->SetBooleanField(env, socket, fieldID, JNI_TRUE);
        return;
    }
    (*env)->ExceptionClear(env);

    org_newsclub_net_unix_NativeUnixSocket_throwException(env,
            kExceptionSocketException,
            "Cannot find method \"setConnected\" or field \"connected\" in java.net.Socket. Unsupported JVM?");
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setBound
 * Signature: (Lcom/newsclub/net/unix/AFUNIXSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setBound(
        JNIEnv * env, jclass clazz, jobject socket)
{
    jclass socketClass = (*env)->GetObjectClass(env, socket);

    jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setBound",
            "()V");
    if(methodID != NULL) {
        (*env)->CallVoidMethod(env, socket, methodID);
        return;
    }
    (*env)->ExceptionClear(env);

    jfieldID fieldID = (*env)->GetFieldID(env, socketClass, "bound", "Z");
    if(fieldID != NULL) {
        (*env)->SetBooleanField(env, socket, fieldID, JNI_TRUE);
        return;
    }
    (*env)->ExceptionClear(env);

    org_newsclub_net_unix_NativeUnixSocket_throwException(env,
            kExceptionSocketException,
            "Cannot find method \"setBound\" or field \"bound\" in java.net.Socket. Unsupported JVM?");
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setCreatedServer
 * Signature: (Lorg/newsclub/net/unix/AFUNIXServerSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setCreatedServer(
        JNIEnv * env, jclass clazz, jobject socket)
{
    jclass socketClass = (*env)->GetObjectClass(env, socket);

    jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setCreated",
            "()V");
    if(methodID != NULL) {
        (*env)->CallVoidMethod(env, socket, methodID);
        return;
    }
    (*env)->ExceptionClear(env);

    jfieldID fieldID = (*env)->GetFieldID(env, socketClass, "created", "Z");
    if(fieldID != NULL) {
        (*env)->SetBooleanField(env, socket, fieldID, JNI_TRUE);
        return;
    }
    (*env)->ExceptionClear(env);

    org_newsclub_net_unix_NativeUnixSocket_throwException(env,
            kExceptionSocketException,
            "Cannot find method \"setCreated\" or field \"created\" in java.net.ServerSocket. Unsupported JVM?");
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setBoundServer
 * Signature: (Lorg/newsclub/net/unix/AFUNIXServerSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setBoundServer(
        JNIEnv * env, jclass clazz, jobject socket)
{
    jclass socketClass = (*env)->GetObjectClass(env, socket);

    jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setBound",
            "()V");
    if(methodID != NULL) {
        (*env)->CallVoidMethod(env, socket, methodID);
        return;
    }
    (*env)->ExceptionClear(env);

    jfieldID fieldID = (*env)->GetFieldID(env, socketClass, "bound", "Z");
    if(fieldID != NULL) {
        (*env)->SetBooleanField(env, socket, fieldID, JNI_TRUE);
        return;
    }
    (*env)->ExceptionClear(env);

    org_newsclub_net_unix_NativeUnixSocket_throwException(env,
            kExceptionSocketException,
            "Cannot find method \"setBound\" or field \"bound\" in java.net.ServerSocket. Unsupported JVM?");
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setPort
 * Signature: (Lorg/newsclub/net/unix/AFUNIXSocketAddress;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setPort(
        JNIEnv * env, jclass clazz, jobject addr, jint port)
{
    jclass fileDescriptorClass = (*env)->GetObjectClass(env, addr);

    jobject fieldObject = addr;

    jfieldID portField;
    jfieldID holderField = (*env)->GetFieldID(env, fileDescriptorClass,
            "holder", "Ljava/net/InetSocketAddress$InetSocketAddressHolder;");
    if(holderField != NULL) {
        fieldObject = (*env)->GetObjectField(env, addr, holderField);
        jclass holderClass = (*env)->GetObjectClass(env, fieldObject);
        portField = (*env)->GetFieldID(env, holderClass, "port", "I");
    } else {
        portField = (*env)->GetFieldID(env, fileDescriptorClass, "port", "I");
    }
    if(portField == NULL) {
        org_newsclub_net_unix_NativeUnixSocket_throwException(env,
                kExceptionSocketException,
                "Cannot find field \"port\" in java.net.InetSocketAddress. Unsupported JVM?");
        return;
    }
    (*env)->SetIntField(env, fieldObject, portField, port);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    attachCloseable
 * Signature: (Ljava/io/FileDescriptor;Ljava/io/Closeable;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_attachCloseable(
        JNIEnv * env, jclass clazz, jobject fdesc, jobject closeable)
{
    callObjectSetter(env, fdesc, "attach", "(Ljava/io/Closeable;)V", closeable);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    maxAddressLength
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_maxAddressLength(
        JNIEnv * env, jclass clazz)
{
    struct sockaddr_un su;
    return sizeof(su.sun_path);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    currentRMISocket
 * Signature: ()Ljava/net/Socket;
 */
JNIEXPORT jobject JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_currentRMISocket
  (JNIEnv *env, jclass clazz)
{
    jclass tcpTransport = (*env)->FindClass(env,
            "sun/rmi/transport/tcp/TCPTransport");
    if(tcpTransport == NULL) {
        return NULL;
    }
    jfieldID threadConnectionHandler = (*env)->GetStaticFieldID(env, tcpTransport,
            "threadConnectionHandler", "Ljava/lang/ThreadLocal;");
    if(threadConnectionHandler == NULL) {
        return NULL;
    }
    jobject tl = (*env)->GetStaticObjectField(env, tcpTransport, threadConnectionHandler);
    if(tl == NULL) {
        return NULL;
    }
    jclass tlClass = (*env)->GetObjectClass(env, tl);
    if(tlClass == NULL) {
        return NULL;
    }
    jmethodID tlGet = (*env)->GetMethodID(env, tlClass,
            "get", "()Ljava/lang/Object;");
    if(tlGet == NULL) {
        return NULL;
    }
    jobject connHandler = (*env)->CallObjectMethod(env, tl,
            tlGet);
    if(connHandler == NULL) {
        return NULL;
    }
    jclass connHandlerClass = (*env)->GetObjectClass(env, connHandler);
    if(connHandlerClass == NULL) {
        return NULL;
    }
    jfieldID socketField = (*env)->GetFieldID(env, connHandlerClass,
            "socket", "Ljava/net/Socket;");
    if(socketField == NULL) {
        return NULL;
    }
    jobject socket = (*env)->GetObjectField(env, connHandler, socketField);

    return socket;
}

#ifdef __cplusplus
}
#endif
