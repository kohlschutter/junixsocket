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

#include "config.h"
#include "filedescriptors.h"

#include "exceptions.h"
#include "jniutil.h"
#include "address.h"

static jclass class_FileDescriptor = NULL;

static jfieldID fieldID_fd = NULL;
static jmethodID methodID_getFd = NULL;
static jmethodID methodID_setFd = NULL;

#if defined(_WIN32)
static jfieldID fieldID_handle = NULL;
#endif

typedef enum {
    kFDTypeOther = 0,
    kFDTypeOtherSocket,
    kFDTypeOtherStreamSocket,
    kFDTypeOtherDatagramSocket,
    kFDTypeAFUNIXStreamSocket,
    kFDTypeAFUNIXDatagramSocket,
#if junixsocket_have_tipc
    kFDTypeAFTIPCStreamSocket,
    kFDTypeAFTIPCDatagramSocket,
#endif
#if junixsocket_have_vsock
    kFDTypeAFVSOCKStreamSocket,
    kFDTypeAFVSOCKDatagramSocket,
#endif
#if junixsocket_have_system
    kFDTypeAFSYSTEMStreamSocket,
    kFDTypeAFSYSTEMDatagramSocket,
#endif
    kFDTypeMaxExcl
} FileDescriptorType;

#if junixsocket_have_tipc
static char* const kClassnameAFTIPCSocket = "org/newsclub/net/unix/tipc/AFTIPCSocket";
static char* const kClassnameAFTIPCDatagramSocket = "org/newsclub/net/unix/tipc/AFTIPCDatagramSocket";
#endif
#if junixsocket_have_vsock
static char* const kClassnameAFVSOCKSocket = "org/newsclub/net/unix/vsock/AFVSOCKSocket";
static char* const kClassnameAFVSOCKDatagramSocket = "org/newsclub/net/unix/vsock/AFVSOCKDatagramSocket";
#endif
#if junixsocket_have_system
static char* const kClassnameAFSYSTEMSocket = "org/newsclub/net/unix/darwin/system/AFSYSTEMSocket";
static char* const kClassnameAFSYSTEMDatagramSocket = "org/newsclub/net/unix/darwin/system/AFSYSTEMDatagramSocket";
#endif

// NOTE: The exceptions must all be either inherit from IOException or RuntimeException/Error
static char *kFDTypeClassNames[kFDTypeMaxExcl] = {
    "java/io/FileDescriptor",
    "java/io/FileDescriptor",
    "java/net/Socket",
    "java/net/DatagramSocket",
    "org/newsclub/net/unix/AFUNIXSocket",
    "org/newsclub/net/unix/AFUNIXDatagramSocket",
#if junixsocket_have_tipc
    kClassnameAFTIPCSocket,
    kClassnameAFTIPCDatagramSocket,
#endif
#if junixsocket_have_vsock
    kClassnameAFVSOCKSocket,
    kClassnameAFVSOCKDatagramSocket,
#endif
#if junixsocket_have_system
    kClassnameAFSYSTEMSocket,
    kClassnameAFSYSTEMDatagramSocket,
#endif
};

static jclass *kFDTypeClasses;

static jclass kRedirectImplClass;
static jmethodID kRedirectImplConstructor;

void init_filedescriptors(JNIEnv *env) {
    kRedirectImplClass = findClassAndGlobalRef0(env, "java/lang/ProcessBuilder$RedirectPipeImpl", JNI_TRUE);
    kRedirectImplConstructor = kRedirectImplClass == NULL ? NULL : (*env)->GetMethodID(env, kRedirectImplClass, "<init>", "()V");
    (*env)->ExceptionClear(env);

    kFDTypeClasses = malloc(sizeof(jclass) * kFDTypeMaxExcl);
    for(int i=0; i<kFDTypeMaxExcl; i++) {
        char *classname = kFDTypeClassNames[i];

        kFDTypeClasses[i] = findClassAndGlobalRef0
        (env, classname,
         JNI_FALSE
#if junixsocket_have_tipc
         // Even if TIPC is technically available, the junixsocket-tipc jar may not be in the classpath,
         // therefore it's OK if these classes are missing
         || (classname == kClassnameAFTIPCSocket || classname == kClassnameAFTIPCDatagramSocket)
#endif
#if junixsocket_have_vsock
         // Even if VSOCK is technically available, the junixsocket-vsock jar may not be in the classpath,
         // therefore it's OK if these classes are missing
         || (classname == kClassnameAFVSOCKSocket || classname == kClassnameAFVSOCKDatagramSocket)
#endif
#if junixsocket_have_system
         // Even if AF_SYSTEM is technically available, the junixsocket-system jar may not be in the classpath,
         // therefore it's OK if these classes are missing
         || (classname == kClassnameAFSYSTEMSocket || classname == kClassnameAFSYSTEMDatagramSocket)
#endif
);
    }

    class_FileDescriptor = kFDTypeClasses[0];
    fieldID_fd = (*env)->GetFieldID(env, class_FileDescriptor, "fd", "I");
    if (fieldID_fd == NULL) {
        (*env)->ExceptionClear(env);

        // https://github.com/AndroidSDKSources/android-sdk-sources-for-api-level-33/blob/master/java/io/FileDescriptor.java
        methodID_getFd = (*env)->GetMethodID(env, class_FileDescriptor, "getInt$", "()I");
        (*env)->ExceptionClear(env);
        methodID_setFd = (*env)->GetMethodID(env, class_FileDescriptor, "setInt$", "(I)V");

        if(methodID_getFd == NULL || methodID_setFd == NULL) {
            (*env)->ExceptionClear(env);
            fieldID_fd = (*env)->GetFieldID(env, class_FileDescriptor, "descriptor", "I");
        }
    }

#if defined(_WIN32)
    fieldID_handle = (*env)->GetFieldID(env, class_FileDescriptor, "handle", "J");
#endif
}

void destroy_filedescriptors(JNIEnv *env) {
    for(int i=0; i<kFDTypeMaxExcl; i++) {
        releaseClassGlobalRef(env, kFDTypeClasses[i]);
    }
    releaseClassGlobalRef(env, kRedirectImplClass);

    fieldID_fd = NULL;
#if defined(_WIN32)
    fieldID_handle = NULL;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    initFD
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_initFD
 (JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jint handle)
{
    _initFD(env, fd, handle);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    getFD
 * Signature: (Ljava/io/FileDescriptor;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_getFD
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd) {
     return _getFD(env, fd);
 }

jint _getFD(JNIEnv * env, jobject fd)
{
    if(fieldID_fd == NULL && methodID_getFd != NULL) {
        // Android
        return (*env)->CallIntMethod(env, fd, methodID_getFd);
    }

    return (*env)->GetIntField(env, fd, fieldID_fd);
}

void _initFD(JNIEnv * env, jobject fd, jint handle)
{
    if(fieldID_fd == NULL && methodID_setFd != NULL) {
        // Android
        (*env)->CallVoidMethod(env, fd, methodID_setFd, handle);
        if((*env)->ExceptionCheck(env)) {
            return;
        }
        return;
    }

    (*env)->SetIntField(env, fd, fieldID_fd, handle);
}

#if defined(_WIN32)
jlong _getHandle(JNIEnv * env, jobject fd)
{
    return (*env)->GetLongField(env, fd, fieldID_handle);
}

void _initHandle(JNIEnv * env, jobject fd, jlong handle)
{
    (*env)->SetLongField(env, fd, fieldID_handle, handle);
}
#endif

// Close a file descriptor. fd object and numeric handle must either be identical,
// or only one of them be valid.
//
// fd objects are marked closed by setting their fd value to -1.
int _closeFd(JNIEnv * env, jobject fd, int handle)
{
    int ret = 0;
    if(fd == NULL) {
        if(handle >= 0) {
            shutdown(handle, SHUT_RDWR);
#if defined(_WIN32)
            ret = closesocket(handle);
#else
            ret = close(handle);
#endif
        }
        return ret;
    }
    (*env)->MonitorEnter(env, fd);

#if defined(_WIN32)
    jboolean isSocket;
    jlong handleWin = _getHandle(env, fd);
    if(handleWin > 0) {
        if(handle >= 0) {
            _close(handle);
        }
        DisconnectNamedPipe((HANDLE)handleWin);
        CloseHandle((HANDLE)handleWin);
        isSocket = false;
    } else {
        isSocket = true;
    }
#else
    if(handle >= 0) {
        shutdown(handle, SHUT_RDWR);
        ret = close(handle);
    }

#endif

    int fdHandle = _getFD(env, fd);
    _initFD(env, fd, -1);
#if defined(_WIN32)
    _initHandle(env, fd, -1);
#endif
    (*env)->MonitorExit(env, fd);

    if(handle >= 0) {
        if(fdHandle >= 0 && handle != fdHandle) {
#if DEBUG
            fprintf(stderr, "NativeUnixSocket_closeFd inconsistency: handle %i vs fdHandle %i\n", handle, fdHandle);
            fflush(stderr);
#endif
        }
    }

    if(fdHandle >= 0) {
#if defined(_WIN32)
        if(isSocket) {
            shutdown(fdHandle, SHUT_RDWR);
            ret = closesocket(fdHandle);
        } else {
            ret = _close(fdHandle);
        }
#else
        shutdown(fdHandle, SHUT_RDWR);
        ret = close(fdHandle);
#endif
    }

    return ret;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    close
 * Signature: (Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_close
 (JNIEnv * env, jclass clazz CK_UNUSED, jobject fd)
{
    if(fd == NULL) {
        _throwException(env, kExceptionNullPointerException, "fd");
        return;
    }
    (*env)->MonitorEnter(env, fd);
    int handle = _getFD(env, fd);
    _initFD(env, fd, -1);
    (*env)->MonitorExit(env, fd);

    int ret = _closeFd(env, fd, handle);
    if(ret == -1) {
        _throwErrnumException(env, errno, NULL);
        return;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    shutdown
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_shutdown
(JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jint mode)
{
    int handle = _getFD(env, fd);
    int ret = shutdown(handle, mode);
    if(ret == -1) {
        int errnum = socket_errno;
        if(errnum == ENOTCONN || errnum == EINVAL || errnum == EBADF) {
            // ignore
            return;
        }
        _throwErrnumException(env, errnum, fd);
        return;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    configureBlocking
 * Signature: (Ljava/io/FileDescriptor;Z)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_configureBlocking
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd, jboolean blocking) {
     int handle = _getFD(env, fd);
#if defined(_WIN32)
     u_long mode = blocking ? 0 : 1;
     if(ioctlsocket(handle, FIONBIO, &mode) != NO_ERROR) {
         if(socket_errno == WSAENOTSOCK) {
             CK_IGNORE_CAST_BEGIN
             HANDLE h = (HANDLE)_get_osfhandle(handle);
             CK_IGNORE_CAST_END

             if(handle) {
                 mode = blocking ? PIPE_WAIT : PIPE_NOWAIT;
                 if(SetNamedPipeHandleState(h, (void*)&mode, NULL, NULL) == 0) {
                     _throwErrnumException(env, errno, NULL);
                     return;
                 }
             } else {
                 _throwErrnumException(env, errno, NULL);
             }
         } else {
             _throwErrnumException(env, errno, NULL);
             return;
         }
     }
#else
     int flags = fcntl(handle, F_GETFL);
     if(flags == -1) {
         _throwErrnumException(env, socket_errno, NULL);
         return;
     }

     int ret = fcntl(handle, F_SETFL, blocking ? flags &~ O_NONBLOCK :  flags | O_NONBLOCK);
     if(ret == -1) {
         _throwErrnumException(env, socket_errno, NULL);
     }
#endif
 }

jboolean checkNonBlocking(int handle, int errnum) {
    return checkNonBlocking0(handle, errnum, org_newsclub_net_unix_NativeUnixSocket_OPT_NON_BLOCKING);
}

jboolean checkNonBlocking0(int handle, int errnum, int options) {
#if defined(_WIN32)
    CK_ARGUMENT_POTENTIALLY_UNUSED(handle);
    return ((options & org_newsclub_net_unix_NativeUnixSocket_OPT_NON_BLOCKING) != 0)
    && (errnum == 0 || errnum == WSAEWOULDBLOCK || errnum == 232 /* named pipes may return this? */);
#else
    if (errnum == EWOULDBLOCK || errnum == EAGAIN || errnum == EINPROGRESS) {
        CK_ARGUMENT_POTENTIALLY_UNUSED(options);
        int flags = fcntl(handle, F_GETFL);
        return (flags != -1 && (flags & O_NONBLOCK));
    } else {
        return false;
    }
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    primaryType
 * Signature: (Ljava/io/FileDescriptor;)Ljava/lang/Class;
 */
JNIEXPORT jclass JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_primaryType
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd) {
     if(fd == NULL) {
         return NULL;
     }

     int handle = _getFD(env, fd);
     if(handle < 0) {
#if defined(_WIN32)
         jlong handleWin = _getHandle(env, fd);
         if(handleWin != -1) {
             return kFDTypeClasses[kFDTypeOther];
         }
#endif
         return NULL;
     }

     jux_sockaddr_t addr0 = {0};
     struct sockaddr *addr = (struct sockaddr *)&addr0;

     int type = 0;
     socklen_t typeLen = sizeof(type);

     int ret;

     errno = 0;
#if defined(__sun) || defined(__sun__)
     // NOTE: Solaris does not set addr->sa_family for getsockname for AF_UNIX datagram sockets.
     ret = getsockopt(handle, SOL_SOCKET, SO_DOMAIN, &type, &typeLen);
     if(ret != 0) {
         int errnum = socket_errno;
         if(errnum == ENOTSOCK) {
             return kFDTypeClasses[kFDTypeOther];
         }
         _throwErrnumException(env, errnum, fd);
         return NULL;
     } else {
         addr->sa_family = type;
     }
#else
     socklen_t len = sizeof(jux_sockaddr_t);
     ret = getsockname(handle, addr, &len);
     if(ret != 0) {
         int errnum = socket_errno;
         switch(errnum) {
             case ENOTSOCK:
                 return kFDTypeClasses[kFDTypeOther];
             case ENOTCONN:
                 break;
             case EOPNOTSUPP:
                 ret = getpeername(handle, addr, &len);
                 if(ret != 0) {
                     switch(errnum) {
                         case ENOTSOCK:
                             return kFDTypeClasses[kFDTypeOther];
                         case EOPNOTSUPP:
                         case ENOTCONN:
                             break;
                         default:
                             _throwErrnumException(env, errnum, fd);
                             return NULL;
                     }
                 }
                 break;
             default:
                 _throwErrnumException(env, errnum, fd);
                 return NULL;
         }
     }
#endif

     ret = getsockopt(handle, SOL_SOCKET, SO_TYPE,
#if defined(_WIN32)
                    (char*)
#endif
                      &type, &typeLen);

     if(ret != 0) {
         _throwErrnumException(env, socket_errno, fd);
         return NULL;
     }

     // FIXME: check protocol?

     switch(addr->sa_family) {
         case AF_UNIX:
             switch(type) {
                 case SOCK_STREAM:
                     return kFDTypeClasses[kFDTypeAFUNIXStreamSocket];
                 case SOCK_DGRAM:
                     return kFDTypeClasses[kFDTypeAFUNIXDatagramSocket];
                 default:
                     return kFDTypeClasses[kFDTypeOtherSocket];
             }
#if junixsocket_have_tipc
         case AF_TIPC:
             switch(type) {
                 case SOCK_STREAM:
                     return kFDTypeClasses[kFDTypeAFTIPCStreamSocket];
                 case SOCK_DGRAM:
                     return kFDTypeClasses[kFDTypeAFTIPCDatagramSocket];
                 default:
                     return kFDTypeClasses[kFDTypeOtherSocket];
             }
#endif
#if junixsocket_have_vsock
         case AF_VSOCK:
             switch(type) {
                 case SOCK_STREAM:
                     return kFDTypeClasses[kFDTypeAFVSOCKStreamSocket];
                 case SOCK_DGRAM:
                     return kFDTypeClasses[kFDTypeAFVSOCKDatagramSocket];
                 default:
                     return kFDTypeClasses[kFDTypeOtherSocket];
             }
#endif
#if junixsocket_have_system
         case AF_SYSTEM:
             switch(type) {
                 case SOCK_STREAM:
                     return kFDTypeClasses[kFDTypeAFSYSTEMStreamSocket];
                 case SOCK_DGRAM:
                     return kFDTypeClasses[kFDTypeAFSYSTEMDatagramSocket];
                 default:
                     return kFDTypeClasses[kFDTypeAFSYSTEMDatagramSocket];
             }
#endif
         default:
             switch(type) {
                 case SOCK_STREAM:
                     return kFDTypeClasses[kFDTypeOtherStreamSocket];
                 case SOCK_DGRAM:
                     return kFDTypeClasses[kFDTypeOtherDatagramSocket];
                 default:
                     return kFDTypeClasses[kFDTypeOtherSocket];
             }
     }
 }

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    copyFileDescriptor
 * Signature: (Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_copyFileDescriptor
(JNIEnv *env, jclass clazz CK_UNUSED, jobject source, jobject target)
{
    _initFD(env, target, _getFD(env, source));
#if defined(_WIN32)
    _initHandle(env, target, (jlong)_getHandle(env, source));
#endif
}

JNIEXPORT jobject JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_initRedirect
(JNIEnv *env, jclass clazz CK_UNUSED, jobject fdesc)
{
    if(fdesc == NULL) {
        _throwException(env, kExceptionNullPointerException, "fdesc");
        return NULL;
    }

    if(kRedirectImplConstructor == NULL) {
        return NULL;
    }

    jobject redirect = (*env)->NewObject(env, kRedirectImplClass, kRedirectImplConstructor);
    setObjectFieldValue(env, redirect, "fd", "Ljava/io/FileDescriptor;", fdesc);
    return redirect;
}

jboolean supportsCastAsRedirect(void) {
#if defined(_WIN32)
    return JNI_FALSE;
#else
    return kRedirectImplConstructor != NULL;
#endif
}
