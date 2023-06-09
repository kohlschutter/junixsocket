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
#  include <sys/sysctl.h>
#endif

#if defined(__sun) || defined(__sun__)
#  include <ucred.h>
#endif

#include "exceptions.h"
#include "filedescriptors.h"
#include "jniutil.h"

#include "init.h"

#if defined(LOCAL_PEEREPID)

static int ucredFromPid(pid_t pid, struct xucred* cr)
{
    if(cr == NULL) {
        errno = EINVAL;
        return -1;
    }
    struct kinfo_proc process = {0};
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

    gid_t *groups = process.kp_eproc.e_ucred.cr_groups;
    for(int i=cr->cr_ngroups-1;i>=0;i--) {
        cr->cr_groups[i] = groups[i];
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

#if defined(__NetBSD__) || defined(SO_PEERCRED) || defined(SO_PEERID) || defined(SIO_AF_UNIX_GETPEERPID)
static void initUidGid(JNIEnv *env, jobject creds, jint pid, jint uid, jint gid) {
    jlongArray gidArray;

    if(gid == -1) {
        gidArray = NULL;
    } else {
        gidArray = (*env)->NewLongArray(env, 1);
        jlong *gids = (*env)->GetLongArrayElements(env, gidArray, 0);
        gids[0] = gid;
        (*env)->ReleaseLongArrayElements(env, gidArray, gids, 0);
    }

    setLongFieldValue(env, creds, "uid", uid);
    setLongFieldValue(env, creds, "pid", pid);
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

#if defined(LOCAL_PEERCRED) || defined(LOCAL_PEEREPID) || defined(LOCAL_PEEREUUID) || \
    defined(SO_PEERCRED) || defined(SO_PEERID) || defined(__NetBSD__) || defined(__sun) || defined(__sun__) || defined(SIO_AF_UNIX_GETPEERPID)
    int fd = _getFD(env, fdesc);

    if(!supportsUNIX()) {
        return NULL;
    }

    jboolean peerCredOK = true;
    CK_ARGUMENT_POTENTIALLY_UNUSED(peerCredOK);

#  if defined(__sun) || defined(__sun__)
    {
        ucred_t *uc = NULL;
        if (getpeerucred(fd, &uc) == -1) {
            return NULL;
        }

        pid_t pid = ucred_getpid(uc);
        uid_t uid = ucred_geteuid(uc);
        gid_t gid = ucred_getegid(uc);

        setLongFieldValue(env, creds, "pid", pid);
        setLongFieldValue(env, creds, "uid", uid);

        const gid_t *groups = NULL;
        int nGroups = 0;
        int gidIndex = -1;
        if((nGroups = ucred_getgroups(uc, &groups)) > 0) {
            for (int i=0;i<nGroups;i++) {
                if(groups[i] == gid) {
                    gidIndex = i;
                    break;
                }
            }
        }

        // NOTE: We could add support for zoneIds, project Ids, bs labels, etc.
        // just like we did for the macOS-only UUID property. However, I think this is
        // a super-niche requirement. Please let me know if you need this functionality.

        if (nGroups == 0) {
            nGroups = 1;
            groups = &gid;
            gidIndex = 0;
        } else if (gidIndex == -1) {
            nGroups += 1;
        }

        jlongArray gidArray = (*env)->NewLongArray(env, nGroups);
        jlong *gids = (*env)->GetLongArrayElements(env, gidArray, 0);
        if (gidIndex == -1) {
            gids[0] = gid;
            for (int i=0;i<nGroups;i++) {
                gids[i + 1] = groups[i];
            }
        } else {
            for (int i=0;i<nGroups;i++) {
                gids[i] = groups[i];
            }
        }
        (*env)->ReleaseLongArrayElements(env, gidArray, gids, 0);

        setObjectFieldValue(env, creds, "gids", "[J", gidArray);

        ucred_free(uc);
    }
#  elif defined(LOCAL_PEERCRED)
    struct xucred cr = {};
    {
        socklen_t len = sizeof(cr);

        // NOTE: macOS may fail here for datagrams ...
        // see below how we recover from that via ucredFromPid
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEERCRED, &cr, &len) < 0) {
            if(socket_errno != EINVAL && errno != EOPNOTSUPP
#if defined(__DragonFly__)
               // sockets created with socketpair may not support LOCAL_PEERCRED
               // https://bugs.freebsd.org/bugzilla/show_bug.cgi?id=176419
               && errno != ENOTCONN
#endif
               ) {
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
#  elif(__NetBSD__) // unless _NETBSD_SOURCE is defined
    jboolean peereidFallback = true;
#    if defined(LOCAL_PEEREID)
    {
        struct unpcbid unp;
        socklen_t unpLen = sizeof(struct unpcbid);
        if(getsockopt(fd, SOL_LOCAL, LOCAL_PEEREID, &unp, &unpLen) != -1) {
            peereidFallback = false;
            initUidGid(env, creds, unp.unp_pid, unp.unp_euid, unp.unp_egid);
        }
    }
#    endif
    if(peereidFallback) {
        uid_t euid = 0;
        gid_t egid = 0;
        int ret = getpeereid(fd, &euid, &egid);
        if(ret == 0) {
            initUidGid(env, creds, -1, euid, egid);
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
#  if defined(SO_PEERCRED) && defined(__linux__)
    {
        struct ucred cr;
        socklen_t len = sizeof(cr);
        if(getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cr, &len) != 0) {
            if(socket_errno != EINVAL && errno != EOPNOTSUPP) {
                _throwErrnumException(env, socket_errno, NULL);
                return NULL;
            }
        } else if((jint)cr.uid == -1 && (jint)cr.gid == -1 && cr.pid == 0) {
            // On Linux, getting peer credentials from datagram sockets may fail
            // without actually returning -1 on getsockopt.
            return NULL;
        } else {
            initUidGid(env, creds, (jint)cr.pid, (jint)cr.uid, (jint)cr.gid);
        }
    }
#  elif defined(SO_PEERCRED) && defined(__BSD_VISIBLE)
    {
        struct sockpeercred cr;
        socklen_t len = sizeof(cr);
        if(getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cr, &len) != 0) {
            if(socket_errno != EINVAL && errno != EOPNOTSUPP) {
                if(errno == ENOTCONN) {
                    int type;
                    socklen_t typeLen = sizeof(type);
                    if(getsockopt(fd, SOL_SOCKET, SO_TYPE, &type, &typeLen) == 0 && type == SOCK_DGRAM) {
                        // This is OpenBSD's way of saying:
                        // "I can't get credentials on non-connected datagram sockets"
                    } else {
                        _throwErrnumException(env, ENOTCONN, NULL);
                        return NULL;
                    }
                } else {
                    _throwErrnumException(env, socket_errno, NULL);
                    return NULL;
                }
            }
        } else if((jint)cr.uid == -1 && (jint)cr.gid == -1 && cr.pid == 0) {
            return NULL;
        } else {
            initUidGid(env, creds, (jint)cr.pid, (jint)cr.uid, (jint)cr.gid);
        }
    }
#  elif defined(SO_PEERID)
    {
        struct peercred_struct cr;
        socklen_t len = sizeof(cr);
        if(getsockopt(fd, SOL_SOCKET, SO_PEERID, &cr, &len) != 0) {
            if(socket_errno == EINVAL || errno == EOPNOTSUPP || errno == ENOPROTOOPT) {
                return NULL;
            } else {
                _throwErrnumException(env, socket_errno, NULL);
                return NULL;
            }
        } else {
            initUidGid(env, creds, (jint)cr.pid, (jint)cr.euid, (jint)cr.egid);
        }
    }
#  elif defined(SIO_AF_UNIX_GETPEERPID)
    {
        u_long pid = -1;
        int ret = ioctlsocket(fd, SIO_AF_UNIX_GETPEERPID, &pid);
        if(ret != 0 && pid > 0) {
            return NULL;
        }
        initUidGid(env, creds, (jint)pid, -1, -1);
    }
#  endif

#endif
    return creds;
}
