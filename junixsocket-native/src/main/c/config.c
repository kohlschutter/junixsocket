/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
#endif

#if defined(_WIN32)
// There's a problem with a missing symbol using mingw 12.0 from Homebrew
// Use mingw 11 for the time being:
//
// brew remove mingw-w64
// wget https://raw.githubusercontent.com/Homebrew/homebrew-core/0247512f8a852f36f14b11809ac08a402de1f9e5/Formula/m/mingw-w64.rb
// brew install ./mingw-w64.rb
//
// Temporary fix for mingw 12:
//FILE * __cdecl __imp___iob_func() {
//    return NULL;
//}

#endif

#if defined(_OS400)
// https://www.ibm.com/docs/en/i/7.5?topic=exceptions-error-conditions
int jux_mangleErrno(int err) {
    switch(err) {
        case 3417:
            return ECLOSED; // just in case IBM i PASE errno.h defines a different value for ECLOSED
        case 3418: // CP3418 Possible APAR condition or hardware failure
                   // https://www.ibm.com/docs/en/i/7.5?topic=ssw_ibm_i_75/apis/gpeern.htm
            return EINVAL;
        default:
            return err;
    }
}
#endif

#if defined(_WIN32)
int jux_mangleErrno(int err) {
    switch(err) {
        case WSAEWOULDBLOCK:
            return EWOULDBLOCK;
        case WSAEINPROGRESS:
            return EINPROGRESS;
        case WSAEALREADY:
            return EALREADY;
        case 232: // Windows may throw this error code. "Connection reset by peer"
        case WSAECONNRESET:
            return ECONNRESET;
        case WSAECONNABORTED:
            return ECONNABORTED;
        case WSAEISCONN:
            return EISCONN;
        case WSAENOTSOCK:
            return ENOTSOCK;
        case WSAETIMEDOUT:
            return ETIMEDOUT;
        default:
            return err;
    }
}
#endif
