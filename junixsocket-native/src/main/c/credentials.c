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

#if defined(LOCAL_PEEREPID)
#include <sys/sysctl.h>
#endif

#include "exceptions.h"
#include "filedescriptors.h"
#include "jniutil.h"

#if defined(LOCAL_PEEREPID)

static int ucredFromPid(pid_t pid, struct xucred* cr)
{
    if(cr == NULL) {
        errno = EINVAL;
        return -1;
    }
    struct kinfo_proc process = {};
    size_t bufSize = sizeof(struct kinfo_proc);

    int path[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, pid};
    int ret = sysctl(path, 4, &process, &bufSize, NULL, 0);

    if (ret == 0 && bufSize == 0)
    {
        errno = ESRCH;
        return -1;
    }

    cr->cr_uid = process.kp_eproc.e_ucred.cr_uid;
    cr->cr_ngroups = process.kp_eproc.e_ucred.cr_ngroups;
    for(int i=cr->cr_ngroups-1;i>=0;i--) {
        cr->cr_groups[i] =process.kp_eproc.e_ucred.cr_groups[i];
    }

    return ret;
}
#endif

#if defined(LOCAL_PEERCRED)

static void initUidGidFromXucred(JNIEnv *env, jobject creds, struct xucred *cr) {
    jlongArray gidArray = (*env)->NewLongArray(env, cr->cr_ngroups);
    jlong *gids = (*env)->GetLongArrayElements(env, gidArray, 0);
    for (int i=0,n=cr->cr_ngroups;i<n;i++) {
        gids[i] = (jlong)cr->cr_groups[i];
    }
    (*env)->ReleaseLongArrayElements(env, gidArray, gids, 0);

    setLongFieldValue(env, creds, "uid", cr->cr_uid);
    setObjectFieldValue(env, creds, "gids", "[J", gidArray);
}

#endif

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

    jboolean peerCredOK = true;
    CK_ARGUMENT_POTENTIALLY_UNUSED(peerCredOK);

#  if defined(LOCAL_PEERCRED)
    struct xucred cr = {};
    {
        socklen_t len = sizeof(cr);

        // NOTE: macOS may fail here for datagrams ...
        // see below how we recover from that via ucredFromPid
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEERCRED, &cr, &len) < 0) {
            if(socket_errno != EINVAL && errno != EOPNOTSUPP) {
                _throwErrnumException(env, errno, NULL);
                return NULL;
            }

            // If LOCAL_PEERCRED fails for any reason, we can use getpeereid,
            // however that only returns the primary gid.

            uid_t euid = 0;
            gid_t egid = 0;
            int ret = getpeereid(fd, &euid, &egid);
            if(ret == 0) {
                cr.cr_uid = euid;
                cr.cr_ngroups = 1;
                cr.cr_groups[0] = egid;
            } else {
                peerCredOK = false;
            }
        }

        if(peerCredOK) {
            initUidGidFromXucred(env, creds, &cr);
        }
    }
#  endif
#  if defined(LOCAL_PEEREPID)
    {
        pid_t pid = (pid_t) -1;
        socklen_t len = sizeof(pid);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEEREPID, &pid, &len) < 0) {
            if(socket_errno != EINVAL && errno != EOPNOTSUPP) {
                _throwErrnumException(env, socket_errno, NULL);
                return NULL;
            }
        } else {
#    if defined(LOCAL_PEERCRED)
            if(!peerCredOK) {
                // so we didn't get the credentials directly...
                // However, now that we've got the PID, we can use
                // sysctl to retrieve the data and copy it over.
                // We only need to ensure that the PID is still valid
                // after we receive the data.
                pid_t pidOrig = pid;
                pid = -1;
                int ucredRes = ucredFromPid(pidOrig, &cr);
                int getoptRes = getsockopt(fd, SOL_LOCAL, LOCAL_PEEREPID, &pid, &len);
                if(ucredRes == 0 && getoptRes == 0 && pidOrig == pid) {
                    initUidGidFromXucred(env, creds, &cr);
                }
            }
#    endif
            setLongFieldValue(env, creds, "pid", (jlong)pid);
        }
    }
#  endif
#  if defined(LOCAL_PEEREUUID)
    {
        uuid_t uuid;
        socklen_t len = sizeof(uuid);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEEREUUID, &uuid, &len) < 0) {
            if(socket_errno != EINVAL && errno != EOPNOTSUPP) {
                _throwErrnumException(env, socket_errno, NULL);
                return NULL;
            }
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
            if(socket_errno != EINVAL && errno != EOPNOTSUPP) {
                _throwErrnumException(env, socket_errno, NULL);
                return NULL;
            }
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
