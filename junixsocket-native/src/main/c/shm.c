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

// see shm_overview(7) https://man7.org/linux/man-pages/man7/shm_overview.7.html
// also: https://github.com/lassik/shm_open_anon
#include "shm.h"

#include "jniutil.h"
#include "exceptions.h"
#include "filedescriptors.h"

#include "logging.h"

#if defined(__linux)
#include <sys/syscall.h>
#endif

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

#if !defined(junixsocket_use_memfd_create)
CK_IGNORE_UNUSED_MACROS_BEGIN
#   define junixsocket_use_memfd_create 0
CK_IGNORE_UNUSED_MACROS_END
#endif

#if __TOS_MVS__
#   ifdef __SUSV3_XSI
#   else
#       define __SUSV3_XSI
#   endif
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

#if __TOS_MVS__
#   if MAP_ANONYMOUS
#   else
#       define MAP_ANONYMOUS 32 // https://www.ibm.com/docs/en/zos/3.1.0?topic=31-bpxycons-constants-used-by-services
#   endif
#elif defined(SHM_ANON)
//
#elif defined(OpenBSD)
//
#elif defined(__APPLE__)
#   undef MADV_PAGEOUT // internal-only (see sys/mman.h)
#elif __TOS_MVS__
#elif __has_include(<bsd/stdlib.h>)
#   include <bsd/stdlib.h>
#elif defined(_WIN32)

static PVOID WINAPI (*f_VirtualAlloc2)(HANDLE Process, PVOID BaseAddress, SIZE_T Size, ULONG AllocationType, ULONG PageProtection, MEM_EXTENDED_PARAMETER* ExtendedParameters, ULONG ParameterCount);
static PVOID WINAPI (*f_MapViewOfFile3)(HANDLE FileMapping, HANDLE Process, PVOID BaseAddress, ULONG64 Offset, SIZE_T ViewSize, ULONG AllocationType, ULONG PageProtection, MEM_EXTENDED_PARAMETER* ExtendedParameters, ULONG ParameterCount);

#elif defined(__ANDROID__)
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

__attribute((weak)) extern int memfd_create(const char *name, unsigned flags);
#if !defined(MFD_CLOEXEC)
// from include/uapi/linux/memfd.h
#   define MFD_CLOEXEC  0x0001U
#   define MFD_ALLOW_SEALING    0x0002U
#endif
#if !defined(MFD_NOEXEC_SEAL)
#   define MFD_NOEXEC_SEAL  0x0008U
#endif
#undef junixsocket_use_memfd_create
#define junixsocket_use_memfd_create    1

#if !defined(SYS_memfd_secret)
// from include/uapi/asm-generic/unistd.h
#   define SYS_memfd_secret 447
#endif

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

#if defined(_WIN32)

/*
 On Windows, there is apparently no way to get the actual size of a shared memory handle, so let's
 do a binary search: we know that the mapping will fail if the size is too large...
 */
static size_t determineShmSize(HANDLE handle, size_t min, size_t max) {
    size_t half = CK_round_page(((max - min) >> 1) + min);
    if(min >= half || max >= half) {
        return min;
    } else {
        void *addr = (void*)MapViewOfFile(handle,
                             (FILE_MAP_READ|FILE_MAP_WRITE), 0, 0, half);
        if(addr != NULL) {
            UnmapViewOfFile(addr);
            return determineShmSize(handle, half, max);
        } else {
            return determineShmSize(handle, min, half);
        }
    }
}
#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    shmUnlink
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_shmUnlink
(JNIEnv *env, CK_UNUSED jclass klazz, jstring nameStr) {
    char name[SHM_NAME_MAXLEN];
    if(jstring_to_char_if_possible(env, nameStr,
#if defined(_WIN32)
                                   1, // trim leading slash
#else
                                   0,
#endif
                                   name, sizeof(name)) == NULL) {
        throwIOErrnumException(env, EINVAL, NULL);
        return;
    }
#if defined(_WIN32)
    //
    HANDLE handle = OpenFileMapping((FILE_MAP_READ|FILE_MAP_WRITE), FALSE, name);
    if(handle != NULL) {
        size_t size = determineShmSize(handle, vm_page_size, CK_round_page((size_t)(SIZE_MAX >> 1)));

        void *addr = (void*)MapViewOfFile(handle,
                             (FILE_MAP_READ|FILE_MAP_WRITE), 0, 0, size);
        if(addr) {
            // sadly, it appears that on Windows we have to forcibly memset the entire memory region
            memset(addr, 0, size);
            DiscardVirtualMemory(addr, size);
            UnmapViewOfFile(addr);
        }
        CloseHandle(handle);
    }
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

#if junixsocket_use_memfd_create

// returns -1 for "error; try something else", and -2 for "error; don't bother trying...",
// values >= 0: the handle (success)
static inline int try_memfd_create(jint juxOpts) {
    if(memfd_create == NULL) {
        errno = ENOTSUP;
        return -1;
    }

    const jboolean sealing = (juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_SEALABLE);
    const jboolean secret = (juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_SECRET);
    if(secret) {
        if(sealing) {
            errno = ENOTSUP;
            return -1;
        }
        int handle = (int)syscall(SYS_memfd_secret, FD_CLOEXEC);
        if(handle < 0) {
            return -2;
        }
        return handle;
    }

    const int opts = MFD_CLOEXEC | (sealing ? MFD_ALLOW_SEALING : 0);

    int handle = memfd_create("junixsocket", opts | MFD_NOEXEC_SEAL);
    if(handle == -1 && errno == EINVAL) {
        handle = memfd_create("junixsocket", opts);
    }
    if(sealing && handle < 0) {
        return -2;
    } else {
        return handle;
    }
}

#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    shmOpen
 * Signature: (Ljava/io/FileDescriptor;Ljava/lang/String;JII)J
 */
JNIEXPORT jlong JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_shmOpen
(JNIEnv *env, CK_UNUSED jclass klazz, jobject targetFd, jstring nameStr, jlong truncateLen, jint mode, jint juxOpts) {
    char name[SHM_NAME_MAXLEN] = {0};
    if(nameStr == NULL) {
        // keep empty
    } else {
        if(jstring_to_char_if_possible(env, nameStr,
#if defined(_WIN32)
                                       1, // trim leading slash
#else
                                       0,
#endif
                                       name, sizeof(name)) == NULL) {
            throwIOErrnumException(env, EINVAL, NULL);
            return -1;
        }
    }

    if(truncateLen <= 0) {
        truncateLen = 1;
    }
    truncateLen = CK_round_page(truncateLen);

    int errnum = 0;
#if defined(_WIN32)

    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_SECRET) {
        throwIOErrnumException(env, ENOTSUP, NULL);
        return -1;
    }

    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_SEALABLE) {
        throwIOErrnumException(env, ENOTSUP, NULL);
        return -1;
    }

    jux_jlong_dword_t lenD = { .jlong = truncateLen };

    HANDLE handle;
    if((juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_CREAT) && (juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_EXCL)) {
        handle = CreateFileMapping
        (
         INVALID_HANDLE_VALUE,
         NULL,
         ((juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_RDONLY) ? PAGE_READONLY : PAGE_READWRITE),
         lenD.dwords.higher, lenD.dwords.lower, name);
    } else {
        if((juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_TRUNC)) {
            handle = NULL;
        } else {
            handle = OpenFileMapping
            (
             ((juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_RDONLY) ? FILE_MAP_READ : FILE_MAP_READ|FILE_MAP_WRITE),
             FALSE, name);
        }
        if(handle == NULL && (juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_CREAT)) {
            handle = CreateFileMapping
            (
             INVALID_HANDLE_VALUE,
             NULL,
             ((juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_RDONLY) ? PAGE_READONLY : PAGE_READWRITE),
             lenD.dwords.higher, lenD.dwords.lower, name);
        }
    }

    if(handle == NULL) {
        throwIOErrnumException(env, errnum ? errnum : io_errno, NULL);
        return -1;
    }

    goto handleOK;
#else // defined(_WIN32)
    int handle = -1;
#   if defined(__ANDROID__)
    CK_ARGUMENT_POTENTIALLY_UNUSED(mode);
    CK_ARGUMENT_POTENTIALLY_UNUSED(juxOpts);

#if junixsocket_use_memfd_create
    if(!*name) {
        handle = try_memfd_create(juxOpts);
        if(handle != -1) {
            goto haveHandle;
        }
    }
#endif
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
        return -1;
#endif
    }

    if(juxOpts & org_newsclub_net_unix_NativeUnixSocket_MOPT_SEALABLE) {
#if junixsocket_use_memfd_create
        if(memfd_create) {
            goto optsAfterSealable;
        }
#endif
        throwIOErrnumException(env, ENOTSUP, NULL);
        return -1;
    }
    goto optsAfterSealable;
optsAfterSealable:

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
            int errn = io_errno;
            if(handle == -1 && (errn == EEXIST || errn == EINVAL)) {
                // EINVAL: at least on macOS may indicate insufficient privileges to access
                continue;
            } else {
                break;
            }
        }
        if(handle >= 0) {
            if(shm_unlink(name) != 0) {
                // FIXME handle?
            }
        }
#       else

#if junixsocket_use_memfd_create
        handle = try_memfd_create(juxOpts);
        if(handle != -1) {
            goto haveHandle;
        }
#endif

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
            if(shm_unlink(name) != 0) {
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

#endif // defined(_WIN32)

    goto haveHandle;

haveHandle:
    if(handle < 0) {
        throwIOErrnumException(env, errnum ? errnum : io_errno, NULL);
        return -1;
    }
    goto handleOK;

handleOK:

#if defined(_WIN32)
    _initHandle(env, targetFd, (jlong)handle);
#else
    _initFD(env, targetFd, handle);
    if(truncateLen > 0) {
        int ret = ftruncate(handle, (off_t)truncateLen);
        if(ret < 0) {
            if(errno == EINVAL) {
                // ftruncate usually only works once on an shm object
                // on macOS, this is documented:
                // see https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/bsd/kern/posix_shm.c#L524-L525
                // on Linux, this is observed (also see https://man7.org/linux/man-pages/man3/ftruncate.3p.html that claims it works)
                return -1;
            }
            throwIOErrnumException(env, errno, NULL);
            return -1;
        }
    }
#endif

    return truncateLen;
}

void init_shm(JNIEnv *env) {
#if defined(_WIN32)
    SYSTEM_INFO system_info;
    GetNativeSystemInfo(&system_info);
    vm_page_size = system_info.dwAllocationGranularity;
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

#if defined(_WIN32)
    HMODULE lib = LoadLibrary("KernelBase.dll"); // Windows 10
    if(lib) {
        f_VirtualAlloc2 = (PVOID WINAPI (*)(HANDLE Process, PVOID BaseAddress, SIZE_T Size, ULONG AllocationType, ULONG PageProtection, MEM_EXTENDED_PARAMETER* ExtendedParameters, ULONG ParameterCount))GetProcAddress(lib, "VirtualAlloc2");
        f_MapViewOfFile3 = (PVOID WINAPI (*)(HANDLE FileMapping, HANDLE Process, PVOID BaseAddress, ULONG64 Offset, SIZE_T ViewSize, ULONG AllocationType, ULONG PageProtection, MEM_EXTENDED_PARAMETER* ExtendedParameters, ULONG ParameterCount))GetProcAddress(lib, "MapViewOfFile3");
    } else {
        f_VirtualAlloc2 = NULL;
        f_MapViewOfFile3 = NULL;
    }
#endif

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

    if(length > jux_SIZE_MAX || length < 0) {
        _throwException(env, kExceptionIOException, "length");
        return NULL;
    }

    if(offset < 0) {
        _throwException(env, kExceptionIOException, "offset");
        return NULL;
    }

    jboolean haveFd = true;

#if defined(_WIN32)
    HANDLE handle = ((HANDLE)_getHandle(env, fd));

    if(handle == NULL || handle == INVALID_HANDLE_VALUE) {
        jint fdHandle = _getFD(env, fd);
        if(fdHandle == -1) {
            _throwException(env, kExceptionClosedChannelException, "Channel is closed");
            return NULL;
        }
        handle = (HANDLE)_get_osfhandle(fdHandle);
    } else {
        haveFd = false;
    }

    if(handle == NULL || handle == INVALID_HANDLE_VALUE) {
        _throwException(env, kExceptionClosedChannelException, "Channel is closed");
        return NULL;
    }

    jux_jlong_dword_t offsetD = { .jlong = offset };

    void *addr;
    if(duplicates == 0) {
        addr = MapViewOfFile(handle,
                             (
                              ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_READ) ? FILE_MAP_READ : 0)
                              | ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_WRITE) ? FILE_MAP_WRITE : 0)
                              | ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_COPY_ON_WRITE) ? FILE_MAP_COPY : 0)
                              ),
                             offsetD.dwords.higher, offsetD.dwords.lower, length);
    } else {
        if(f_VirtualAlloc2 == NULL || f_MapViewOfFile3 == NULL) {
            throwIOErrnumException(env, ENOTSUP, NULL); // Windows 8
            return NULL;
        }
        length = CK_round_page(length);

        jlong copies = duplicates + 1;
        size_t totalLen = CK_round_page((size_t)(length * copies));

        int access;
        if((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_READ)) {
            if((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_WRITE)) {
                if((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_COPY_ON_WRITE)) {
                    access = PAGE_WRITECOPY;
                } else {
                    access = PAGE_READWRITE;
                }
            } else {
                access = PAGE_READONLY;
            }
        } else {
            access = PAGE_NOACCESS;
        }

        addr = f_VirtualAlloc2(NULL, NULL, totalLen, (MEM_RESERVE | MEM_RESERVE_PLACEHOLDER), PAGE_NOACCESS, NULL, 0);
        if(addr == NULL) {
            throwIOErrnumException(env, io_errno, NULL);
            return NULL;
        }

        for(int i=0; i<copies; i++) {
            void *sliceAddr = (void*) ((uint64_t)addr + i * length);

            if(i < (copies - 1) && !VirtualFree(sliceAddr, length, (MEM_RELEASE | MEM_PRESERVE_PLACEHOLDER))) {
                throwIOErrnumException(env, io_errno, NULL);
                return NULL;
            }

            void* actualAddr = f_MapViewOfFile3
            (handle, NULL, sliceAddr, offset, length, MEM_REPLACE_PLACEHOLDER, access, NULL, 0);
            if(actualAddr == NULL) {
                throwIOErrnumException(env, io_errno, NULL);
                return NULL;
            } else if(actualAddr != sliceAddr) {
                _throwException(env, kExceptionIOException, "mmap-slice");
                return NULL;
            }
        }

        length = (jlong)totalLen;
    }

    if(addr == NULL) {
        throwIOErrnumException(env, io_errno, NULL);
        return NULL;
    }

#else // defined(_WIN32)

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
#   if defined(MAP_SHARED_VALIDATE)
                       ((mmode & org_newsclub_net_unix_NativeUnixSocket_MMODE_SYNC) ? MAP_SHARED_VALIDATE : MAP_SHARED)
#   else
                       MAP_SHARED
#   endif
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
#   if defined(MAP_FIXED_NOREPLACE)
                                    MAP_FIXED_NOREPLACE
#   else
                                    MAP_FIXED
#   endif
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
#endif

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
    if(kMappedByteBufferFieldFd && haveFd) {
        (*env)->SetObjectField(env, dbb, kMappedByteBufferFieldFd, fd);
    }

#if defined(_WIN32)
#else
    // Set MappedByteBuffer.isSync only when necessary, since it's false by default
    if((flags & MAP_SYNC) && kMappedByteBufferFieldIsSync) {
        (*env)->SetBooleanField(env, dbb, kMappedByteBufferFieldIsSync, JNI_TRUE);
    }
#endif

    // Buffer.segment
    if(kBufferFieldSegment) {
        (*env)->SetObjectField(env, dbb, kBufferFieldSegment, arenaSegment);
    }

    return dbb;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    unmap
 * Signature: (JJIZ)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_unmap
        (JNIEnv *env, CK_UNUSED jclass klazz, jlong addr, jlong length, jint duplicates, jboolean ignoreError) {
#if defined(_WIN32)

    if(duplicates == 0) {
        if(!UnmapViewOfFile((void*)addr)) {
            if(!ignoreError) {
                throwIOErrnumException(env, io_errno, NULL);
                return;
            }
        }
        VirtualFree((void*)addr, 0, MEM_RELEASE);
        VirtualAlloc((void*)addr, length, MEM_RESET, PAGE_NOACCESS);
    } else {
        jlong sliceLength = length / (duplicates + 1);
        for(jlong start = addr, end = addr + length; start < end; start += sliceLength) {
            if(!UnmapViewOfFile((void*)start)) {
                if(!ignoreError) {
                    throwIOErrnumException(env, io_errno, NULL);
                    return;
                }
            }
            VirtualFree((void*)start, 0, MEM_RELEASE);
            VirtualAlloc((void*)start, length, MEM_RESET, PAGE_NOACCESS);
        }
    }
#else
    CK_ARGUMENT_POTENTIALLY_UNUSED(duplicates);

    int ret = munmap((void*)addr, (size_t)length);
    if(ret != 0 && !ignoreError) {
        throwIOErrnumException(env, errno, NULL);
    }
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    sharedMemoryAllocationSize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_sharedMemoryAllocationSize
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
    int advice;
    switch(jmadv) {
        case org_newsclub_net_unix_NativeUnixSocket_MADV_FREE:
        case org_newsclub_net_unix_NativeUnixSocket_MADV_FREE_NOW:
            DiscardVirtualMemory((void*)addr, length); // warning: also clears backing storage!
            // VirtualUnlock(addr, length);
            return;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_NORMAL:
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_WILLNEED:
            WIN32_MEMORY_RANGE_ENTRY entry = {
                .VirtualAddress = (void*)addr,
                .NumberOfBytes = length
            };
            PrefetchVirtualMemory(GetCurrentProcess(), 1, &entry, 0);

            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_DONTNEED:
            // VirtualUnlock(addr, length);
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_SEQUENTIAL:
            break;
        case org_newsclub_net_unix_NativeUnixSocket_MADV_RANDOM:
            break;
        default:
            if(ignoreError) {
                return;
            }
            throwIOErrnumException(env, ENOTSUP, NULL);
            return;
    }
#elif __TOS_MVS__
    // no madvise on z/OS ...
    if(ignoreError) {
        return;
    }
    throwIOErrnumException(env, ENOTSUP, NULL);
    return;
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
            throwIOErrnumException(env, ENOTSUP, NULL);
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
    if(ignoreError) {
        return;
    }
    throwIOErrnumException(env, errno, NULL);
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    needToTrackSharedMemory
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_needToTrackSharedMemory
 (CK_UNUSED JNIEnv *env, CK_UNUSED jclass klazz) {
#if defined(_WIN32)
    return true;
#else
    return false;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    sizeOfSharedMemory
 * Signature: (Ljava/io/FileDescriptor;)J
 */
JNIEXPORT jlong JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_sizeOfSharedMemory
 (JNIEnv *env, CK_UNUSED jclass klazz, jobject fd) {
#if defined(_WIN32)
    HANDLE handle = ((HANDLE)_getHandle(env, fd));

    jlong size = -1;
    if(!GetFileSizeEx(handle, &size)) {
        return determineShmSize(handle, vm_page_size, CK_round_page((size_t)(SIZE_MAX >> 1)));
    }

    return size;
#else
    int handle = _getFD(env, fd);
    struct stat s;
    if(fstat(handle, &s) == -1) {
        throwIOErrnumException(env, io_errno, NULL);
        return -1;
    }
    return s.st_size;
#endif
}
