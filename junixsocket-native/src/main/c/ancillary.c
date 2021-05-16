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

#include "ancillary.h"

#if defined(junixsocket_have_ancillary)

#if JUNIXSOCKET_HARDEN_CMSG_NXTHDR
struct cmsghdr* junixsocket_CMSG_NXTHDR (struct msghdr *mhdr, struct cmsghdr *cmsg)
{
    if((size_t)cmsg->cmsg_len >= sizeof(struct cmsghdr)) {
        CK_IGNORE_CAST_ALIGN_BEGIN // false positive: CMSG_ALIGN ensures alignment
        cmsg = (struct cmsghdr*)((unsigned char*) cmsg + CMSG_ALIGN (cmsg->cmsg_len));
        if((unsigned char*)cmsg < ((unsigned char*) mhdr->msg_control + mhdr->msg_controllen)) {
            CK_IGNORE_SIGN_COMPARE_BEGIN
            return CMSG_NXTHDR(mhdr, cmsg);
            CK_IGNORE_SIGN_COMPARE_END
        }
        CK_IGNORE_CAST_ALIGN_END
    }
    return NULL;
}
#endif

#endif
