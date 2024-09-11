//
//  stat_wrapper.h
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
int ck_stat(const char *__filename, struct stat *__stat_buf) __THROW __nonnull ((1, 2));
#endif

#endif
