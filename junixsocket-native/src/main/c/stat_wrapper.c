//
//  stat_wrapper.c
//  junixsocket-native
//
//  Created by Christian Kohlschütter
//

#if __TOS_MVS__
//
#else
#if __has_include(<features.h>)
#  include <features.h>
#endif

#if __GLIBC__
#  include <sys/stat.h>
#  include "ckmacros.h"
#  include "stat_wrapper.h"

#  ifndef _STAT_VER
#    if defined(__aarch64__) || defined(__riscv) || defined(__loongarch64)
#        define _STAT_VER 0
#    elif defined(__x86_64__)
#        define _STAT_VER 1
#    else
#        define _STAT_VER 3
#    endif
#  endif

CK_IGNORE_RESERVED_IDENTIFIER_BEGIN
extern int __attribute__((weak)) __xstat (int __ver, const char *__filename,
                                          struct stat *__stat_buf) __THROW __nonnull ((2, 3));
extern int __attribute__((weak)) stat (const char *__filename,
                                       struct stat *__stat_buf) __THROW __nonnull ((1, 2));
CK_IGNORE_RESERVED_IDENTIFIER_END

int CK_VISIBILITY_INTERNAL ck_stat(const char *filename, struct stat *stat_buf) {
    if(__xstat) {
        return __xstat(_STAT_VER, filename, stat_buf);
    } else {
        return stat(filename, stat_buf);
    }
}
#endif
#endif
