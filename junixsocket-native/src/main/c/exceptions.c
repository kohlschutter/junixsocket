/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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

#include "config.h"
#include "exceptions.h"

#include "jniutil.h"
#include "filedescriptors.h"

// NOTE: The exceptions must all be either inherit from IOException or RuntimeException/Error
static char *kExceptionClassnames[kExceptionMaxExcl] = {
    "java/net/SocketException", // kExceptionSocketException
    "java/net/SocketTimeoutException", // kExceptionSocketTimeoutException
    "java/lang/IndexOutOfBoundsException", // kExceptionIndexOutOfBoundsException
    "java/lang/IllegalStateException", // kExceptionIllegalStateException
    "java/lang/NullPointerException", // kExceptionNullPointerException
    "java/net/NoRouteToHostException", // kExceptionNoRouteToHostException
    "java/nio/channels/ClosedChannelException", // kExceptionClosedChannelException
    "org/newsclub/net/unix/InvalidArgumentSocketException", // kExceptionInvalidArgumentSocketException
    "org/newsclub/net/unix/AddressUnavailableSocketException", // kExceptionAddressUnavailableSocketException
    "org/newsclub/net/unix/OperationNotSupportedSocketException", // kExceptionOperationNotSupportedSocketException
    "org/newsclub/net/unix/NoSuchDeviceSocketException", // kExceptionNoSuchDeviceSocketException
    "org/newsclub/net/unix/BrokenPipeSocketException", // kExceptionBrokenPipeSocketException
    "org/newsclub/net/unix/ConnectionResetSocketException", // kExceptionConnectionResetSocketException
    "org/newsclub/net/unix/SocketClosedException", // kExceptionSocketClosedException
    "org/newsclub/net/unix/NotConnectedSocketException", // kExceptionNotConnectedSocketException
    "java/io/FileNotFoundException", // kExceptionFileNotFoundException
    "java/nio/file/FileAlreadyExistsException", // kExceptionFileAlreadyExistsException
    "java/io/IOException", // kExceptionIOException
    "org/newsclub/net/unix/OperationNotSupportedIOException", // kExceptionOperationNotSupportedIOException
};

static jclass *kExceptionClasses;

static jmethodID *kExceptionConstructors;

void init_exceptions(JNIEnv *env) {
    kExceptionClasses = malloc(sizeof(jclass) * kExceptionMaxExcl);
    kExceptionConstructors = malloc(sizeof(jmethodID) * kExceptionMaxExcl);

    for (int i=0; i<kExceptionMaxExcl; i++) {
        jclass exc = findClassAndGlobalRef(env, kExceptionClassnames[i]);
        if(!exc) {
#if DEBUG
            fprintf(stderr, "Could not find exception class: %s\n", kExceptionClassnames[i]);
#endif
            exc = findClassAndGlobalRef(env, "java/lang/IllegalStateException"); // fallback
        }
        kExceptionClasses[i] = exc;

        jmethodID m = (*env)->GetMethodID(env, exc, "<init>", "(Ljava/lang/String;)V");
        if(!m) {
            (*env)->ExceptionClear(env);
            m = (*env)->GetMethodID(env, exc, "<init>", "()V");
            if(!m) {
#if DEBUG
                fprintf(stderr, "Could not initialize handler for exception %s\n", kExceptionClassnames[i]);
#endif
            }
        }

        kExceptionConstructors[i] = m;
    }
}

void destroy_exceptions(JNIEnv *env) {
    for (int i=0; i<kExceptionMaxExcl; i++) {
        releaseClassGlobalRef(env, kExceptionClasses[i]);
    }
    free(kExceptionConstructors);
    free(kExceptionClasses);
}

void _throwException(JNIEnv* env, ExceptionType exceptionType, char* message)
{
    if((*env)->ExceptionCheck(env)) {
        // keep the existing exception
        return;
    }

    if((int)exceptionType < 0 || exceptionType >= kExceptionMaxExcl) {
        exceptionType = kExceptionIllegalStateException;
    }
    jclass exc = kExceptionClasses[exceptionType];
    jmethodID constr = kExceptionConstructors[exceptionType];

    jstring str;
    if(message == NULL) {
        message = "Unknown error";
    }
    str = (*env)->NewStringUTF(env, message);

    jthrowable t = (jthrowable)(*env)->NewObject(env, exc, constr, str);
    (*env)->Throw(env, t);
}

void throwErrnumException1(JNIEnv* env, int errnum, jobject fdToClose, jboolean isSocket)
{
    ExceptionType exceptionType;

    switch(errnum) {
        case EAGAIN:
        case ETIMEDOUT:
            exceptionType = kExceptionSocketTimeoutException;
            break;
        case EHOSTUNREACH:
            exceptionType = kExceptionNoRouteToHostException;
            break;
#if defined(_WIN32)
        case 87 /*ERROR_INVALID_PARAMETER*/:
#endif
        case EINVAL:
            if(isSocket) {
                exceptionType = kExceptionInvalidArgumentSocketException;
            } else {
                exceptionType = kExceptionIOException;
            }
            break;
        case EADDRNOTAVAIL:
            exceptionType = kExceptionAddressUnavailableSocketException;
            break;
#if ENOTSUP != EOPNOTSUPP
        case ENOTSUP:
#endif
        case EOPNOTSUPP:
#if EPROTOTYPE
        case EPROTOTYPE:
#endif
#if EPROTONOSUPPORT
        case EPROTONOSUPPORT:
#endif
#if ESOCKTNOSUPPORT
        case ESOCKTNOSUPPORT:
#endif
#if EPFNOSUPPORT
        case EPFNOSUPPORT:
#endif
#if EAFNOSUPPORT
        case EAFNOSUPPORT:
#endif
        case ENOSYS:
            if(isSocket) {
                exceptionType = kExceptionOperationNotSupportedSocketException;
            } else {
                exceptionType = kExceptionOperationNotSupportedIOException;
            }
            break;
        case ENODEV:
            if(isSocket) {
                exceptionType = kExceptionNoSuchDeviceSocketException;
            } else {
                exceptionType = kExceptionIOException;
            }
            break;
        case ENOTCONN:
            exceptionType = kExceptionNotConnectedSocketException;
            if(fdToClose != NULL) {
                _closeFd(env, fdToClose, -1);
            }
            break;
        case EPIPE:
            exceptionType = kExceptionBrokenPipeSocketException;
            if(fdToClose != NULL) {
                _closeFd(env, fdToClose, -1);
            }
            break;
        case ECONNRESET:
            exceptionType = kExceptionConnectionResetSocketException;
            if(fdToClose != NULL) {
                _closeFd(env, fdToClose, -1);
            }
            break;
#if ECLOSED
        case ECLOSED:
#endif
        case ECONNABORTED:
            // upon accept(2) either ECONNABORTED (seen on macOS) or ECLOSED (seen on IBM i OS/400)
            // may indicate that the server socket has been closed
            exceptionType = kExceptionSocketClosedException;
            if(fdToClose != NULL) {
                _closeFd(env, fdToClose, -1);
            }
            break;
        case ENOENT:
            if(isSocket) {
                exceptionType = kExceptionSocketException; // unexpected
            } else {
                exceptionType = kExceptionFileNotFoundException;
            }
            break;
        case EEXIST:
            if(isSocket) {
                exceptionType = kExceptionSocketException; // unexpected
            } else {
                exceptionType = kExceptionFileAlreadyExistsException;
            }
            break;
        case EBADF:
            // close socket fd, so Socket#isClosed returns true
            if(fdToClose != NULL) {
                _closeFd(env, fdToClose, -1);
            }

            CK_FALLTHROUGH;
        default:
            if(isSocket) {
                exceptionType = kExceptionSocketException;
            } else {
                exceptionType = kExceptionIOException;
            }
    }

    size_t buflen = 255;
    char *message = calloc(1, buflen + 1);

#ifdef __linux__
    __auto_type otherBuf = strerror_r(errnum, message, buflen);
    if(CK_IGNORE_CAST_BEGIN (unsigned int)otherBuf CK_IGNORE_CAST_END > 255) {
        // strerror_r is ill-defined.
        strncpy(message,
                CK_IGNORE_CAST_BEGIN (char *)otherBuf CK_IGNORE_CAST_END,
                buflen);
    }
#elif defined(_WIN32)
    if(errnum >= 10000) {
        // winsock error
        FormatMessage (FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                       NULL, errnum, MAKELANGID (LANG_NEUTRAL, SUBLANG_DEFAULT),
                       message, buflen, NULL);
    } else {
        switch(errnum) {
            case 87:
                strcpy(message, "Invalid parameter");
                break;
            case 138:
                strcpy(message, "Permission to access the network was denied.");
                break;
            case 487:
                strcpy(message, "Invalid address");
                break;
            default:
                strncpy(message, strerror(errnum), buflen);
        }
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
#if __TOS_MVS__
    // snprintf is broken on z/OS
#else
    char *message1 = calloc(1, buflen);
    CK_IGNORE_USED_BUT_MARKED_UNUSED_BEGIN
    snprintf(message1, buflen, "%s; errno=%i", message, errnum);
    CK_IGNORE_USED_BUT_MARKED_UNUSED_END
    free(message);
    message = message1;
#endif
#endif

    _throwException(env, exceptionType, message);

    free(message);
}

CK_INLINE_IF_POSSIBLE void _throwErrnumException(JNIEnv* env, int errnum, jobject fdToClose)
{
    throwErrnumException1(env, errnum, fdToClose, true);
}

CK_INLINE_IF_POSSIBLE void throwIOErrnumException(JNIEnv* env, int errnum, jobject fdToClose)
{
    throwErrnumException1(env, errnum, fdToClose, false);
}

void _throwSockoptErrnumException(JNIEnv* env, int errnum, jobject fd)
{
    // when setsockopt returns an error with EINVAL, it may mean the socket was shut down already
    if(errnum == EINVAL) {
        int handle = _getFD(env, fd);
        struct sockaddr addr = {0};
        socklen_t len = 0;
        int ret = getsockname(handle, &addr, &len);
        if(ret == -1) {
            _throwException(env, kExceptionSocketException, "Socket is closed");
            return;
        }
    }

    _throwErrnumException(env, errnum, fd);
}
