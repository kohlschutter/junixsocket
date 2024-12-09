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
    if(len == 0) {
        return NULL;
    }

    jbyteArray buf = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, buf, 0, len, (jbyte*)&(req.node_id));

    return buf;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    tipcGetLinkName
 * Signature: (II)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_tipcGetLinkName
(JNIEnv *env, jclass clazz CK_UNUSED, jint peer, jint bearerId) {
    int fd = newTipcRDMSocket();
    if(fd <= 0) {
        return NULL;
    }

    // At one point, TIPC_MAX_LINK_NAME got changed from 60 to 68
    // Require a minimum size so we don't crash
    size_t reqSize = MAX(76, sizeof(struct tipc_sioc_ln_req));
    struct tipc_sioc_ln_req *req = calloc(1, reqSize);
    if(req == NULL) {
        return NULL;
    }
    req->peer = peer;
    req->bearer_id = bearerId;

    int ret = ioctl(fd, SIOCGETLINKNAME, req);
    if(ret < 0) {
        free(req);
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

    jsize len = (jsize)strnlen(req->linkname, TIPC_MAX_LINK_NAME);
    if(len == 0) {
        free(req);
        return NULL;
    }

    jbyteArray buf = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, buf, 0, len, (jbyte*)&(req->linkname));
    free(req);

    return buf;
}

#endif
