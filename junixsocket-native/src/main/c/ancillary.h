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

#ifndef ancillary_h
#define ancillary_h

#include "config.h"

#if defined(junixsocket_have_ancillary)

#if JUNIXSOCKET_HARDEN_CMSG_NXTHDR
struct cmsghdr* junixsocket_CMSG_NXTHDR (struct msghdr *mhdr, struct cmsghdr *cmsg);
#else
#  define junixsocket_CMSG_NXTHDR CMSG_NXTHDR
#endif

void init_ancillary(JNIEnv *env);
void destroy_ancillary(JNIEnv *env);

jfieldID getFieldID_ancillaryReceiveBuffer(void);
jfieldID getFieldID_pendingFileDescriptors(void);
jmethodID getMethodID_setTipcErrorInfo(void);
jmethodID getMethodID_setTipcDestName(void);

#endif

#endif /* ancillary_h */
