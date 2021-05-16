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
#include "credentials.h"

#include "exceptions.h"
#include "filedescriptors.h"
#include "jniutil.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    peerCredentials
 * Signature: (Ljava/io/FileDescriptor;Lorg/newsclub/net/unix/AFUNIXSocketCredentials;)Lorg/newsclub/net/unix/AFUNIXSocketCredentials;
 */
JNIEXPORT jobject JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_peerCredentials(
                                                                                      JNIEnv *env, jclass clazz CK_UNUSED, jobject fdesc, jobject creds)
{
    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
    CK_ARGUMENT_POTENTIALLY_UNUSED(fdesc);

#if defined(LOCAL_PEERCRED) || defined(LOCAL_PEEREPID) || defined(LOCAL_PEEREUUID) ||defined(SO_PEERCRED)
    int fd = _getFD(env, fdesc);

#  if defined(LOCAL_PEERCRED)
    {
        struct xucred cr;
        socklen_t len = sizeof(cr);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEERCRED, &cr, &len) < 0) {
            _throwErrnumException(env, socket_errno, NULL);
            return NULL;
        } else {
            jlongArray gidArray = (*env)->NewLongArray(env, cr.cr_ngroups);
            jlong *gids = (*env)->GetLongArrayElements(env, gidArray, 0);
            for (int i=0,n=cr.cr_ngroups;i<n;i++) {
                gids[i] = (jlong)cr.cr_groups[i];
            }
            (*env)->ReleaseLongArrayElements(env, gidArray, gids, 0);

            setLongFieldValue(env, creds, "uid", cr.cr_uid);
            setObjectFieldValue(env, creds, "gids", "[J", gidArray);
        }
    }
#  endif
#  if defined(LOCAL_PEEREPID)
    {
        pid_t pid = (pid_t) -1;
        socklen_t len = sizeof(pid);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEEREPID, &pid, &len) < 0) {
            _throwErrnumException(env, socket_errno, NULL);
            return NULL;
        }
        setLongFieldValue(env, creds, "pid", (jlong)pid);
    }
#  endif
#  if defined(LOCAL_PEEREUUID)
    {
        uuid_t uuid;
        socklen_t len = sizeof(uuid);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEEREUUID, &uuid, &len) < 0) {
            _throwErrnumException(env, socket_errno, NULL);
            return NULL;
        } else {
            uuid_string_t uuidStr;
            uuid_unparse(uuid, uuidStr);

            jobject uuidString = (*env)->NewStringUTF(env, uuidStr);
            callObjectSetter(env, creds, "setUUID", "(Ljava/lang/String;)V", uuidString);
        }
    }
#  endif
#  if defined(SO_PEERCRED)
    {
        struct ucred cr;
        socklen_t len = sizeof(cr);
        if(getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cr, &len) < 0) {
            _throwErrnumException(env, socket_errno, NULL);
            return NULL;
        } else {
            jlongArray gidArray = (*env)->NewLongArray(env, 1);
            jlong *gids = (*env)->GetLongArrayElements(env, gidArray, 0);
            gids[0] = cr.gid;
            (*env)->ReleaseLongArrayElements(env, gidArray, gids, 0);

            setLongFieldValue(env, creds, "uid", cr.uid);
            setLongFieldValue(env, creds, "pid", cr.pid);
            setObjectFieldValue(env, creds, "gids", "[J", gidArray);
        }
    }
#  endif

#endif
    return creds;
}
