/*
 * junixsocket
 *
 * Copyright 2009-2026 Christian Kohlschütter
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

#ifndef polling_with_deadline_h
#define polling_with_deadline_h

#include "config.h"

#if __TOS_MVS__
#include "gettod_zos.h"
#endif

static int pollMonotonicTime(struct timespec* out) {
#if __TOS_MVS__
    return gettimeofdayMonotonic(out);
#else
    return clock_gettime(CLOCK_MONOTONIC, out);
#endif
}

static uint64_t pollTimespecToMillis(const struct timespec* ts) {
    return (uint64_t)ts->tv_sec * 1000 + (uint64_t)ts->tv_nsec / 1000000;
}

#if !defined(_WIN32)

static int pollWithDeadline(struct pollfd* pollFd, nfds_t nfds, int timeout) {
    uint64_t deadlineMillis = 0;

    if(timeout > 0) {
        struct timespec now;
        if(pollMonotonicTime(&now) == -1) {
            return -1;
        }
        deadlineMillis = pollTimespecToMillis(&now) + (uint64_t)timeout;
    }

    for(;;) {
        int ret = poll(pollFd, nfds, timeout);
        if(ret != -1 || socket_errno != EINTR) {
            return ret;
        }

        if(timeout > 0) {
            struct timespec now;
            if(pollMonotonicTime(&now) == -1) {
                return -1;
            }

            uint64_t nowMillis = pollTimespecToMillis(&now);
            if(nowMillis >= deadlineMillis) {
                return 0;
            }
            timeout = (int)(deadlineMillis - nowMillis);
        }
    }
}

#endif /* !_WIN32 */

#endif /* polling_with_deadline_h */
