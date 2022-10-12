//
//  vsock.c
//  junixsocket-native
//
//  Created by Christian Kohlschütter
//

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

    local_cid = (ret == 0 ? cid : VMADDR_CID_HOST);
    return local_cid;
#  else
    // get local CID via ioctl from /dev/vsock device
    int fd = open("/dev/vsock", 0);
    if(fd < 0) {
        local_cid = VMADDR_CID_HOST;
        return local_cid; // access denied, etc.
    }
    ioctl(fd, IOCTL_VM_SOCKETS_GET_LOCAL_CID, &cid);
    close(fd);
    local_cid = cid;
    return local_cid;
#  endif
#else
    return 2; // VMADDR_CID_HOST;
#endif
}
