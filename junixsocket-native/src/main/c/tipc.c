//
//  tipc.c
//  junixsocket-native
//
//  Created by Christian Kohlschütter
//

#include "tipc.h"

#include "exceptions.h"

#if junixsocket_have_tipc

#if !defined(SIOCGETLINKNAME)
#define SIOCGETLINKNAME        SIOCPROTOPRIVATE
#define TIPC_MAX_LINK_NAME 68
struct tipc_sioc_ln_req {
    __u32 peer;
    __u32 bearer_id;
    char linkname[TIPC_MAX_LINK_NAME];
};
#endif

#if !defined(SIOCGETNODEID)
#define SIOCGETNODEID          (SIOCPROTOPRIVATE + 1)
#define TIPC_NODEID_LEN 16
struct tipc_sioc_nodeid_req {
    __u32 peer;
    char node_id[TIPC_NODEID_LEN];
};
#endif

static int newTipcRDMSocket(void) {
#if defined(junixsocket_have_socket_cloexec)
    int fd = socket(AF_TIPC, SOCK_RDM | SOCK_CLOEXEC, 0);
    if(fd == -1 && errno == EPROTONOSUPPORT) {
        fd = socket(AF_TIPC, SOCK_RDM, 0);
    }
#else
    int fd = socket(AF_TIPC, SOCK_RDM, 0);
#endif
    return fd;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    tipcGetNodeId
 * Signature: (I)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_tipcGetNodeId
(JNIEnv *env, jclass clazz CK_UNUSED, jint peer)
{
    struct tipc_sioc_nodeid_req req = {
        .peer = peer
    };

    int fd = newTipcRDMSocket();
    if(fd <= 0) {
        return NULL;
    }
    int ret = ioctl(fd, SIOCGETNODEID, &req);
    if(ret < 0) {
        int errnum = socket_errno;
        close(fd);
        if(errnum == ENOTTY) {
            // kernel does not implement ioctl
        } else {
            _throwErrnumException(env, errnum, NULL);
        }
        return NULL;
    }
    close(fd);

    jsize len = (jsize)strnlen(req.node_id, TIPC_NODEID_LEN);
    jbyteArray buf = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, buf, 0, len, (jbyte*)&(req.node_id));

    return buf;}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    tipcGetLinkName
 * Signature: (II)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_tipcGetLinkName
(JNIEnv *env, jclass clazz CK_UNUSED, jint peer, jint bearerId) {
    struct tipc_sioc_ln_req req = {
        .peer = peer,
        .bearer_id = bearerId
    };

    int fd = newTipcRDMSocket();
    if(fd <= 0) {
        return NULL;
    }
    int ret = ioctl(fd, SIOCGETLINKNAME, &req);
    if(ret < 0) {
        int errnum = socket_errno;
        close(fd);
        if(errnum == ENOTTY) {
            // kernel does not implement ioctl
        } else {
            _throwErrnumException(env, errnum, NULL);
        }
        return NULL;
    }
    close(fd);

    jsize len = (jsize)strnlen(req.linkname, TIPC_MAX_LINK_NAME);
    jbyteArray buf = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, buf, 0, len, (jbyte*)&(req.linkname));

    return buf;
}

#endif
