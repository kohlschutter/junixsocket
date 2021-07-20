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

static jclass class_FileDescriptor = NULL;
static jfieldID fieldID_fd = NULL;

typedef enum {
    kFDTypeOther = 0,
    kFDTypeOtherSocket,
    kFDTypeOtherStreamSocket,
    kFDTypeOtherDatagramSocket,
    kFDTypeAFUNIXStreamSocket,
    kFDTypeAFUNIXDatagramSocket,
    kFDTypeMaxExcl
} FileDescriptorType;

// NOTE: The exceptions must all be either inherit from IOException or RuntimeException/Error
static char *kFDTypeClassNames[kFDTypeMaxExcl] = {
    "java/io/FileDescriptor",
    "java/io/FileDescriptor",
    "java/net/Socket",
    "java/net/DatagramSocket",
    "org/newsclub/net/unix/AFUNIXSocket",
    "org/newsclub/net/unix/AFUNIXDatagramSocket",
};

static jclass *kFDTypeClasses;

void init_filedescriptors(JNIEnv *env) {
    kFDTypeClasses = malloc(sizeof(jclass) * kFDTypeMaxExcl);
    for(int i=0; i<kFDTypeMaxExcl; i++) {
        kFDTypeClasses[i] = findClassAndGlobalRef(env, kFDTypeClassNames[i]);
    }

    class_FileDescriptor = kFDTypeClasses[0];
    fieldID_fd = (*env)->GetFieldID(env, class_FileDescriptor, "fd", "I");
}

void destroy_filedescriptors(JNIEnv *env) {
    for(int i=0; i<kFDTypeMaxExcl; i++) {
        releaseClassGlobalRef(env, kFDTypeClasses[i]);
    }
    fieldID_fd = NULL;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    getFD
 * Signature: (Ljava/io/FileDescriptor;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocketunwrap
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd)
{
    return _getFD(env, fd);
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

int _getFD(JNIEnv * env, jobject fd)
{
    return (*env)->GetIntField(env, fd, fieldID_fd);
}

void _initFD(JNIEnv * env, jobject fd, int handle)
{
    (*env)->SetIntField(env, fd, fieldID_fd, handle);
}

// Close a file descriptor. fd object and numeric handle must either be identical,
// or only one of them be valid.
//
// fd objects are marked closed by setting their fd value to -1.
int _closeFd(JNIEnv * env, jobject fd, int handle)
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
    int fdHandle = _getFD(env, fd);
    _initFD(env, fd, -1);
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
        if(errnum == ENOTCONN) {
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
#if defined(_WIN32)
    CK_ARGUMENT_POTENTIALLY_UNUSED(handle);
    return errnum == 0 || errnum == WSAEWOULDBLOCK || errnum == 232 /* named pipes may return this? */;
#else
    if (errnum == EWOULDBLOCK || errnum == EAGAIN || errnum == EINPROGRESS) {
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
     int handle = (fd == NULL) ? -1 : _getFD(env, fd);
     if(handle <= 0) {
         return NULL;
     }

     struct sockaddr_un addrUn = {0};
     struct sockaddr *addr = (struct sockaddr *)&addrUn;
     socklen_t len = sizeof(struct sockaddr_un);

     int ret;

     ret = getsockname(handle, addr, &len);
     if(ret != 0) {
         int errnum = socket_errno;
         if(errnum == ENOTSOCK) {
             return kFDTypeClasses[kFDTypeOther];
         }
         _throwErrnumException(env, errnum, fd);
         return NULL;
     }

     int type = -1;
     socklen_t typeLen = sizeof(type);
     ret = getsockopt(handle, SOL_SOCKET, SO_TYPE, &type, &typeLen);
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
