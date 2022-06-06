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

#if defined(_WIN32) && junixsocket_clock_gettime_impl
CK_VISIBILITY_INTERNAL int clock_gettime(int ignored CK_UNUSED, struct timespec *spec) {
    __int64 time;
    GetSystemTimeAsFileTime((FILETIME*)&time);
    time -= 116444736000000000LL; // EPOCHFILETIME
    spec->tv_sec = time / 10000000LL;
    spec->tv_nsec = time % 10000000LL * 100;
    return 0;
}
# endif

#if defined(_OS400)
int jux_mangleErrno(int err) {
    switch(err) {
        case 3418: // CP3418 Possible APAR condition or hardware failure
                   // https://www.ibm.com/docs/en/i/7.5?topic=ssw_ibm_i_75/apis/gpeern.htm
            return EINVAL;
        default:
            return err;
    }
}
#endif
