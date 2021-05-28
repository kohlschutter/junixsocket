//
//  socketpair.c
//  junixsocket-native
//
//  Created by Christian Kohlschütter on 5/27/21.
//

#include "config.h"
#include "socketpair.h"

#include "exceptions.h"
#include "socket.h"
#include "filedescriptors.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    socketPair
 * Signature: (ILjava/io/FileDescriptor;Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_socketPair
(JNIEnv *env, jclass clazz CK_UNUSED, jint type, jobject fd1, jobject fd2) {
    type = sockTypeToNative(env, type);
    if(type == -1) {
        return;
    }

#if defined(_WIN32)
    CK_ARGUMENT_POTENTIALLY_UNUSED(fd1);
    CK_ARGUMENT_POTENTIALLY_UNUSED(fd2);
    _throwException(env, kExceptionSocketException, "unsupported");
    return;
#else
    int socket_vector[2];

    int ret = socketpair(AF_UNIX, type, 0, socket_vector);
    if(ret == -1) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }

    _initFD(env, fd1, socket_vector[0]);
    _initFD(env, fd2, socket_vector[1]);
#endif
}
