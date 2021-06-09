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
//    CK_ARGUMENT_POTENTIALLY_UNUSED(fd1);
//    CK_ARGUMENT_POTENTIALLY_UNUSED(fd2);
//    _throwException(env, kExceptionSocketException, "unsupported");
//    return;

    int handleListen = socket(AF_INET, type, 0);
    if(handleListen <= 0) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }

    struct sockaddr_in addr = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(0x7F000001), // loopback
        .sin_port = 0
    };
    int ret;

    ret = bind(handleListen, (struct sockaddr*)&addr, sizeof(struct sockaddr_in));
    if(ret != 0) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }
    ret = listen(handleListen, 1);
    if(ret != 0) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }

    int len;
    len = sizeof(struct sockaddr_in);
    ret = getsockname(handleListen, (struct sockaddr *)&addr, &len);
    if(ret != 0) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }

    int handleConnect = socket(AF_INET, type, 0);
    if(handleConnect <= 0) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }

    u_long mode = 1;
    if(ioctlsocket(handleConnect, FIONBIO, &mode) != NO_ERROR) {
        int errnum = socket_errno;
        close(handleListen);
        close(handleConnect);
        _throwErrnumException(env, errnum, NULL);
        return;
    }

    ret = connect(handleConnect, (struct sockaddr*)&addr, sizeof(struct sockaddr_in));
    if(ret != 0 && socket_errno != EWOULDBLOCK) {
        _throwErrnumException(env, errno, NULL);
        return;
    }

    len = sizeof(struct sockaddr_in);
    int handleAccept = accept(handleListen, (struct sockaddr *)&addr, &len);
    if(handleAccept <= 0) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }

    close(handleListen);

    mode = 0;
    if(ioctlsocket(handleConnect, FIONBIO, &mode) != NO_ERROR) {
        int errnum = socket_errno;
        close(handleAccept);
        close(handleConnect);
        _throwErrnumException(env, errnum, NULL);
        return;
    }

    _initFD(env, fd1, handleAccept);
    _initFD(env, fd2, handleConnect);
#else
    int socket_vector[2];
    int ret;
#if defined(junixsocket_have_socket_cloexec)
    ret = socketpair(AF_UNIX, type, SOCK_CLOEXEC, socket_vector);
    if(ret == -1 && errno == EPROTONOSUPPORT) {
        ret = socketpair(AF_UNIX, type, 0, socket_vector);
        if(ret == 0) {
#  if defined(FD_CLOEXEC)
            fcntl(socket_vector[0], F_SETFD, FD_CLOEXEC); // best effort
            fcntl(socket_vector[1], F_SETFD, FD_CLOEXEC); // best effort
#  endif
        }
    }
#else
    ret = socketpair(AF_UNIX, type, 0, socket_vector);
#endif
    if(ret == -1) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }

    _initFD(env, fd1, socket_vector[0]);
    _initFD(env, fd2, socket_vector[1]);
#endif

}
