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
     int fildes[2];

     int ret;
#if defined(_WIN32)
     CK_ARGUMENT_POTENTIALLY_UNUSED(fildes);
     CK_ARGUMENT_POTENTIALLY_UNUSED(ret);
     if(selectable) {
         Java_org_newsclub_net_unix_NativeUnixSocket_socketPair
         (env, NULL, SOCK_STREAM, fdSource, fdSink);
         return true;
     } else {
         ret = _pipe((int*)&fildes, 256, O_BINARY | O_NOINHERIT);
     }
#else
     CK_ARGUMENT_POTENTIALLY_UNUSED(selectable);

     ret = pipe((int*)&fildes);
#endif
     if(ret != 0) {
         _throwSockoptErrnumException(env, socket_errno, NULL);
         return false;
     }

     _initFD(env, fdSource, fildes[0]);
     _initFD(env, fdSink, fildes[1]);

     return false;
 }
