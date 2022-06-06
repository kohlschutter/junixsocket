//
//  pipe.c
//  junixsocket-native
//
//  Created by Christian Kohlschütter on 5/26/21.
//

#include "pipe.h"

#include "exceptions.h"
#include "filedescriptors.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    initPipe
 * Signature: (Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;Z)Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_initPipe
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fdSource, jobject fdSink, jboolean selectable) {
    int fildes[2] = {-1, -1};

     int ret;
#if defined(_WIN32)
     CK_ARGUMENT_POTENTIALLY_UNUSED(fildes);
     CK_ARGUMENT_POTENTIALLY_UNUSED(ret);

     if(selectable) {
         Java_org_newsclub_net_unix_NativeUnixSocket_socketPair
         (env, NULL, AF_UNIX, SOCK_STREAM, fdSource, fdSink);
         return true;
     } else {
         ret = _pipe((int*)&fildes, 256, O_BINARY | O_NOINHERIT);
     }
#elif defined(junixsocket_have_pipe2)
     CK_ARGUMENT_POTENTIALLY_UNUSED(selectable);

     ret = pipe2((int*)&fildes, O_CLOEXEC);
     if(ret == -1 && errno == EINVAL) {
         ret = pipe((int*)&fildes);
         if(ret == 0) {
#  if defined(FD_CLOEXEC)
             fcntl(fildes[0], F_SETFD, FD_CLOEXEC); // best effort
             fcntl(fildes[1], F_SETFD, FD_CLOEXEC); // best effort
#  endif
         }
     }
#else
     CK_ARGUMENT_POTENTIALLY_UNUSED(selectable);

     ret = pipe((int*)&fildes);
     if(ret == 0) {
#  if defined(FD_CLOEXEC)
         fcntl(fildes[0], F_SETFD, FD_CLOEXEC); // best effort
         fcntl(fildes[1], F_SETFD, FD_CLOEXEC); // best effort
#  endif
     }
#endif

    if(ret != 0) {
         _throwSockoptErrnumException(env,
#if defined(_WIN32)
                                      (selectable ? socket_errno : errno),
#else
                                      socket_errno,
#endif
                                      NULL);

         return false;
     }

     _initFD(env, fdSource, fildes[0]);
     _initFD(env, fdSink, fildes[1]);

#if defined(_WIN32)
     _initHandle(env, fdSource, (jlong)(HANDLE)_get_osfhandle(fildes[0]));
     _initHandle(env, fdSink, (jlong)(HANDLE)_get_osfhandle(fildes[1]));
#endif

     return false;
 }
