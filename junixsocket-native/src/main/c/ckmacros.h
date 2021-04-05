//
//  devmacros.h
//  junixsocket-native
//

#ifndef devmacros_h
#define devmacros_h

#if __clang__
#   define CK_IGNORE_UNUSED_MACROS_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunused-macros\"")
#   define CK_IGNORE_UNUSED_MACROS_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_CAST_ALIGN_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wcast-align\"")
#   define CK_IGNORE_CAST_ALIGN_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_SIGN_COMPARE_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wsign-compare\"")
#   define CK_IGNORE_SIGN_COMPARE_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_CAST_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wint-to-pointer-cast\"") \
_Pragma("clang diagnostic ignored \"-Wpointer-to-int-cast\"")
#   define CK_IGNORE_CAST_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wused-but-marked-unused\"")
#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_UNUSED_FUNCTION_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunused-function\"")
#   define CK_IGNORE_UNUSED_FUNCTION_END \
_Pragma("clang diagnostic pop")

/**
 * Wrap code with #if CK_EXCLUDED_FROM_STATIC_ANALYSIS and #endif
 * to exclude said portion from static analysis
 */
#   define CK_EXCLUDED_FROM_STATIC_ANALYSIS !(__clang_analyzer__)

#   define CK_STATIC_ASSERT(COND) _Static_assert(COND, "Assertion failed")

#define CK_FALLTHROUGH \
    _Pragma("clang diagnostic push") \
    _Pragma("clang diagnostic ignored \"-Wmissing-declarations\"") \
    __attribute__((fallthrough)) \
    _Pragma("clang diagnostic pop")

#else
#   define CK_IGNORE_UNUSED_MACROS_BEGIN
#   define CK_IGNORE_UNUSED_MACROS_END

#   define CK_IGNORE_CAST_ALIGN_BEGIN
#   define CK_IGNORE_CAST_ALIGN_END

#   define CK_IGNORE_SIGN_COMPARE_BEGIN
#   define CK_IGNORE_SIGN_COMPARE_END

#   define CK_IGNORE_CAST_BEGIN
#   define CK_IGNORE_CAST_END

#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_BEGIN
#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_END

#   define CK_EXCLUDED_FROM_STATIC_ANALYSIS 1

#   define CK_STATIC_ASSERT(COND)

#define CK_FALLTHROUGH __attribute__((fallthrough))

#endif

#define CK_UNUSED __attribute__((__unused__))

/**
 * Marks arguments as "potentially unused" (depending on source configuration).
 */
#define CK_ARGUMENT_POTENTIALLY_UNUSED(X) (void)(X)

#endif /* devmacros_h */
