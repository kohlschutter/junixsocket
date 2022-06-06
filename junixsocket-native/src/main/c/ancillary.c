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

#include "jniutil.h"

#if defined(junixsocket_have_ancillary)

#if JUNIXSOCKET_HARDEN_CMSG_NXTHDR

#if !defined(CMSG_ALIGN)
#  define CMSG_ALIGN(n) _ALIGN(n)
#endif

struct cmsghdr* junixsocket_CMSG_NXTHDR (struct msghdr *mhdr, struct cmsghdr *cmsg)
{
    if((size_t)cmsg->cmsg_len >= sizeof(struct cmsghdr)) {
        CK_IGNORE_CAST_ALIGN_BEGIN // false positive: CMSG_ALIGN ensures alignment
        cmsg = (struct cmsghdr*)((unsigned char*) cmsg + CMSG_ALIGN (cmsg->cmsg_len));
        if((unsigned char*)cmsg < ((unsigned char*) mhdr->msg_control + mhdr->msg_controllen)) {
#ifdef __linux__
            // already aligned; avoid referencing __cmsg_nxthdr, which does the same thing
            // but may not be present on non-glibc platforms
            return cmsg;
#else
            CK_IGNORE_SIGN_COMPARE_BEGIN
            return CMSG_NXTHDR(mhdr, cmsg);
            CK_IGNORE_SIGN_COMPARE_END
#endif
        }
        CK_IGNORE_CAST_ALIGN_END
    }
    return NULL;
}
#endif

static jclass class_AncillaryDataSupport = NULL;
static jfieldID fieldID_ancillaryReceiveBuffer = NULL;
static jfieldID fieldID_pendingFileDescriptors = NULL;

static jmethodID kSetTipcErrorInfo = NULL;
static jmethodID kSetTipcDestName = NULL;

jfieldID getFieldID_ancillaryReceiveBuffer(void) {
    return fieldID_ancillaryReceiveBuffer;
}
jfieldID getFieldID_pendingFileDescriptors(void) {
    return fieldID_pendingFileDescriptors;
}
jmethodID getMethodID_setTipcErrorInfo(void) {
    return kSetTipcErrorInfo;
}
jmethodID getMethodID_setTipcDestName(void) {
    return kSetTipcDestName;
}

void init_ancillary(JNIEnv *env) {
    class_AncillaryDataSupport = findClassAndGlobalRef(env, "org/newsclub/net/unix/AncillaryDataSupport");
    fieldID_ancillaryReceiveBuffer = (*env)->GetFieldID(env, class_AncillaryDataSupport, "ancillaryReceiveBuffer", "Ljava/nio/ByteBuffer;");
    fieldID_pendingFileDescriptors = (*env)->GetFieldID(env, class_AncillaryDataSupport, "pendingFileDescriptors", "[I");

    kSetTipcErrorInfo = (*env)->GetMethodID(env, class_AncillaryDataSupport, "setTipcErrorInfo", "(II)V");
    kSetTipcDestName = (*env)->GetMethodID(env, class_AncillaryDataSupport, "setTipcDestName", "(III)V");
}
void destroy_ancillary(JNIEnv *env) {
    releaseClassGlobalRef(env, class_AncillaryDataSupport);
    fieldID_ancillaryReceiveBuffer = NULL;
    fieldID_pendingFileDescriptors = NULL;
    kSetTipcErrorInfo = NULL;
    kSetTipcDestName = NULL;
}

#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    ancillaryBufMinLen
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_ancillaryBufMinLen
 (JNIEnv *env CK_UNUSED, jclass klazz CK_UNUSED) {
#if defined(junixsocket_have_ancillary)
    return sizeof(struct cmsghdr);
#else
    return 0;
#endif
}
