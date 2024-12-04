//
//  futex.c
//  junixsocket-native
//
//  Created by Christian Kohlschütter on 01.12.24.
//

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
#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    futexWait
 * Signature: (JII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_futexWait
(JNIEnv *env, CK_UNUSED jclass klazz, jlong addr, jint ifValue, jint timeoutMillis) {
#if defined(__APPLE__)
    int ret;
    if(os_sync_wait_on_address_with_timeout && USE_OS_SYNC_WAIT_ON_ADDRESS) {
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
        if(os_sync_wake_by_address_all && USE_OS_SYNC_WAIT_ON_ADDRESS) {
            ret = os_sync_wake_by_address_all((void*)addr, 4, OS_SYNC_WAKE_BY_ADDRESS_SHARED);
        } else if(__ulock_wake) {
            ret = __ulock_wake(UL_COMPARE_AND_WAIT_SHARED | ULF_WAKE_ALL, (void*)addr, 0);
        } else {
            throwIOErrnumException(env, ENOTSUP, NULL);
            return false;
        }
    } else {
        if(os_sync_wake_by_address_any && USE_OS_SYNC_WAIT_ON_ADDRESS) {
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
    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
    CK_ARGUMENT_POTENTIALLY_UNUSED(addr);
    CK_ARGUMENT_POTENTIALLY_UNUSED(wakeAll);
    throwIOErrnumException(env, ENOTSUP, NULL);
    return false;
#endif
}
