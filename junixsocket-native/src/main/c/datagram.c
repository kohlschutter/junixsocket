/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

#include "config.h"
#include "datagram.h"

#include "jniutil.h"
#include "exceptions.h"
#include "filedescriptors.h"
#include "receive.h"
#include "send.h"
#include "address.h"

static jclass class_DatagramPacket = NULL;
static jmethodID methodID_getData = NULL;
static jmethodID methodID_getOffset = NULL;
static jmethodID methodID_getLength = NULL;
static jmethodID methodID_setLength = NULL;
static jmethodID methodID_getAddress = NULL;
static jmethodID methodID_setAddress = NULL;

static jclass class_AFUNIXInetAddress = NULL;
static jmethodID methodID_fromBytes = NULL;
static jmethodID methodID_toBytes = NULL;

void init_datagram(JNIEnv *env) {
    class_DatagramPacket = findClassAndGlobalRef(env, "java/net/DatagramPacket");
    methodID_getData = (*env)->GetMethodID(env, class_DatagramPacket, "getData", "()[B");
    methodID_getOffset = (*env)->GetMethodID(env, class_DatagramPacket, "getOffset", "()I");
    methodID_getLength = (*env)->GetMethodID(env, class_DatagramPacket, "getLength", "()I");
    methodID_setLength = (*env)->GetMethodID(env, class_DatagramPacket, "setLength", "(I)V");
    methodID_getAddress = (*env)->GetMethodID(env, class_DatagramPacket, "getAddress", "()Ljava/net/InetAddress;");
    methodID_setAddress = (*env)->GetMethodID(env, class_DatagramPacket, "setAddress", "(Ljava/net/InetAddress;)V");

    class_AFUNIXInetAddress = findClassAndGlobalRef(env, "org/newsclub/net/unix/AFUNIXInetAddress");
    methodID_fromBytes = (*env)->GetStaticMethodID(env, class_AFUNIXInetAddress, "wrapAddress", "([B)Ljava/net/InetAddress;");
    methodID_toBytes = (*env)->GetStaticMethodID(env, class_AFUNIXInetAddress, "unwrapAddress", "(Ljava/net/InetAddress;)[B");
}
void destroy_datagram(JNIEnv *env) {
    releaseClassGlobalRef(env, class_DatagramPacket);
    methodID_getData = NULL;
    methodID_getOffset = NULL;
    methodID_getLength = NULL;
    methodID_setLength = NULL;
    methodID_getAddress = NULL;
    methodID_setAddress = NULL;
}

struct datagram_packet {
    jbyteArray dataArray;
    jint dataArrayLength;
    jint offset;
    jint length;
    int handle;
};

static struct datagram_packet unwrapDatagramPacket(JNIEnv *env, jobject fd, jobject dp) {
    jbyteArray dataArray = (jbyteArray)(*env)->CallObjectMethod(env, dp, methodID_getData);
    jint offset = (*env)->CallIntMethod(env, dp, methodID_getOffset);
    jint length = (*env)->CallIntMethod(env, dp, methodID_getLength);

    int handle = _getFD(env, fd);

    struct datagram_packet pkt = {
        .handle = handle,
        .dataArray = dataArray,
        .offset = offset,
        .length = length,
    };

    jsize dataArrayLen = (*env)->GetArrayLength(env, dataArray);
    if(offset < 0 || length < 0 || offset >= dataArrayLen || (offset+length) > dataArrayLen) {
        _throwException(env, kExceptionSocketException, "Illegal offset or length");
        pkt.handle = 0;
    } else {
        pkt.dataArrayLength = dataArrayLen;
    }

    return pkt;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    receiveDatagram
 * Signature: (Ljava/io/FileDescriptor;Ljava/net/DatagramPacket;ILorg/newsclub/net/unix/AncillaryDataSupport;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_receiveDatagram
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd, jobject dp, jint opt, jobject ancSupp) {

     struct datagram_packet pkt = unwrapDatagramPacket(env, fd, dp);
     if (pkt.handle <= 0) {
         return -1;
     }

#if defined(junixsocket_use_poll_for_read)
     int ret = pollWithTimeout(env, fd, pkt.handle, 0);
     if(ret < 1) {
         return 0;
     }
#endif

     jbyte *buf = malloc((size_t)pkt.length);
     if(buf == NULL) {
         return -1; // OOME
     }

     struct sockaddr_un senderBuf = {};
     socklen_t senderBufLen = sizeof(struct sockaddr_un);

     ssize_t count;

     count = recvmsg_wrapper(env, pkt.handle, buf, pkt.length, NULL, 0, &senderBuf, &senderBufLen, opt, ancSupp);

     // if we receive messages from an unbound socket, the "sender" may be just a bunch of zeros.
     jboolean allZeros = true;
     for (socklen_t i=0;i<senderBufLen;i++) {
         if(senderBuf.sun_path[i] != 0) {
             allZeros = false;
             break;
         }
     }

     if(count < 0) {
         // read(2) returns -1 on error. Java throws an Exception.
         count = -1;
         _throwErrnumException(env, errno, fd);
         goto end;
     } else if(count == 0) {
         // nothing
     } else {
         (*env)->SetByteArrayRegion(env, pkt.dataArray, pkt.offset, count, buf);
     }

     (*env)->CallVoidMethod(env, dp, methodID_setLength, count);

     jobject address;
     if(!allZeros) {
         jbyteArray byteArray = (*env)->NewByteArray(env, senderBufLen);
         (*env)->SetByteArrayRegion(env, byteArray, 0, senderBufLen, (jbyte*)senderBuf.sun_path);
         address = (*env)->CallStaticObjectMethod(env, class_AFUNIXInetAddress, methodID_fromBytes, byteArray);
         (*env)->DeleteLocalRef(env, byteArray);
     } else {
         address = NULL;
     }
     (*env)->CallVoidMethod(env, dp, methodID_setAddress, address);

 end:
     free(buf);

     return count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    sendDatagram
 * Signature: (Ljava/io/FileDescriptor;Ljava/net/DatagramPacket;Lorg/newsclub/net/unix/AncillaryDataSupport;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_sendDatagram
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd, jobject dp, jobject ancSupp) {
     struct datagram_packet pkt = unwrapDatagramPacket(env, fd, dp);
     if (pkt.handle <= 0) {
         return;
     }

     jbyte *buf = malloc((size_t)pkt.length);
     if(buf == NULL) {
         return; // OOME
     }

     (*env)->GetByteArrayRegion(env, pkt.dataArray, pkt.offset, pkt.length, buf);

     struct sockaddr_un *sendTo = NULL;
     socklen_t sendToLen = 0;

     ssize_t count;
     jobject address = (*env)->CallObjectMethod(env, dp, methodID_getAddress);
     if(address != NULL) {
         jbyteArray byteArray = (*env)->CallStaticObjectMethod(env, class_AFUNIXInetAddress, methodID_toBytes, address);
         if(byteArray == NULL) {
             if((*env)->ExceptionCheck(env)) {
                 count = 0;
                 goto end;
             }
         } else {
             sendTo = malloc(sizeof(struct sockaddr_un));
             sendToLen = initSu(env, sendTo, byteArray);
             if(sendToLen == 0) {
//                 count = -1;
//                 _throwException(env, kExceptionSocketException, "Illegal datagram address");
//                 goto end;
             }
         }
     }

     count = sendmsg_wrapper(env, pkt.handle, buf, pkt.length, sendTo, sendToLen, ancSupp);

 end:
     free(buf);
     free(sendTo);

     if(count == -1) {
         if(errno == EAGAIN || errno == EWOULDBLOCK) {
             return;
         }

         _throwErrnumException(env, errno, fd);
     }
}
