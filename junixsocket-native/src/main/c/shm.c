//
//  shm.c
//  junixsocket-native
//
//  Created by Christian Kohlschütter on 25.11.24.
//

// see shm_overview(7) https://man7.org/linux/man-pages/man7/shm_overview.7.html
// also: https://github.com/lassik/shm_open_anon
#include "shm.h"

#include "jniutil.h"
#include "exceptions.h"
#include "filedescriptors.h"

static jclass kBufferClass;
static jfieldID kBufferFieldSegment;
static jclass kMappedByteBufferClass;
static jfieldID kMappedByteBufferFieldFd;
static jfieldID kMappedByteBufferFieldIsSync;

static int vm_page_size;
#define CK_trunc_page(x)    ((x) & (~(vm_page_size - 1)))
#define CK_round_page(x)    CK_trunc_page((x) + (vm_page_size - 1))

// FIXME check out fcntl F_GETPATH (not portable)
// on macOS, F_GETPATH returns errno=EBADF for shm fds

#if defined(__sun) || defined(__sun__)
extern int madvise(caddr_t, size_t, int);
#endif

#if defined(_WIN32)
//
#  define SHM_NAME_MAXLEN PATH_MAX
#else
#  include <sys/mman.h>
#  include <fcntl.h>

#  if defined(__ANDROID__)
#    define ASHMEM_NAME_LEN 256
/*
 * ashmem_create_region - creates a new ashmem region and returns the file
 * descriptor, or <0 on error
 *
 * `name' is an optional label to give the region (visible in /proc/pid/maps)
 * `size' is the size of the region, in page-aligned bytes
 */
int ashmem_create_region(const char *name, size_t size);

#    define SHM_NAME_MAXLEN ASHMEM_NAME_LEN
#  else
#    define SHM_NAME_MAXLEN PATH_MAX

#  endif
#endif /* shm_h */

#if defined(SHM_ANON)
//
#elif defined(OpenBSD)
//
#elif defined(__APPLE__)
#   undef MADV_PAGEOUT // internal-only (see sys/mman.h)
#elif __has_include(<bsd/stdlib.h>)
#   include <bsd/stdlib.h>
#   define junixsocket_have_arc4random 1
#elif defined(_WIN32)
//
#elif defined(__ANDROID__)
//
#else
//
#endif

#ifdef __linux__
// Older musl doesn't have it; see https://git.musl-libc.org/cgit/musl/commit/?id=9b57db3f958d9adc3b1c7371b5c6723aaee448b7
#   if !defined(MAP_SYNC)
#       define MAP_SYNC 0x80000
#   endif
#   if !defined(MAP_SHARED_VALIDATE)
#       define MAP_SHARED_VALIDATE  0x03
#   endif

#   if !defined(MADV_FREE)
#       define MADV_FREE    8
#   endif

#endif
#if !defined(MAP_SYNC)
#       define MAP_SYNC 0 // no effect unless defined elsewhere
#endif

#if defined(MADV_FREE_REUSE)
#   define jux_MADV_FREE_OR_DONTNEED MADV_FREE_REUSE
#elif defined(MADV_FREE)
#   define jux_MADV_FREE_OR_DONTNEED MADV_FREE
#else
#   define jux_MADV_FREE_OR_DONTNEED MADV_DONTNEED
#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    shmUnlink
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_shmUnlink
(JNIEnv *env, CK_UNUSED jclass klazz, jstring nameStr) {
    char name[SHM_NAME_MAXLEN];
    if(jstring_to_char_if_possible(env, nameStr, name, sizeof(name)) == NULL) {
        throwIOErrnumException(env, EINVAL, NULL);
        return;
    }
#if defined(_WIN32)
    //
#elif defined(__ANDROID__)
    // FIXME use ashmem (but not directly; use libcutils.so instead)
    // however https://lpc.events/event/2/contributions/227/attachments/51/58/08._ashmem_-_getting_it_out_of_staging.pdf
    // see https://android.googlesource.com/platform/system/core/+/062a488/include/utils/ashmem.h
    // see https://android.googlesource.com/platform/system/core/+/4f6e8d7a00cbeda1e70cc15be9c4af1018bdad53/libcutils/ashmem-dev.c
    // see https://github.com/SHallimani/Android-NDK-Examples
    // see https://yannik520.github.io/ashmem.html

    // don't throw -> no-op instead
    // throwIOErrnumException(env, ENOTSUP, NULL);
#else
    int ret = shm_unlink(name);
    if(ret != 0) {
        int errnum = errno;
        if(errnum == ENOENT) {
            // that's OK
        } else {
            throwIOErrnumException(env, errnum, NULL);
        }
    }
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    shmOpen
 * Signature: (Ljava/io/FileDescriptor;Ljava/lang/String;JII)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_shmOpen
(JNIEnv *env, CK_UNUSED jclass klazz, jobject targetFd, jstring nameStr, jlong truncateLen, jint mode, jint juxOpts) {
    char name[SHM_NAME_MAXLEN] = {0};
    if(nameStr == NULL) {
        // keep empty
    } else {
        if(jstring_to_char_if_possible(env, nameStr, name, sizeof(name)) == NULL) {
            throwIOErrnumException(env, EINVAL, NULL);
            return;
        }
    }

#if defined(_WIN32)
    CK_ARGUMENT_POTENTIALLY_UNUSED(truncateLen);
    CK_ARGUMENT_POTENTIALLY_UNUSED(mode);
    CK_ARGUMENT_POTENTIALLY_UNUSED(juxOpts);

    // FIXME
    throwIOErrnumException(env, ENOTSUP, NULL);
    return;
#else
    int handle = -1;
#   if defined(__ANDROID__)
    CK_ARGUMENT_POTENTIALLY_UNUSED(mode);
    CK_ARGUMENT_POTENTIALLY_UNUSED(juxOpts);

    //
    //    handle = ashmem_create_region(name, 0);
    // FIXME
    handle = -1;
    errno = ENOTSUP;

#   else

    int opts = 0;
    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_RDONLY) {
        opts |= O_RDONLY;
    } else {
        opts |= O_RDWR;
    }
    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_CREAT) {
        opts |= O_CREAT;
    }
    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_EXCL) {
        opts |= O_EXCL;
    }
    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_TRUNC) {
        opts |= O_TRUNC;
    }

    // FIXME MOPT_SEALABLE, MOPT_SECRET
    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_SECRET) {
#ifdef __linux__
#else
        throwIOErrnumException(env, ENOTSUP, NULL);
        return;
#endif
    }
    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_SEALABLE) {
#ifdef __linux__
#else
        throwIOErrnumException(env, ENOTSUP, NULL);
        return;
#endif
    }

    if(!*name) {
#       if defined(SHM_ANON)
        handle = shm_open(SHM_ANON, opts, mode); // FreeBSD
#       elif defined(OpenBSD)
        memcpy(name, "/jux.XXXXXXXX", sizeof("/jux.XXXXXXXX"));

        handle = shm_mkstemp(name);
        if(handle >= 0 -1) {
            if(shm_unlink(name) != 0) {
                // FIXME handle?
            }
        }
#       elif defined(__APPLE__)
        opts |= O_EXCL;
        for(int attempt=0;attempt<5;attempt++) {
            uint64_t num = arc4random();
            snprintf(name, 14, "/jux.%llX", (unsigned long long)num);
            handle = shm_open(name, opts, mode);
            int errnum = errno;
            if(handle == -1 && (errnum == EEXIST || errnum == EINVAL)) {
                // EINVAL: at least on macOS may indicate insufficient privileges to access
                continue;
            } else {
                break;
            }
        }
        if(handle >= 0) {
            if(shm_unlink(name) == 0) {
                // FIXME handle?
            }
        }
#       else
        opts |= (O_CREAT | O_EXCL);

        pid_t ppid = getppid();
        pid_t pid = getpid();
        int dummyFd = dup(0);

        int id = dummyFd;
        for(int attempt=0;attempt<10;attempt++) {
            snprintf(name, 31, "/jux.%x.%x.%x.%x", (unsigned)ppid, (unsigned)pid, (unsigned)++id, (unsigned)attempt);
            handle = shm_open(name, opts, mode);
            int errnum = errno;
            if(handle == -1 && (errnum == EEXIST || errnum == EINVAL)) {
                // EINVAL: at least on macOS may indicate insufficient privileges to access
                continue;
            } else {
                break;
            }
        }
        if(dummyFd > 0) {
            close(dummyFd);
        }
        if(handle >= 0) {
            if(shm_unlink(name) == 0) {
                // FIXME handle
            }
        }
#       endif
    } else {
        handle = shm_open(name, opts, mode);
        if(handle == -1) {
            if((errno == EINVAL || errno == EEXIST)
               && (opts & (O_CREAT|O_EXCL|O_TRUNC)) == (O_CREAT|O_TRUNC)) {
                // EINVAL: at least on macOS may indicate insufficient privileges to access
                // since we didn't ask for exclusive access but allowed truncation, unlink the entry and try again
                shm_unlink(name); // ignore error
                handle = shm_open(name, opts, mode);
            }
        }
    }
#   endif

    if(handle < 0) {
        throwIOErrnumException(env, errno, NULL);
        return;
    }

    _initFD(env, targetFd, handle);

    if(truncateLen > 0) {
        int ret = ftruncate(handle, (off_t)truncateLen);
        if(ret < 0) {
            if(errno == EINVAL) {
                // ftruncate usually only works once on an shm object
                // on macOS, this is documented:
                // see https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/bsd/kern/posix_shm.c#L524-L525
                // on Linux, this is observed (also see https://man7.org/linux/man-pages/man3/ftruncate.3p.html that claims it works)
                return;
            }
            throwIOErrnumException(env, errno, NULL);
            return;
        }
    }
#endif
}

void init_shm(JNIEnv *env) {
#if defined(_WIN32)
    SYSTEM_INFO system_info;
    GetNativeSystemInfo(&system_info);
    vm_page_size = system_info.dwPageSize;
#else
    vm_page_size = getpagesize();
#endif

    kBufferClass = findClassAndGlobalRef(env, "java/nio/Buffer");
    if(kBufferClass != NULL) {
        kBufferFieldSegment = (*env)->GetFieldID(env, kBufferClass, "segment", "Ljava/lang/foreign/MemorySegment;");
        if(kBufferFieldSegment == NULL) {
            // older type (JEP-370, Java 14+)
            kBufferFieldSegment = (*env)->GetFieldID(env, kBufferClass, "segment", "Ljdk/internal/access/foreign/MemorySegmentProxy;");
        }
    }

    kMappedByteBufferClass = findClassAndGlobalRef(env, "java/nio/MappedByteBuffer");
    if(kMappedByteBufferClass != NULL) {
        kMappedByteBufferFieldFd = (*env)->GetFieldID(env, kMappedByteBufferClass, "fd", "Ljava/io/FileDescriptor;");
        kMappedByteBufferFieldIsSync = (*env)->GetFieldID(env, kMappedByteBufferClass, "isSync", "Z");
    }

    (*env)->ExceptionClear(env);
}

void destroy_shm(JNIEnv *env) {
    releaseClassGlobalRef(env, kBufferClass);
    releaseClassGlobalRef(env, kMappedByteBufferClass);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    mmap
 * Signature: (Ljava/lang/Object;Ljava/io/FileDescriptor;JJII)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_mmap
 (JNIEnv *env, CK_UNUSED jclass klazz, jobject arenaSegment, jobject fd, jlong offset, jlong length, jint mmode, jint duplicates) {
#if defined(_WIN32)
    throwIOErrnumException(env, ENOTSUP, NULL); // FIXME
    return NULL;
#else
    int handle = _getFD(env, fd);
    if(handle < 0) {
        _throwException(env, kExceptionClosedChannelException, "Channel is closed");
        return NULL;
    }
    if(offset != CK_trunc_page(offset)) {
        _throwException(env, kExceptionIOException, "offset");
        return NULL;
    }

    const int prot = ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_READ) ? PROT_READ : 0)
    | ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_WRITE) ? PROT_WRITE : 0);
    const int flags = (mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_COPY_ON_WRITE ? MAP_PRIVATE :
#if defined(MAP_SHARED_VALIDATE)
                       ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_SYNC) ? MAP_SHARED_VALIDATE : MAP_SHARED)
#else
                       MAP_SHARED
#endif
                       )
    | ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_SYNC) ? MAP_SYNC : 0)
    ;

    // FIXME add support for MAP_NOCORE/MAP_CONCEAL, MAP_NOSYNC (NOTE: pre-allocate space using write, not ftruncate!)
    // MAP_LOCKED, MAP_POPULATE(FreeBSD:MAP_PREFAULT_READ)/MAP_NONBLOCK, MAP_NOCACHE
    // MAP_SYNC (DAX Linux)

    // FIXME check if MAP_HASSEMAPHORE is actually used/useful elsewhere

    // on MAP_POPULATE/MAP_PREFAULT_READ: see https://stackoverflow.com/questions/32049240/implementing-mmaps-map-populate-flag-in-windows-and-other-unices-than-linux
    // (if unavailable, use madvise with MADV_WILLNEED or PrefetchVirtualMemory on macOS)

    void *addr;
    if(duplicates) {
        // FIXME: check MAP_GUARD on FreeBSD

        length = CK_round_page(length);

        jlong copies = duplicates + 1;
        size_t totalLen = (size_t)(length * copies);
        addr = mmap(NULL, totalLen, prot, flags | MAP_ANONYMOUS, -1, (off_t)offset);
        if(addr == MAP_FAILED) {
            throwIOErrnumException(env, errno, NULL);
            return NULL;
        } else if(addr == NULL) {
            _throwException(env, kExceptionIOException, "mmap-anonymous");
            return NULL;
        }

        for(int i=0;i<copies;i++) {
            void* sliceAddr = (void*)((uint64_t)addr + i * length);
            void* actualAddr = mmap(sliceAddr, (size_t)length, prot, flags |
#if defined(MAP_FIXED_NOREPLACE)
                                    MAP_FIXED_NOREPLACE
#else
                                    MAP_FIXED
#endif
                                    , handle, (off_t)offset
                                    );
            if(actualAddr == MAP_FAILED) {
                throwIOErrnumException(env, errno, NULL);
                return NULL;
            } else if(actualAddr != sliceAddr) {
                _throwException(env, kExceptionIOException, "mmap-slice");
                return NULL;
            }
        }

        length = (jlong)totalLen;
    } else {
        // no duplicates

        addr = mmap(NULL, (size_t)length, prot, flags, handle, (off_t)offset);
        if(addr == MAP_FAILED) {
            throwIOErrnumException(env, errno, NULL);
            return NULL;
        } else if(addr == NULL) {
            _throwException(env, kExceptionIOException, "mmap");
            return NULL;
        }
    }

    // NewDirectByteBuffer is guaranteed to create a DirectByteBuffer class, which is a subclass of MappedByteBuffer/Buffer
    jobject dbb = (*env)->NewDirectByteBuffer(env, addr, length);
    if(dbb == NULL) {
        if(!((*env)->ExceptionCheck(env))) {
            _throwException(env, kExceptionIOException, "NewDirectByteBuffer");
            return NULL;
        }
    }

    // enable mmap-specific operations on MemorySegment (isMapped=true -> force(), load(), isLoaded(), unload())
    // MappedByteBuffer.fd
    if(kMappedByteBufferFieldFd) {
        (*env)->SetObjectField(env, dbb, kMappedByteBufferFieldFd, fd);
    }

    // Set MappedByteBuffer.isSync only when necessary, since it's false by default
    if((flags & MAP_SYNC) && kMappedByteBufferFieldIsSync) {
        (*env)->SetBooleanField(env, dbb, kMappedByteBufferFieldIsSync, JNI_TRUE);
    }

    // Buffer.segment
    if(kBufferFieldSegment) {
        (*env)->SetObjectField(env, dbb, kBufferFieldSegment, arenaSegment);
    }

    return dbb;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    unmap
 * Signature: (JJZ)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_unmap
(JNIEnv *env, CK_UNUSED jclass klazz, jlong addr, jlong length, jboolean ignoreError) {
#if defined(_WIN32)
    throwIOErrnumException(env, ENOTSUP, NULL); // FIXME
#else
    int ret = munmap((void*)addr, (size_t)length);
    if(ret != 0 && !ignoreError) {
        throwIOErrnumException(env, errno, NULL);
    }
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    pageSize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_pageSize
(CK_UNUSED JNIEnv *env, CK_UNUSED jclass klazz) {
    return vm_page_size;
}


// on MADV_FREE vs MADV_DONTNEED:
// https://github.com/JuliaLang/julia/issues/51086
// https://github.com/cockroachdb/cockroach/issues/83790

// on MADV_FREE vs MADV_FREE_REUSABLE (Darwin)
// https://github.com/golang/go/issues/29844

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    madvise
 * Signature: (JJIZ)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_madvise
(JNIEnv *env, CK_UNUSED jclass klazz, jlong addr, jlong length, jint jmadv, jboolean ignoreError) {
#if defined(_WIN32)
    throwIOErrnumException(env, ENOTSUP, NULL); // FIXME
#else

    int advice;
    switch(jmadv) {
        case org_newsclub_net_unix_NativeUnixSocket_MADV_FREE_NOW:
             mmap((void*)addr, (size_t)length, /*PROT_NONE*/(PROT_READ|PROT_WRITE), MAP_FIXED|MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
             advice = jux_MADV_FREE_OR_DONTNEED;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_FREE:
            advice = jux_MADV_FREE_OR_DONTNEED;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_NORMAL:
            advice = MADV_NORMAL;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_WILLNEED:
            advice = MADV_WILLNEED;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_DONTNEED:
            advice = MADV_DONTNEED;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_SEQUENTIAL:
            advice = MADV_SEQUENTIAL;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_RANDOM:
            advice = MADV_RANDOM;
            break;
        default:
            throwIOErrnumException(env, ENOTSUP, NULL); // FIXME
            return;
    }



    int ret = madvise((void*)addr, (size_t)length, advice);
    if(ret == 0 || ignoreError) {
        return;
    }

    if(errno == EINVAL) {
#if defined(MADV_FREE)
        // older Linux kernel; try again with MADV_DONTNEED
        if(advice == jux_MADV_FREE_OR_DONTNEED && advice != MADV_DONTNEED) {
            ret = madvise((void*)addr, (size_t)length, MADV_DONTNEED);
            if(ret == 0) {
                return;
            }
        }
#endif
    }
    throwIOErrnumException(env, errno, NULL);
#endif
}
