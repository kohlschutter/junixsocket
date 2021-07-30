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
#include "polling.h"

#include "exceptions.h"
#include "filedescriptors.h"
#include "jniutil.h"

static jclass class_PollFd = NULL;
static jfieldID fieldID_fds = NULL;
static jfieldID fieldID_ops = NULL;
static jfieldID fieldID_rops = NULL;

void init_poll(JNIEnv *env) {
    class_PollFd = findClassAndGlobalRef(env, "org/newsclub/net/unix/AFUNIXSelector$PollFd");
    fieldID_fds = (*env)->GetFieldID(env, class_PollFd, "fds", "[Ljava/io/FileDescriptor;");
    fieldID_ops = (*env)->GetFieldID(env, class_PollFd, "ops", "[I");
    fieldID_rops = (*env)->GetFieldID(env, class_PollFd, "rops", "[I");
}

void destroy_poll(JNIEnv *env) {
    releaseClassGlobalRef(env, class_PollFd);
    fieldID_fds = NULL;
    fieldID_ops = NULL;
    fieldID_rops = NULL;
}

static const int OP_READ = (1<<0);
static const int OP_WRITE = (1<<2);
static const int OP_CONNECT = (1<<3);
static const int OP_ACCEPT = (1<<4);

static int opToEvent(int op) {
    int event = 0;
    if((op & OP_READ) || (op & OP_ACCEPT)) {
        event |= POLLIN;
    }
    if((op & OP_WRITE) || (op & OP_CONNECT)) {
        event |= POLLOUT;
    }
    return event;
}

static int eventToOp(int event) {
    int op = 0;
    if((event & POLLIN)) {
        op |= (OP_READ | OP_ACCEPT); // will be masked accordingly later
    }
    if((event & POLLOUT)) {
        op |= (OP_WRITE | OP_CONNECT); // will be masked accordingly later
    }
    if((event & POLLHUP)) {
        // FIXME should we set op to 0?
    }
    return op;
}

#if defined(junixsocket_use_poll_for_accept) || defined(junixsocket_use_poll_for_read)

static uint64_t timespecToMillis(struct timespec* ts) {
    return (uint64_t)ts->tv_sec * 1000 + (uint64_t)ts->tv_nsec / 1000000;
}

/*
 * Waits until the connection is ready to read/accept.
 *
 * Returns -1 if an exception was thrown, 0 if a timeout occurred, 1 if ready.
 */
jint pollWithTimeout(JNIEnv * env, jobject fd, int handle, int timeout) {
#if defined(_WIN32)
    DWORD optVal;
#else
    struct timeval optVal;
#endif

    socklen_t optLen = sizeof(optVal);
    int ret = getsockopt(handle, SOL_SOCKET, SO_RCVTIMEO, WIN32_NEEDS_CHARP &optVal, &optLen);

    uint64_t millis = 0;
    if(ret != 0) {
        if(socket_errno == ENOTSOCK) {
#if defined(_WIN32)
            optVal = 0;
#else
            optVal.tv_sec = 0;
            optVal.tv_usec = 0;
#endif
        } else {
            _throwSockoptErrnumException(env, socket_errno, fd);
            return -1;
        }
    }
#if defined(_WIN32)
    if(optLen >= (socklen_t)sizeof(optVal)) {
        millis = optVal;
    }
#else
    if(optLen >= sizeof(optVal) && (optVal.tv_sec > 0 || optVal.tv_usec > 0)) {
        millis = ((uint64_t)optVal.tv_sec * 1000) + (uint64_t)(optVal.tv_usec / 1000);
    }
#endif

    if(timeout > 0 && millis < (uint64_t)timeout) {
        // Some platforms (Windows) may not support SO_TIMEOUT, so let's override the timeout with our own value
        millis = (uint64_t)timeout;
    }

    ret = pollWithMillis(handle, millis);
    if(ret == -1) {
        _throwErrnumException(env, errno, NULL);
    }
    return ret;
}

jint pollWithMillis(int handle, uint64_t millis) {
    if(millis == 0) {
#if defined(junixsocket_accept_infinite_timeout_workaround)
        int flags = fcntl(handle, F_GETFL);
        if(flags != -1 && (flags & O_NONBLOCK)) {
            return 1;
        }

        // On OpenBSD, it looks like we need to simulate "unlimited timeout" by this loop,
        // otherwise we won't be able to disconnect any pending accepts
        // (we could probably also just poll with -1)
        jint ret;
        do {
            ret = pollWithMillis(handle, INT_MAX);
        } while(ret == 0);
#else
        return 1;
#endif
    }

    if(millis > INT_MAX) {
        millis = INT_MAX;
    }
    struct pollfd pfd;
    pfd.fd = handle;
    pfd.events = (POLLIN);
    pfd.revents = 0;

    int millisRemaining = (int)millis;

    struct pollfd fds[] = {pfd};

    struct timespec timeStart;
    struct timespec timeEnd;

    if(clock_gettime(CLOCK_MONOTONIC, &timeEnd) == -1) {
        return -1;
    }

    int ret;

    while (millisRemaining > 0) {
        // FIXME: should this be in a loop to ensure the timeout condition is met?

        timeStart = timeEnd;

        int pollTime = millisRemaining;
#  if defined(junixsocket_use_poll_interval_millis)
        // Since poll doesn't abort upon closing the socket,
        // let's simply poll on a frequent basis
        if(pollTime > junixsocket_use_poll_interval_millis) {
            pollTime = junixsocket_use_poll_interval_millis;
        }
#  endif

#  if defined(_WIN32)
        ret = WSAPoll(fds, 1, pollTime);
#  else
        ret = poll(fds, 1, pollTime);
#  endif

        if(ret == 1) {
            if((pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) == 0) {
                break;
            } else {
                // timeout
                return 0;
            }
        }
        int errnum = socket_errno;
        if(clock_gettime(CLOCK_MONOTONIC, &timeEnd) == -1) {
            return -1;
        }
        int elapsed = (int)(timespecToMillis(&timeEnd) - timespecToMillis(&timeStart));
        if(elapsed <= 0) {
            elapsed = 1;
        }
        millisRemaining -= elapsed;
        if(millisRemaining <= 0) {
            // timeout
            return 0;
        }

        if(ret == -1) {
            if(errnum == EAGAIN) {
                // try again
                continue;
            }

            if(errnum == ETIMEDOUT) {
                return 0;
            } else {
                return -1;
            }
        }
    }

    return 1;
}
#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    available
 * Signature: (Ljava/io/FileDescriptor;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_available(
                                                                             JNIEnv * env, jclass clazz CK_UNUSED, jobject fd)
{
    int handle = _getFD(env, fd);
    if (handle <= 0) {
        _throwException(env, kExceptionSocketException, "Socket closed");
        return 0;
    }

    // the following would actually block and keep the peek'ed byte in the buffer
    //ssize_t count = recv(handle, &buf, 1, MSG_PEEK);

    int ret;
#if defined(_WIN32)
    u_long count;
    ret = ioctlsocket(handle, FIONREAD, &count);
#else
    int count;
    ret = ioctl(handle, FIONREAD, &count);
#endif
    if((int)count == -1 || ret == -1) {
        _throwErrnumException(env, socket_errno, fd);
        return -1;
    }

    return count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    poll
 * Signature: (Lorg/newsclub/net/unix/AFUNIXSelector/PollFd;I)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_poll
(JNIEnv *env, jclass clazz CK_UNUSED, jobject pollFdObj, jint timeout) {
    if(pollFdObj == NULL) {
        return 0;
    }

    jobject fdsObj = (*env)->GetObjectField(env, pollFdObj, fieldID_fds);
    jsize nfds = (*env)->GetArrayLength(env, fdsObj);
    if(nfds == 0) {
        return 0;
    }

    jintArray opsObj = (*env)->GetObjectField(env, pollFdObj, fieldID_ops);
    jintArray ropsObj = (*env)->GetObjectField(env, pollFdObj, fieldID_rops);

    struct pollfd* pollFd = calloc(nfds, sizeof(struct pollfd));

    jint *buf = calloc(nfds, sizeof(jint));
    (*env)->GetIntArrayRegion(env, opsObj, 0, nfds, buf);
    for(int i=0;i<nfds;i++) {
        pollFd[i].events = opToEvent(buf[i]);
    }

    for(int i=0; i<nfds;i++) {
        jobject fdObj = (*env)->GetObjectArrayElement(env, fdsObj, i);
        int fd = _getFD(env, fdObj);
        pollFd[i].fd = fd;
    }

#if defined(_WIN32)
    int ret = WSAPoll(pollFd, nfds, timeout);
#else
    int ret = poll(pollFd, nfds, timeout);
#endif
    if(ret == -1) {
        ret = 0;
        _throwSockoptErrnumException(env, socket_errno, NULL);
        goto end;
    }

    for(int i=0; i<nfds;i++) {
        // FIXME check for POLLERR?
        buf[i] &= eventToOp(pollFd[i].revents);
    }
    (*env)->SetIntArrayRegion(env, ropsObj, 0, nfds, buf);

end:
    free(buf);
    free(pollFd);
    return ret;
}
