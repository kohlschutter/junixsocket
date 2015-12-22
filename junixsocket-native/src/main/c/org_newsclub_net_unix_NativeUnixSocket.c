/**
 * junixsocket
 *
 * Copyright (c) 2009, 2010 NewsClub, Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifdef _GNU_SOURCE
# error This file requires that _GNU_SOURCE is not defined
#endif
#include "org_newsclub_net_unix_NativeUnixSocket.h"

#include <errno.h>
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

#ifndef FIONREAD
#include <sys/filio.h>
#endif
#ifdef __cplusplus
extern "C" {
#endif
#if 0
}
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
static jfieldID fdField;
static jclass AFUNIXSocketException;
static jmethodID AFUNIXSocketExceptionConstr;
static jclass IndexOutOfBoundsException;

// Get a global reference to a jclass
static jclass get_global_class_ref(JNIEnv* env, char *classname) {
	jclass LocalRef = (*env)->FindClass(env, classname);
	return LocalRef ? (*env)->NewGlobalRef(env, LocalRef) : NULL;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
	return JNI_VERSION_1_6;
}

/**
 * The native C part of the AFUnixSocket implementation.
 *
 * @author Christian Kohlschuetter
 */
static jclass regenerate_global_exceptions(JNIEnv *env) {
	jclass AFUNIXSocketExceptionLocal = (*env)->FindClass(env,
			"org/newsclub/net/unix/AFUNIXSocketException");
	if (AFUNIXSocketExceptionLocal == NULL) {
		return NULL;
	}
	AFUNIXSocketException = (*env)->NewWeakGlobalRef(env, AFUNIXSocketExceptionLocal);
	if (AFUNIXSocketException == NULL) {
		return NULL;
	}
	return AFUNIXSocketExceptionLocal;
}

static char *safe_strerror(int errnum) {
	int error = errno;
	errno = 0;
	size_t buflen = 256;
	char *buf = malloc(buflen);
	while (strerror_r(errnum, buf, buflen)) {
		buflen *= 3;
		buflen /= 2;
		char *buf_ = realloc(buf, buflen);
		if (NULL == buf_) {
			free(buf);
			return NULL;
		}
	}
        errno = error;
	return buf;
}

static void org_newsclub_net_unix_NativeUnixSocket_throwException(JNIEnv* env,
		char* message, jstring file)
{
	_Bool must_free = message == NULL;
	if (must_free) {
		message = safe_strerror(errno);
	}
	jstring str = (*env)->NewStringUTF(env, message);
	jclass AFUNIXSocketExceptionLocal = (*env)->NewLocalRef(env, AFUNIXSocketException);
	if (AFUNIXSocketExceptionLocal == NULL) {
		AFUNIXSocketExceptionLocal = regenerate_global_exceptions(env);
	}
	if (AFUNIXSocketExceptionLocal == NULL) {
		return;
	}
	jthrowable t = (jthrowable)(*env)->NewObject(env,
			AFUNIXSocketExceptionLocal, AFUNIXSocketExceptionConstr, str, file);
	(*env)->Throw(env, t);
	if (must_free) {
		free(message);
	}
}

static void cleanup(JNIEnv *env) {
	if (AFUNIXSocketException != NULL) {
		(*env)->DeleteWeakGlobalRef(env, AFUNIXSocketException);
		AFUNIXSocketException = NULL;
	}
	if (IndexOutOfBoundsException != NULL) {
		(*env)->DeleteGlobalRef(env, IndexOutOfBoundsException);
		IndexOutOfBoundsException = NULL;
	}
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
	JNIEnv *env;
	(*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6);
	if (NULL != env) {
		cleanup(env);
	}
}

JNIEXPORT void JNICALL
Java_org_newsclub_net_unix_NativeUnixSocket_registerNatives(JNIEnv *env, jclass Class) {
	IndexOutOfBoundsException = get_global_class_ref(env,
			"java/lang/IndexOutOfBoundsException");
	if (IndexOutOfBoundsException == NULL) {
		return;
	}

	jclass AFUNIXSocketExceptionLocal = regenerate_global_exceptions(env);
	if (AFUNIXSocketExceptionLocal == NULL) {
		goto fail;
	}

	jclass fileDescriptorClass = NULL;
	AFUNIXSocketExceptionConstr = (*env)->GetMethodID(env, AFUNIXSocketExceptionLocal,
			 "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
	if (AFUNIXSocketExceptionConstr == NULL) {
		goto fail;
	}

	fileDescriptorClass = (*env)->FindClass(env, "java/io/FileDescriptor");
	if (fileDescriptorClass == NULL) {
		goto fail;
	}

	fdField = (*env)->GetFieldID(env, fileDescriptorClass, "fd", "I");
	if(fdField == NULL) {
		(*env)->ExceptionClear(env);
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				"Cannot find field \"fd\" in java.io.FileDescriptor. Unsupported JVM?",
				NULL);
		goto fail;
	}
	return;
  fail:
	cleanup(env);
}

static void org_newsclub_net_unix_NativeUnixSocket_throwIndexOutOfBoundsException(
		JNIEnv* env)
{
	(*env)->ThrowNew(env, IndexOutOfBoundsException, "Invalid buffer offset");
}

static int org_newsclub_net_unix_NativeUnixSocket_getFD(JNIEnv * env, jobject fd)
{
	return (*env)->GetIntField(env, fd, fdField);
}

static void org_newsclub_net_unix_NativeUnixSocket_initFD(JNIEnv * env, jobject fd,
		int handle)
{
	(*env)->SetIntField(env, fd, fdField, handle);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    accept
 * Signature: (Ljava/lang/String;Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_accept
(JNIEnv * env, jclass clazz, jstring file, jobject fdServer, jobject fd) {

	const char* socketFile = (*env)->GetStringUTFChars(env, file, NULL);
	if(socketFile == NULL) {
		return; // OOME
	}
	if(strlen(socketFile) >= 104) {
		(*env)->ReleaseStringUTFChars(env, file, socketFile);
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
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

	socklen_t suLength = (socklen_t)(strlen(su.sun_path) + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
	+ (unsigned char)sizeof(su.sun_len)
#endif
	);

	int socketHandle = accept(serverHandle, (struct sockaddr *)&su, &suLength);
	if(socketHandle < 0) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
		return;
	}

	org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, socketHandle);
	return;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    bind
 * Signature: (Ljava/lang/String;Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_bind
(JNIEnv * env, jclass clazz, jstring file, jobject fd, jint backlog) {
	const char* socketFile = (*env)->GetStringUTFChars(env, file, NULL);
	if(socketFile == NULL) {
		return; // OOME
	}
	if(strlen(socketFile) >= 104) {
		(*env)->ReleaseStringUTFChars(env, file, socketFile);
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				"Pathname too long for socket", file);
		return;
	}

	int serverHandle = socket(AF_UNIX, SOCK_STREAM, 0);
	if(serverHandle == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
		return;
	}

	// This block is only prophylactic, as SO_REUSEADDR seems not to work with AF_UNIX
	int optVal = 1;
	int ret = setsockopt(serverHandle, SOL_SOCKET, SO_REUSEADDR, &optVal, sizeof(optVal));
	if(ret == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, NULL);
	}

	struct sockaddr_un su;
	su.sun_family = AF_UNIX;
#ifdef junixsocket_have_sun_len
	su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + strlen(su.sun_path));
#endif

	strcpy(su.sun_path, socketFile);
	(*env)->ReleaseStringUTFChars(env, file, socketFile);

	socklen_t suLength = (socklen_t)(strlen(su.sun_path) + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
	+ sizeof(su.sun_len)
#endif
	);

	int bindRes = bind(serverHandle, (struct sockaddr *)&su, suLength);

	if(bindRes == -1) {
		int myErr = errno;
		if(errno == EADDRINUSE) {
			// Let's check whether the address *really* is in use.
			// Maybe it's just a dead reference

			// if the given file exists, but is not a socket, ENOTSOCK is returned
			// if access is denied, EACCESS is returned
			ret = connect(serverHandle, (struct sockaddr *)&su, suLength);

			if(ret == -1 && errno == ECONNREFUSED) {
				// assume non-connected socket

				close(serverHandle);
				if(unlink(socketFile) == -1) {
					org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
					return;
				}

				serverHandle = socket(AF_UNIX, SOCK_STREAM, 0);
				if(serverHandle == -1) {
					org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
					return;
				}

				bindRes = bind(serverHandle, (struct sockaddr *)&su, suLength);
				if(bindRes == -1) {
					errno = myErr;
					close(serverHandle);
					myErr = errno;
					org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
					return;
				}
			} else {
				close(serverHandle);
				errno = myErr;
				org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
				return;
			}
		} else {
			close(serverHandle);
			errno = myErr;
			org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
			return;
		}
	}

	int chmodRes = chmod(su.sun_path, 0666);
	if(chmodRes == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
	}

	int listenRes = listen(serverHandle, backlog);
	if(listenRes == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
		return;
	}

	org_newsclub_net_unix_NativeUnixSocket_initFD(env, fd, serverHandle);
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
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, NULL);
		return;
	}
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    connect
 * Signature: (Ljava/lang/String;Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_connect
(JNIEnv * env, jclass clazz, jstring file, jobject fd) {
	const char* socketFile = (*env)->GetStringUTFChars(env, file, NULL);
	if(socketFile == NULL) {
		return; // OOME
	}
	if(strlen(socketFile) >= 104) {
		(*env)->ReleaseStringUTFChars(env, file, socketFile);
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				"Pathname too long for socket", file);
		return;
	}

	int socketHandle = socket(AF_UNIX, SOCK_STREAM, 0);
	if(socketHandle == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
		return;
	}

	struct sockaddr_un su;
	su.sun_family = AF_UNIX;
#ifdef junixsocket_have_sun_len
	su.sun_len = (unsigned char)(sizeof(su) - sizeof(su.sun_path) + strlen(su.sun_path));
#endif

	strcpy(su.sun_path, socketFile);
	(*env)->ReleaseStringUTFChars(env, file, socketFile);

	socklen_t suLength = (socklen_t)(strlen(su.sun_path) + sizeof(su.sun_family)
#ifdef junixsocket_have_sun_len
			+ sizeof(su.sun_len)
#endif
	);

	int ret = connect(socketHandle, (struct sockaddr *)&su, suLength);
	if(ret == -1) {
		int myErr = errno;
		close(socketHandle);
		errno = myErr;
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, file);
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
				"Illegal offset or length", NULL);
		return -1;
	}
	jint maxRead = bufLen - offset;
	if(length > maxRead) {
		length = maxRead;
	}

	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

	ssize_t count = read(handle, &(buf[offset]), (size_t)length);
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
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				NULL, NULL);
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
	if(offset < 0 || length < 0) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				"Illegal offset or length", NULL);
		return -1;
	}

	if(length > bufLen - offset) {
		org_newsclub_net_unix_NativeUnixSocket_throwIndexOutOfBoundsException(
				env);
		return -1;
	}

	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);

	ssize_t count = write(handle, &buf[offset], (size_t)length);
	(*env)->ReleaseByteArrayElements(env, jbuf, buf, 0);

	if(count == -1) {
		if(errno == EAGAIN || errno == EWOULDBLOCK) {
			return 0;
		}
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				NULL, NULL);
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
	int handle = org_newsclub_net_unix_NativeUnixSocket_getFD(env, fd);
	int ret = close(handle);
	if(ret == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, NULL);
	}
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
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, NULL);
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
				"Unsupported socket option", NULL);
		return -1;
	}

	if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
		struct timeval optVal;
		socklen_t optLen = sizeof(optVal);
		int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwException(env,
					NULL, NULL);
			return -1;
		}
		return (jint)(optVal.tv_sec * 1000 + optVal.tv_usec / 1000);
	} else if(optID == SO_LINGER) {
		struct linger optVal;
		socklen_t optLen = sizeof(optVal);

		int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwException(env,
					NULL, NULL);
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
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				NULL, NULL);
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
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				"Unsupported socket option", NULL);
		return;
	}

	if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
		struct timeval optVal;
		optVal.tv_sec = value / 1000;
		optVal.tv_usec = (value % 1000) * 1000;
		int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal, sizeof(optVal));

		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, NULL);
		}
		return;
	} else if(optID == SO_LINGER) {
		struct linger optVal;

		optVal.l_onoff = value >= 0;
		optVal.l_linger = value >= 0 ? value : 0;

		int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal, sizeof(optVal));
		if(ret == -1) {
			org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, NULL);
		}
		return;
	}

	int optVal = (int)value;

	int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal, sizeof(optVal));
	if(ret == -1) {
		org_newsclub_net_unix_NativeUnixSocket_throwException(env, NULL, NULL);
	}
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    unlink
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_unlink
(JNIEnv * env, jclass clazz, jstring file) {
	const char* socketFile = (*env)->GetStringUTFChars(env, file, NULL);
	int ret = unlink(socketFile);
	(*env)->ReleaseStringUTFChars(env, file, socketFile);

	if(ret == -1) {
		// ignore
		//	org_newsclub_net_NativeUnixSocket_throwException(env, NULL, NULL);
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
		org_newsclub_net_unix_NativeUnixSocket_throwException(env,
				NULL, NULL);
		return -1;
	}

	return count;
}

#if 0
{
#endif
#ifdef __cplusplus
}
#endif

/*
 * Local Variables:
 * indent-tabs-mode: t
 * c-basic-offset: 4
 * tab-width: 4
 */
