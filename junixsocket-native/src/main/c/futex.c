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

// futexes
// see https://en.wikipedia.org/wiki/Futex
// and https://outerproduct.net/futex-dictionary.html
// and https://shift.click/blog/futex-like-apis/
// also: http://locklessinc.com/articles/mutex_cv_futex/
// also: futex_waitv: https://docs.kernel.org/userspace-api/futex2.html
// apple: os_sync_wait_on_address.h; https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/ulock.h

#include "futex.h"

#include "jniutil.h"
#include "exceptions.h"

#if defined(__APPLE__)
// see <bsd/sys/ulock.h>, this is not public API
#   define UL_COMPARE_AND_WAIT_SHARED  3
#   define ULF_WAKE_ALL    0x00000100

CK_IGNORE_RESERVED_IDENTIFIER_BEGIN
__attribute__((weak_import)) extern int __ulock_wait(uint32_t operation, void *addr, uint64_t value, uint32_t timeout); // timeout is microseconds
__attribute__((weak_import)) extern int __ulock_wake(uint32_t operation, void *addr, uint64_t wake_value);
CK_IGNORE_RESERVED_IDENTIFIER_END

#   define USE_OS_SYNC_WAIT_ON_ADDRESS 1
// #    include <os/os_sync_wait_on_address.h>, this is public API but only since macOS 14.4
#   define OS_CLOCK_MACH_ABSOLUTE_TIME 32
#   define OS_SYNC_WAIT_ON_ADDRESS_SHARED 1
#   define OS_SYNC_WAKE_BY_ADDRESS_SHARED 1
__attribute__((weak_import)) extern int os_sync_wait_on_address  (void *addr, uint64_t value, size_t size, uint32_t flags);
__attribute__((weak_import)) extern int os_sync_wait_on_address_with_timeout(void *addr, uint64_t value, size_t size, uint32_t flags, uint32_t clockid, uint64_t timeout_ns);
__attribute__((weak_import)) extern int os_sync_wake_by_address_any(void *addr, size_t size, uint32_t flags);
__attribute__((weak_import)) extern int os_sync_wake_by_address_all(void *addr, size_t size, uint32_t flags);

#elif defined(__linux__)
#   include <linux/futex.h>      /* Definition of FUTEX_* constants */
#   include <sys/syscall.h>      /* Definition of SYS_* constants */
#   include <unistd.h>
#elif defined(_WIN32)

static VOID WINAPI (*f_WakeByAddressSingle)(PVOID Address);
static VOID WINAPI (*f_WakeByAddressAll)(PVOID Address);
static WINBOOL WINAPI (*f_WaitOnAddress)(volatile VOID *Address, PVOID CompareAddress, SIZE_T AddressSize, DWORD dwMilliseconds);

#endif


void init_futex(JNIEnv *env) {
#if defined(_WIN32)
    HMODULE lib = LoadLibrary("KernelBase.dll"); // Windows 10
    if(lib) {
        f_WakeByAddressSingle = (VOID WINAPI (*)(PVOID Address))GetProcAddress(lib, "WakeByAddressSingle");
        f_WakeByAddressAll = (VOID WINAPI (*)(PVOID Address))GetProcAddress(lib, "WakeByAddressAll");
        f_WaitOnAddress = (WINBOOL WINAPI (*)(volatile VOID *Address, PVOID CompareAddress, SIZE_T AddressSize, DWORD dwMilliseconds))GetProcAddress(lib, "WaitOnAddress");
    } else {
        f_WakeByAddressSingle = NULL;
        f_WakeByAddressAll = NULL;
        f_WaitOnAddress = NULL;
    }
#endif

    (*env)->ExceptionClear(env);
}

void destroy_futex(JNIEnv *env) {
    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    futexWait
 * Signature: (JII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_futexWait
(JNIEnv *env, CK_UNUSED jclass klazz, jlong addr, jint ifValue, jint timeoutMillis) {
#if defined(__APPLE__)
    int ret;
    if(USE_OS_SYNC_WAIT_ON_ADDRESS && os_sync_wait_on_address_with_timeout) {
        if(timeoutMillis == 0) {
            ret = os_sync_wait_on_address((void*)addr, (uint64_t)ifValue, 4, OS_SYNC_WAIT_ON_ADDRESS_SHARED);
        } else {
            ret = os_sync_wait_on_address_with_timeout((void*)addr, (uint64_t)ifValue, 4, OS_SYNC_WAIT_ON_ADDRESS_SHARED,
                                                       OS_CLOCK_MACH_ABSOLUTE_TIME, timeoutMillis * 1000 * 1000);
        }
    } else if(__ulock_wait) {
        ret = __ulock_wait(UL_COMPARE_AND_WAIT_SHARED, (void*)addr, (uint64_t)ifValue, timeoutMillis * 1000);
    } else {
        throwIOErrnumException(env, ENOTSUP, NULL);
        return false;
    }

    if(ret >= 0) {
        return true;
    } else if(ret == -ETIMEDOUT || errno == ETIMEDOUT) {
        // timeout
    } else if(errno == EAGAIN) { // not observed on macOS; just in case
        return true; // ifValue did not match
    } else {
        throwIOErrnumException(env, errno, NULL);
    }

#elif defined(__linux__)
    if(timeoutMillis == 0) {
        // specifying NULL would prevent the call from being interruptable
        // cf. https://outerproduct.net/futex-dictionary.html#linux
        timeoutMillis = INT_MAX; // a long time
    }

    struct timespec ts = {
        .tv_sec = timeoutMillis / 1000,
        .tv_nsec = (timeoutMillis % 1000) * 1000000
    };
    long ret = syscall(SYS_futex, (void*)addr, FUTEX_WAIT, ifValue, &ts, NULL, 0);

    if(ret == 0) {
        return true;
    } else if(ret > 0 || errno == ETIMEDOUT) {
        return false;
    } else if(errno == EAGAIN) {
        return true; // ifValue did not match
    }

    if(errno == ENOSYS) {
        throwIOErrnumException(env, ENOTSUP, NULL);
    } else {
        throwIOErrnumException(env, errno, NULL);
    }
    return false;

#else
#   if defined(_WIN32)
    if(f_WaitOnAddress) {
        if(f_WaitOnAddress((void*)addr, &ifValue, 4, timeoutMillis)) {
            return true;
        } else if(io_errno == 1460) { // ERROR_TIMEOUT
            return false;
        } else {
            throwIOErrnumException(env, io_errno, NULL);
        }
    }
#   endif
    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
    CK_ARGUMENT_POTENTIALLY_UNUSED(addr);
    CK_ARGUMENT_POTENTIALLY_UNUSED(ifValue);
    CK_ARGUMENT_POTENTIALLY_UNUSED(timeoutMillis);
    throwIOErrnumException(env, ENOTSUP, NULL);
#endif
    return false;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    futexWake
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_futexWake
 (JNIEnv *env, CK_UNUSED jclass klazz, jlong addr, jboolean wakeAll) {
#if defined(__APPLE__)
    int ret;
    if(wakeAll) {
        if(USE_OS_SYNC_WAIT_ON_ADDRESS && os_sync_wake_by_address_all) {
            ret = os_sync_wake_by_address_all((void*)addr, 4, OS_SYNC_WAKE_BY_ADDRESS_SHARED);
        } else if(__ulock_wake) {
            ret = __ulock_wake(UL_COMPARE_AND_WAIT_SHARED | ULF_WAKE_ALL, (void*)addr, 0);
        } else {
            throwIOErrnumException(env, ENOTSUP, NULL);
            return false;
        }
    } else {
        if(USE_OS_SYNC_WAIT_ON_ADDRESS && os_sync_wake_by_address_any) {
            ret = os_sync_wake_by_address_any((void*)addr, 4, OS_SYNC_WAKE_BY_ADDRESS_SHARED);
        } else if(__ulock_wake) {
            ret = __ulock_wake(UL_COMPARE_AND_WAIT_SHARED, (void*)addr, 0);
        } else {
            throwIOErrnumException(env, ENOTSUP, NULL);
            return false;
        }
    }

    if(ret >= 0) {
        return true;
    } else if(ret == -ENOENT || errno == ENOENT) {
        // none to wake up
        return false;
    } else {
        throwIOErrnumException(env, errno, NULL);
        return false;
    }
#elif defined(__linux__)
    long ret = syscall(SYS_futex, (void*)addr, FUTEX_WAKE, (wakeAll ? INT_MAX : 1), NULL, NULL, 0);
    if(ret == 0) {
        return false;
    } else if(ret > 0) {
        return true;
    }

    if(errno == ENOSYS) {
        throwIOErrnumException(env, ENOTSUP, NULL);
    } else {
        throwIOErrnumException(env, errno, NULL);
    }
    return false;
#else
#   if defined(_WIN32)
    if(wakeAll && f_WakeByAddressAll) {
        f_WakeByAddressAll((void*)addr);
        return false;
    } else if(f_WakeByAddressSingle) {
        f_WakeByAddressSingle((void*)addr);
        return false;
    }
#   endif

    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
    CK_ARGUMENT_POTENTIALLY_UNUSED(addr);
    CK_ARGUMENT_POTENTIALLY_UNUSED(wakeAll);
    throwIOErrnumException(env, ENOTSUP, NULL);
    return false;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    futexIsInterProcess
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_futexIsInterProcess
 (CK_UNUSED JNIEnv *env, CK_UNUSED  jclass klazz) {
#if defined(_WIN32)
    return false;
#else
    return true;
#endif
}
