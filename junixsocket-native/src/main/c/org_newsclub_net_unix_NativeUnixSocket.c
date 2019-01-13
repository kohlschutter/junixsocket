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
#include "org_newsclub_net_unix_NativeUnixSocket.h"

#include <errno.h>
#include <sys/param.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <unistd.h>
#include <stdbool.h>

#ifndef FIONREAD
#include <sys/filio.h>
#endif 
#ifdef __cplusplus
extern "C" {
#endif

#define junixsocket_have_sun_len

// Linux
#ifdef __linux__
#undef junixsocket_have_sun_len
#endif

// Solaris
#if defined(__sun)
#undef junixsocket_have_sun_len
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
#define junixsocket_use_poll_for_accept
//#define junixsocket_use_poll_interval_millis	1000
#endif

#if defined(junixsocket_use_poll_for_accept)
#include <poll.h>
#include <limits.h>
#include <time.h>

#if !defined(uint64_t)
typedef unsigned long long uint64_t;
#endif
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

static void org_newsclub_net_unix_NativeUnixSocket_throwException(JNIEnv* env,
		ExceptionType exceptionType, char* message, jstring file)
{
	if(exceptionType < 0 || exceptionType >= kExceptionMaxExcl) {
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
		JNIEnv* env, int errnum, jobject fdToClose, jstring file)
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
		if (fdToClose != NULL) {
			_closeFd(env, fdToClose, -1);
		}

		// fall-through
	default:
		exceptionType = kExceptionSocketException;
	}

	size_t buflen = 256;
	char *message = calloc(1, buflen);
	strerror_r(errnum, message, buflen);

#if DEBUG
	char *message1 = calloc(1, buflen);
	snprintf(message1, buflen, "%s; errno=%i", message, errnum);
	free(message);
	message = message1;
#endif

	org_newsclub_net_unix_NativeUnixSocket_throwException(env, exceptionType,
			message, file);

	free(message);
}

static void org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(
		JNIEnv* env, int errnum, jobject fd, jstring file)
{
	// when setsockopt returns an error with EINVAL, it means the socket was shut down already
	if (errnum == EINVAL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
					"Socket closed", file);
		return;
	}

	org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum, fd, file);
}

int org_newsclub_net_unix_NativeUnixSocket_getFD(JNIEnv * env, jobject fd)
{
	jclass fileDescriptorClass = (*env)->GetObjectClass(env, fd);
	jfieldID fdField = (*env)->GetFieldID(env, fileDescriptorClass, "fd", "I");
	if(fdField == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				kExceptionSocketException,
				"Cannot find field \"fd\" in java.io.FileDescriptor. Unsupported JVM?",
				NULL);
		return 0;
	}
	return (*env)->GetIntField(env, fd, fdField);
}

void org_newsclub_net_unix_NativeUnixSocket_initFD(JNIEnv * env, jobject fd,
		int handle)
{
	jclass fileDescriptorClass = (*env)->GetObjectClass(env, fd);
	jfieldID fdField = (*env)->GetFieldID(env, fileDescriptorClass, "fd", "I");
	if(fdField == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				kExceptionSocketException,
				"Cannot find field \"fd\" in java.io.FileDescriptor. Unsupported JVM?",
				NULL);
		return;
	}
	(*env)->SetIntField(env, fd, fdField, handle);
}

#if defined(junixsocket_use_poll_for_accept)

static uint64_t timespecToMillis(struct timespec* ts) {
	return (uint64_t)ts->tv_sec * 1000 + (uint64_t)ts->tv_nsec / 1000000;
}

#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    accept
 * Signature: (Ljava/lang/String;Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;L)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_accept
(JNIEnv * env, jclass clazz, jstring file, jobject fdServer, jobject fd, jlong expectedInode) {

	const char* socketFile = (*env)->GetStringUTFChars(env, file, NULL);
	if(socketFile == NULL) {
		return; // OOME
	}
	if(strlen(socketFile) >= 104) {
		(*env)->ReleaseStringUTFChars(env, file, socketFile);
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Pathname too long for socket", file);
		return;
	}

	int serverHandle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fdServer);

	struct sockaddr_un su;
	su.sun_family = AF_UNIX;
#ifdef junixsocket_have_sun_len
	su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + strlen(su.sun_path));
#endif
	strcpy(su.sun_path, socketFile);
	(*env)->ReleaseStringUTFChars(env, file, socketFile);
	socketFile = NULL;

	socklen_t suLength = (socklen_t)(strlen(su.sun_path) + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
			+ (unsigned char)sizeof(su.sun_len)
#endif
	);

	if (expectedInode > 0) {
		struct stat fdStat;

		// It's OK when the file's gone, but not OK if it refers to another inode.
		int statRes = stat(su.sun_path, &fdStat);
		if (statRes == 0) {
			long statInode = fdStat.st_ino;

			if (expectedInode != statInode) {
				// inode mismatch -> someone else took over this socket address
				_closeFd(env, fdServer, serverHandle);
				org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, ECONNABORTED, NULL, file);
				return;
			}
		}
	}

#if defined(junixsocket_use_poll_for_accept)

	{
		struct timeval optVal;
		socklen_t optLen = sizeof(optVal);
		int ret = getsockopt(serverHandle, SOL_SOCKET, SO_RCVTIMEO, &optVal, &optLen);
		if(optLen >= sizeof(optVal) && ret == 0 && (optVal.tv_sec > 0 || optVal.tv_usec > 0)) {
			struct pollfd pfd;
			pfd.fd = serverHandle;
			pfd.events = (POLLIN);

			uint64_t millis = ((uint64_t)optVal.tv_sec * 1000) + (uint64_t)(optVal.tv_usec / 1000);
			if(millis > INT_MAX) {
				millis = INT_MAX;
			}
			int millisRemaining = (int)millis;

			struct pollfd fds[] = {pfd};

			struct timespec timeStart;
			struct timespec timeEnd;

			if(clock_gettime(CLOCK_MONOTONIC, &timeEnd) == -1) {
				org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, NULL, file);
				return;
			}

			while (millisRemaining > 0) {
				// FIXME: should this be in a loop to ensure the timeout condition is met?

				timeStart = timeEnd;

				int pollTime = millisRemaining;
#if defined(junixsocket_use_poll_interval_millis)
				// Since poll doesn't abort upon closing the socket,
				// let's simply poll on a frequent basis
				if (pollTime > junixsocket_use_poll_interval_millis) {
					pollTime = junixsocket_use_poll_interval_millis;
				}
#endif

				ret = poll(fds, 1, pollTime);
				if(ret == 1) {
					if((pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) == 0) {
						break;
					} else {
						// FIXME better error handling
						org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, ETIMEDOUT, fdServer, file);
						return;
					}
				}
				int errnum = errno;
				if(clock_gettime(CLOCK_MONOTONIC, &timeEnd) == -1) {
					org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum, NULL, file);
					return;
				}
				int elapsed = (int)(timespecToMillis(&timeEnd) - timespecToMillis(&timeStart));
				if(elapsed <= 0) {
					elapsed = 1;
				}
				millisRemaining -= elapsed;
				if(millisRemaining <= 0) {
					org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, ETIMEDOUT, fdServer, file);
					return;
				}

				if(ret == -1) {
					if(errnum == EAGAIN) {
						// try again
						continue;
					}

					org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum, fdServer, file);
					return;
				}
			}
		}
	}
#endif

	int socketHandle;
	do {
		socketHandle = accept(serverHandle, (struct sockaddr *)&su, &suLength);
	}while (socketHandle == -1 && errno == EINTR);
	if(socketHandle < 0) {
		int errnum = errno;
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errnum, fdServer, file);
		return;
	}

	org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, socketHandle);
	return;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    bind
 * Signature: (Ljava/lang/String;Ljava/io/FileDescriptor;I)L
 */
JNIEXPORT jlong JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_bind
(JNIEnv * env, jclass clazz, jstring file, jobject fd, jint options) {
	bool reuse = (options == -1);

	const char* socketFile = (*env)->GetStringUTFChars(env, file, NULL);
	if(socketFile == NULL) {
		return -1; // OOME
	}
	if(strlen(socketFile) >= 104) {
		(*env)->ReleaseStringUTFChars(env, file, socketFile);
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Pathname too long for socket", file);
		return -1;
	}

	struct sockaddr_un su;
	su.sun_family = AF_UNIX;
#ifdef junixsocket_have_sun_len
	su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + strlen(su.sun_path));
#endif

	strcpy(su.sun_path, socketFile);
	(*env)->ReleaseStringUTFChars(env, file, socketFile);
	socketFile = NULL;

	socklen_t suLength = (socklen_t)(strlen(su.sun_path) + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
			+ sizeof(su.sun_len)
#endif
	);

	bool useSuTmp = false;
	struct sockaddr_un suTmp;
	if(reuse) {
		suTmp.sun_family = AF_UNIX;
		suTmp.sun_path[0] = 0;
	#ifdef junixsocket_have_sun_len
		suTmp.sun_len = (unsigned char)(sizeof(suTmp) - sizeof(suTmp.sun_path) + strlen(suTmp.sun_path));
	#endif
	}

	int myErr;

	int serverHandle = 0;
	for(int attempt=0;attempt<2;attempt++) {
		myErr = 0;
		if (serverHandle != 0) {
			close(serverHandle);
		}
		serverHandle = socket(AF_UNIX, SOCK_STREAM, 0);
		if(serverHandle == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, fd, file);
			return -1;
		}

		int ret;
		int optVal = 1;

		if (reuse) {
			// reuse address

			// This block is only prophylactic, as SO_REUSEADDR seems not to affect AF_UNIX sockets
			ret = setsockopt(serverHandle, SOL_SOCKET, SO_REUSEADDR, &optVal, sizeof(optVal));
			if(ret == -1) {
				org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env, errno, fd, file);
				return -1;
			}
		}

	#if defined(SO_NOSIGPIPE)
		// prevent raising SIGPIPE
		ret = setsockopt(serverHandle, SOL_SOCKET, SO_NOSIGPIPE, &optVal, sizeof(optVal));
		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env, errno, fd, file);
			return -1;
		}
	#endif

		int bindRes;
		if (attempt == 0 && !reuse) {
			// if we're not going to reuse the socket, let's try to connect first.
			// This avoids changing file metadata (e.g. ctime!)
			bindRes = -1;
			errno = 0;
		} else {
			bindRes = bind(serverHandle, (struct sockaddr *)&su, suLength);
		}

		myErr = errno;
		if (bindRes == 0) {
			break;
		} else if(attempt == 0 && (!reuse || myErr == EADDRINUSE)) {
			if (reuse) {
				close(serverHandle);

				// if we're reusing the socket, it's better to move away the existing
				// socket, bind ours to the correct address, and then connect to
				// the temporary file (to unblock the accept), and finally delete
				// the temporary file
				strcpy(suTmp.sun_path, "/tmp/junixsocket.XXXXXX");
				mkstemp(suTmp.sun_path);

				int renameRet = rename(su.sun_path, suTmp.sun_path);
				if(renameRet == -1) {
					if (errno != ENOENT) {
						org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr, NULL, file);
						return -1;
					}
				}
				useSuTmp = true;
			}

			if (useSuTmp) {
				// we've moved the existing socket, let's first bind and then let the old server know!
				_closeFd(env, fd, serverHandle);
				continue;
			}

			// if the given file exists, but is not a socket, ENOTSOCK is returned
			// if access is denied, EACCESS is returned
			do {
				ret = connect(serverHandle, (struct sockaddr *)&su, suLength);
			}while (ret == -1 && errno == EINTR);

			if(ret == 0) {
				// if we can successfully connect, the address is in use
				errno = EADDRINUSE;
				if (!reuse) {
					myErr = EADDRINUSE;
				}
			} else if(errno == ENOENT) {
				continue;
			}

			if(ret == 0 || (ret == -1 && (errno == ECONNREFUSED || errno == EADDRINUSE))) {
				// assume existing socket file
				_closeFd(env, fd, serverHandle);

				if (reuse || errno == ECONNREFUSED) {
					// either reuse existing socket, or take over a no longer working socket
					if(unlink(su.sun_path) == -1) {
						if (errno == ENOENT) {
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
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr, NULL, file);
		return -1;
	}

	int chmodRes = chmod(su.sun_path, 0666);
	if(chmodRes == -1) {
		myErr = errno;
		_closeFd(env, fd, serverHandle);
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr, NULL, file);
		return -1;
	}

	org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, serverHandle);

	struct stat fdStat;

	int statRes = stat(su.sun_path, &fdStat);
	if (statRes == -1) {
		myErr = errno;
		_closeFd(env, fd, serverHandle);
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr, NULL, file);
		return -1;
	}

	jlong inode = fdStat.st_ino;

	if (useSuTmp) {
		// now that we've bound our socket, let the previously listening server know.
		// We've moved their socket to a safe place, which we'll have to unlink, too!

		socklen_t suTmpLength = (socklen_t)(strlen(suTmp.sun_path) + sizeof(suTmp.sun_family)
#ifdef junixsocket_have_sun_len
				+ (unsigned char)sizeof(suTmp.sun_len)
#endif
		);

		int tmpHandle = socket(AF_UNIX, SOCK_STREAM, 0);
		if (tmpHandle != -1) {
			int ret;
			do {
				ret = connect(tmpHandle, (struct sockaddr *)&suTmp, suTmpLength);
			}while (ret == -1 && errno == EINTR);

			ret = shutdown(tmpHandle, SHUT_RDWR);
			ret = close(tmpHandle);
		}

		if(unlink(suTmp.sun_path) == -1) {
			if (errno != ENOENT) {
				org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, NULL, file);
				return -1;
			}
		}
	}

	return inode;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    listen
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_listen
(JNIEnv * env, jclass clazz, jobject fd, jint backlog) {
	int serverHandle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

	int listenRes = listen(serverHandle, backlog);
	if(listenRes == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, fd, NULL);
		return;
	}
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    connect
 * Signature: (Ljava/lang/String;Ljava/io/FileDescriptor;L)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_connect
(JNIEnv * env, jclass clazz, jstring file, jobject fd, jlong expectedInode) {
	const char* socketFile = (*env)->GetStringUTFChars(env, file, NULL);
	if(socketFile == NULL) {
		return; // OOME
	}
	if(strlen(socketFile) >= 104) {
		(*env)->ReleaseStringUTFChars(env, file, socketFile);
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Pathname too long for socket", file);
		return;
	}

	int socketHandle = socket(AF_UNIX, SOCK_STREAM, 0);
	if(socketHandle == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, fd, file);
		return;
	}

	struct sockaddr_un su;
	su.sun_family = AF_UNIX;
#ifdef junixsocket_have_sun_len
	su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + strlen(su.sun_path));
#endif

	strcpy(su.sun_path, socketFile);
	(*env)->ReleaseStringUTFChars(env, file, socketFile);
	socketFile = NULL;

	socklen_t suLength = (socklen_t)(strlen(su.sun_path) + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
			+ sizeof(su.sun_len)
#endif
	);

	if (expectedInode > 0) {
		struct stat fdStat;

		// It's OK when the file's gone, but not OK if it refers to another inode.
		int statRes = stat(su.sun_path, &fdStat);
		if (statRes == 0) {
			long statInode = fdStat.st_ino;

			if (expectedInode != statInode) {
				// inode mismatch -> someone else took over this socket address
				_closeFd(env, fd, socketHandle);
				org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, ECONNABORTED, NULL, file);
				return;
			}
		}
	}

	int ret;
	do {
		ret = connect(socketHandle, (struct sockaddr *)&su, suLength);
	}while(ret == -1 && errno == EINTR);

	if(ret == -1) {
		int myErr = errno;
		_closeFd(env, fd, socketHandle);
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, myErr, NULL, file);
		return;
	}

	org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, socketHandle);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    read
 * Signature: (Ljava/io/FileDescriptor;[BII)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_read(
		JNIEnv * env, jclass clazz, jobject fd, jbyteArray jbuf, jint offset,
		jint length)
{
	jbyte *buf = (*env)->GetByteArrayElements(env, jbuf, NULL);
	if(buf == NULL) {
		return -1; // OOME
	}
	jsize bufLen = (*env)->GetArrayLength(env, jbuf);
	if(offset < 0 || length < 0) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				kExceptionSocketException, "Illegal offset or length", NULL);
		return -1;
	}
	jint maxRead = bufLen - offset;
	if(length > maxRead) {
		length = maxRead;
	}

	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

	ssize_t count;
	do {
		count = read(handle, &(buf[offset]), (size_t)length);
	} while(count == -1 && errno == EINTR);

	(*env)->ReleaseByteArrayElements(env, jbuf, buf, 0);

	if(count == 0) {
		// read(2) returns 0 on EOF. Java returns -1.
		return -1;
	} else if(count == -1) {
		// read(2) returns -1 on error. Java throws an Exception.

//          Removed since non-blocking is not yet supported
//			if(errno == EAGAIN || errno == EWOULDBLOCK) {
//				return 0;
//			}
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, fd,
				NULL);
		return -1;
	}

	return (jint)count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    write
 * Signature: (Ljava/io/FileDescriptor;[BII)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_write(
		JNIEnv * env, jclass clazz, jobject fd, jbyteArray jbuf, jint offset,
		jint length)
{
	jbyte *buf = (*env)->GetByteArrayElements(env, jbuf, NULL);
	if(buf == NULL) {
		return -1; // OOME
	}
	jsize bufLen = (*env)->GetArrayLength(env, jbuf);
	if(offset < 0 || length < 0 || (length > (bufLen - offset))) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				kExceptionIndexOutOfBoundsException, "Illegal offset or length",
				NULL);
		return -1;
	}

	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

	ssize_t count;
	do {
		count = write(handle, &buf[offset], (size_t)length);
	} while(count == -1 && errno == EINTR);

	(*env)->ReleaseByteArrayElements(env, jbuf, buf, 0);

	if(count == -1) {
		if(errno == EAGAIN || errno == EWOULDBLOCK) {
			return 0;
		}

		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, fd,
				NULL);
		return -1;
	}

	return (jint)count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    close
 * Signature: (Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_close
(JNIEnv * env, jclass clazz, jobject fd) {
	if (fd == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				kExceptionNullPointerException, "fd", NULL);
		return;
	}
	(*env)->MonitorEnter(env, fd);
	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
	org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, -1);
	(*env)->MonitorExit(env, fd);

	int ret = _closeFd(env, fd, handle);
	if(ret == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, NULL, NULL);
		return;
	}
}

// Close a file descriptor. fd object and numeric handle must either be identical,
// or only one of them be valid.
//
// fd objects are marked closed by setting their fd value to -1.
static int _closeFd(JNIEnv * env, jobject fd, int handle) {
	int ret = 0;
	if (handle > 0) {
		shutdown(handle, SHUT_RDWR);
		ret = close(handle);
	}
	if (fd == NULL) {
		return ret;
	}
	(*env)->MonitorEnter(env, fd);
	int fdHandle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
	org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, -1);
	(*env)->MonitorExit(env, fd);

	if (handle > 0) {
		if (fdHandle > 0 && handle != fdHandle) {
//				fprintf(stderr, "Inconsistency: %i vs %i\n", handle, fdHandle);
		}
	} else if (fdHandle > 0) {
		shutdown(fdHandle, SHUT_RDWR);
		ret = close(fdHandle);
	}

	return ret;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    shutdown
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_shutdown
(JNIEnv * env, jclass clazz, jobject fd, jint mode) {
	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
	int ret = shutdown(handle, mode);
	if(ret == -1) {
		if(errno == ENOTCONN) {
			// ignore
			return;
		}
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, fd, NULL);
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
				kExceptionSocketException, "Unsupported socket option", NULL);
		return -1;
	}

	if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
		struct timeval optVal;
		socklen_t optLen = sizeof(optVal);
		int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env,
					errno, fd, NULL);
			return -1;
		}
		return (jint)(optVal.tv_sec * 1000 + optVal.tv_usec / 1000);
	} else if(optID == SO_LINGER) {
		struct linger optVal;
		socklen_t optLen = sizeof(optVal);

		int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env,
					errno, fd, NULL);
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

	int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
	if(ret == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env, errno,
				fd, NULL);
		return -1;
	}

	return optVal;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setSocketOptionInt
 * Signature: (Ljava/io/FileDescriptor;II)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setSocketOptionInt
(JNIEnv * env, jclass clazz, jobject fd, jint optID, jint value) {
	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

	optID = convertSocketOptionToNative(optID);
	if(optID == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Unsupported socket option", NULL);
		return;
	}

	if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
		// NOTE: SO_RCVTIMEO == SocketOptions.SO_TIMEOUT = 0x1006
		struct timeval optVal;
		optVal.tv_sec = value / 1000;
		optVal.tv_usec = (value % 1000) * 1000;
		int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal, sizeof(optVal));

		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env, errno, fd, NULL);
			return;
		}
		return;
	} else if(optID == SO_LINGER) {
		struct linger optVal;

		optVal.l_onoff = value >= 0;
		optVal.l_linger = value >= 0 ? value : 0;

		int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal, sizeof(optVal));
		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env, errno, fd, NULL);
			return;
		}
		return;
	}

	int optVal = (int)value;

	int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal, sizeof(optVal));
	if(ret == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwSockoptErrnumException(env, errno, fd, NULL);
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

	int count;
	ioctl(handle, FIONREAD, &count);
	if(count == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwErrnumException(env, errno, fd,
				NULL);
		return -1;
	}

	return count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    initServerImpl
 * Signature: (Lcom/newsclub/net/unix/AFUNIXServerSocket;Lcom/newsclub/net/unix/AFUNIXSocketImpl;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_initServerImpl
(JNIEnv * env, jclass clazz, jobject serverSocket, jobject impl) {
	jclass serverSocketClass = (*env)->GetObjectClass(env, serverSocket);
	jfieldID fieldID = (*env)->GetFieldID(env, serverSocketClass, "impl", "Ljava/net/SocketImpl;");
	if(fieldID == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Cannot find field \"impl\" in java.net.SocketImpl. Unsupported JVM?", NULL);
		return;
	}
	(*env)->SetObjectField(env, serverSocket, fieldID, impl);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setCreated
 * Signature: (Lcom/newsclub/net/unix/AFUNIXSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setCreated
(JNIEnv * env, jclass clazz, jobject socket) {
	jclass socketClass = (*env)->GetObjectClass(env, socket);
	jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setCreated", "()V");
	if(methodID == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Cannot find method \"setCreated\" in java.net.Socket. Unsupported JVM?", NULL);
		return;
	}
	(*env)->CallVoidMethod(env, socket, methodID);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setConnected
 * Signature: (Lcom/newsclub/net/unix/AFUNIXSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setConnected
(JNIEnv * env, jclass clazz, jobject socket) {
	jclass socketClass = (*env)->GetObjectClass(env, socket);
	jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setConnected", "()V");
	if(methodID == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Cannot find method \"setConnected\" in java.net.Socket. Unsupported JVM?", NULL);
		return;
	}
	(*env)->CallVoidMethod(env, socket, methodID);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setBound
 * Signature: (Lcom/newsclub/net/unix/AFUNIXSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setBound
(JNIEnv * env, jclass clazz, jobject socket) {
	jclass socketClass = (*env)->GetObjectClass(env, socket);
	jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setBound", "()V");
	if(methodID == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Cannot find method \"setBound\" in java.net.Socket. Unsupported JVM?", NULL);
		return;
	}
	(*env)->CallVoidMethod(env, socket, methodID);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setCreatedServer
 * Signature: (Lorg/newsclub/net/unix/AFUNIXServerSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setCreatedServer
(JNIEnv * env, jclass clazz, jobject socket) {
	jclass socketClass = (*env)->GetObjectClass(env, socket);
	jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setCreated", "()V");
	if(methodID == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Cannot find method \"setCreated\" in java.net.ServerSocket. Unsupported JVM?", NULL);
		return;
	}
	(*env)->CallVoidMethod(env, socket, methodID);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setBoundServer
 * Signature: (Lorg/newsclub/net/unix/AFUNIXServerSocket;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setBoundServer
(JNIEnv * env, jclass clazz, jobject socket) {
	jclass socketClass = (*env)->GetObjectClass(env, socket);
	jmethodID methodID = (*env)->GetMethodID(env, socketClass, "setBound", "()V");
	if(methodID == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Cannot find method \"setBound\" in java.net.ServerSocket. Unsupported JVM?", NULL);
		return;
	}
	(*env)->CallVoidMethod(env, socket, methodID);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setPort
 * Signature: (Lorg/newsclub/net/unix/AFUNIXSocketAddress;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setPort
(JNIEnv * env, jclass clazz, jobject addr, jint port) {
	jclass fileDescriptorClass = (*env)->GetObjectClass(env, addr);

	jobject fieldObject = addr;

	jfieldID portField;
	jfieldID holderField = (*env)->GetFieldID(env, fileDescriptorClass, "holder", "Ljava/net/InetSocketAddress$InetSocketAddressHolder;");
	if(holderField != NULL) {
		fieldObject = (*env)->GetObjectField(env, addr, holderField);
		jclass holderClass = (*env)->GetObjectClass(env, fieldObject);
		portField = (*env)->GetFieldID(env, holderClass, "port", "I");
	} else {
		portField = (*env)->GetFieldID(env, fileDescriptorClass, "port", "I");
	}
	if(portField == NULL) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, kExceptionSocketException,
				"Cannot find field \"port\" in java.net.InetSocketAddress. Unsupported JVM?", NULL);
		return;
	}
	(*env)->SetIntField(env, fieldObject, portField, port);
}

#ifdef __cplusplus
}
#endif
