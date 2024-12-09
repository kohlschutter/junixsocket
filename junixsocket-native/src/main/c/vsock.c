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

#include "vsock.h"

#if junixsocket_have_vsock
static int local_cid = -1;
#endif

int vsock_get_local_cid(int sockFd) {
    CK_ARGUMENT_POTENTIALLY_UNUSED(sockFd);

#if junixsocket_have_vsock
    if(local_cid != -1) {
        return local_cid;
    }

    int cid = VMADDR_CID_HOST;
#  if defined(__MACH__)
    // get local CID via ioctl from socket
    int ret = ioctl(sockFd, IOCTL_VM_SOCKETS_GET_LOCAL_CID, &cid);

    local_cid = (ret == 0 ? cid : -1);
    return local_cid;
#  else
    // get local CID via ioctl from /dev/vsock device
    int fd = open("/dev/vsock", 0);
    if(fd < 0) {
        switch(errno) {
            case EACCES:
                // device present but inaccessible, assume host mode
                local_cid = VMADDR_CID_HOST;
                break;
            default:
                local_cid = -1;
                break;
        }
        return local_cid;
    }
    ioctl(fd, IOCTL_VM_SOCKETS_GET_LOCAL_CID, &cid);
    close(fd);
    local_cid = cid;
    return local_cid;
#  endif
#else
    return -1; // VMADDR_CID_ANY;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    vsockGetLocalCID
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_vsockGetLocalCID
 (JNIEnv *env CK_UNUSED, jclass klazz CK_UNUSED) {
    return vsock_get_local_cid(-1);
}
