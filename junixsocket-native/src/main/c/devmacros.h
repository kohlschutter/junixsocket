//
//  devmacros.h
//  junixsocket-native
//
//  Created by Christian Kohlschütter on 4/4/21.
//

#ifndef devmacros_h
#define devmacros_h

#if __clang__
/**
 * Wrap code with CK_IGNORE_UNUSED_MACRO_BEGIN and CK_IGNORE_UNUSED_MACRO_END to
 * suppress "unused macro" warnings.
 */
#   define CK_IGNORE_UNUSED_MACRO_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunused-macros\"")
#   define CK_IGNORE_UNUSED_MACRO_END \
_Pragma("clang diagnostic pop")

/**
 * Wrap code with #if CK_EXCLUDED_FROM_STATIC_ANALYSIS and #endif
 * to exclude said portion from static analysis
 */
#   define CK_EXCLUDED_FROM_STATIC_ANALYSIS !(__clang_analyzer__)

#   define CK_STATIC_ASSERT(COND) _Static_assert(COND, "Assertion failed")

#else
#   define CK_IGNORE_UNUSED_MACRO_BEGIN
#   define CK_IGNORE_UNUSED_MACRO_END

#   define CK_EXCLUDED_FROM_STATIC_ANALYSIS 1

#   define CK_STATIC_ASSERT(COND)
#endif

#ifndef __unused
#define __unused __attribute__((__unused__))
#endif

#endif /* devmacros_h */
