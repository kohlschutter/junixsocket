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
#include "address.h"
#include "init.h"

#if defined(_WIN32)
typedef int socklen_t;
#endif

#if defined(_WIN32) || junixsocket_have_vsock
#  if !defined(_WIN32)
#    define closesocket close
#  endif
static int simulateSocketPair(JNIEnv *env, int domain, int type, jobject fd1, jobject fd2, jux_sockaddr_t *addr, socklen_t addrLen) {
    int handleListen = socket(domain, type, 0);
    if(handleListen < 0) {
        errno = socket_errno;
        return -1;
    }

    int ret;

    fixupSocketAddress(handleListen, addr, addrLen);
    ret = bind(handleListen, (struct sockaddr*)addr, addrLen);
    if(ret != 0 && fixupSocketAddressPostError(handleListen, addr, addrLen, socket_errno)) {
        ret = bind(handleListen, (struct sockaddr*)addr, addrLen);
    }
    if(ret != 0) {
        errno = socket_errno;
        return -1;
    }
    ret = listen(handleListen, 1);
    if(ret != 0) {
        errno = socket_errno;
        return -1;
    }

    socklen_t len = addrLen;
    ret = getsockname(handleListen, (struct sockaddr *)addr, &len);
    if(ret != 0) {
        errno = socket_errno;
        return -1;
    }

    int handleConnect = socket(domain, type, 0);
    if(handleConnect < 0) {
        int errnum = socket_errno;
        closesocket(handleListen);
        errno = errnum;
        return -1;
    }

#if defined(_WIN32)
    u_long mode = 1;
    if(ioctlsocket(handleConnect, FIONBIO, &mode) != NO_ERROR) {
        int errnum = socket_errno;
        closesocket(handleListen);
        closesocket(handleConnect);
        errno = errnum;
        return -1;
    }
#endif

    ret = connect(handleConnect, (struct sockaddr*)addr, addrLen);
    if(ret != 0 && socket_errno != EWOULDBLOCK) {
        errno = socket_errno;
        return -1;
    }

    len = addrLen;
    int handleAccept = accept(handleListen, (struct sockaddr *)addr, &len);
    if(handleAccept < 0) {
        errno = socket_errno;
        return -1;
    }

    closesocket(handleListen);

#if defined(_WIN32)
    mode = 0;
    if(ioctlsocket(handleConnect, FIONBIO, &mode) != NO_ERROR) {
        int errnum = socket_errno;
        closesocket(handleAccept);
        closesocket(handleConnect);
        errno = errnum;
        return -1;
    }
#endif

    _initFD(env, fd1, handleAccept);
    _initFD(env, fd2, handleConnect);
    return 0;
}
#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    socketPair
 * Signature: (IILjava/io/FileDescriptor;Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_socketPair
(JNIEnv *env, jclass clazz CK_UNUSED, jint domain, jint type, jobject fd1, jobject fd2) {
    domain = domainToNative(domain);
    if(domain == -1) {
        _throwException(env, kExceptionSocketException, "Unsupported domain");
        return;
    }

    type = sockTypeToNative(env, type);
    if(type == -1) {
        return;
    }

    int ret;
    
#if defined(_WIN32)
    struct sockaddr_in addr = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(0x7F000001), // loopback
        .sin_port = 0
    };
    ret = simulateSocketPair(env, AF_INET, type, fd1, fd2, (jux_sockaddr_t*)&addr, sizeof(struct sockaddr_in));
    if(ret == -1) {
        _throwErrnumException(env, errno, NULL);
        return;
    }
#else
    int socket_vector[2];
#if defined(junixsocket_have_socket_cloexec)
    if(supportsUNIX()) {
        ret = socketpair(domain, type, SOCK_CLOEXEC, socket_vector);
        if(ret == -1 && errno == EPROTONOSUPPORT) {
            ret = socketpair(domain, type, 0, socket_vector);
            if(ret == 0) {
#    if defined(FD_CLOEXEC)
                fcntl(socket_vector[0], F_SETFD, FD_CLOEXEC); // best effort
                fcntl(socket_vector[1], F_SETFD, FD_CLOEXEC); // best effort
#    endif
            }
        }
    } else {
        // workaround for OSv assert(proto == 0);
        ret = socketpair(domain, type, 0, socket_vector);
    }
#else
    ret = socketpair(domain, type, 0, socket_vector);
#endif

    if(ret == -1) {
        int myerr = socket_errno;
        if(myerr == EOPNOTSUPP) {
#if junixsocket_have_vsock
            if(domain == AF_VSOCK) {
                struct sockaddr_vm addr = {
#  if defined(junixsocket_have_sun_len)
                    .svm_len = sizeof(struct sockaddr_vm),
#  endif
                    .svm_reserved1 = 0,
                    .svm_family = AF_VSOCK,
                    .svm_port = VMADDR_PORT_ANY,
#  if defined(VMADDR_CID_LOCAL)
                    .svm_cid = VMADDR_CID_LOCAL
#  else
                        .svm_cid = VMADDR_CID_RESERVED
#  endif
                };
CK_IGNORE_CAST_ALIGN_BEGIN
                if(simulateSocketPair(env, domain, type, fd1, fd2, (jux_sockaddr_t*)&addr,
                                      sizeof(struct sockaddr_vm)) != 0) {
                    _throwErrnumException(env, myerr, NULL);
                    return;
                }
CK_IGNORE_CAST_ALIGN_END
                return;
            }
#endif
        }

        _throwErrnumException(env, myerr, NULL);
        return;
    }

    _initFD(env, fd1, socket_vector[0]);
    _initFD(env, fd2, socket_vector[1]);

#endif

}
