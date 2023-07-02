/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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
package org.newsclub.net.unix;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

final class AncillaryDataSupport implements Closeable {
  private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
  private static final FileDescriptor[] NO_FILE_DESCRIPTORS = new FileDescriptor[0];

  private static final int MIN_ANCBUF_LEN = NativeUnixSocket.isLoaded() ? NativeUnixSocket
      .ancillaryBufMinLen() : 0;

  private final Map<FileDescriptor, Integer> openReceivedFileDescriptors = Collections
      .synchronizedMap(new HashMap<>());

  private final List<FileDescriptor[]> receivedFileDescriptors = Collections.synchronizedList(
      new LinkedList<>());

  // referenced from native code
  private ByteBuffer ancillaryReceiveBuffer = EMPTY_BUFFER;

  // referenced from native code
  @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  int[] pendingFileDescriptors = null;

  private int[] tipcErrorInfo = null;

  private int[] tipcDestName = null;

  // referenced from native code
  void setTipcErrorInfo(int errorCode, int dataLength) {
    if (errorCode == 0 && dataLength == 0) {
      tipcErrorInfo = null;
    } else {
      tipcErrorInfo = new int[] {errorCode, dataLength};
    }
  }

  int[] getTIPCErrorInfo() {
    int[] info = tipcErrorInfo;
    tipcErrorInfo = null;
    return info;
  }

  void setTipcDestName(int a, int b, int c) {
    if (a == 0 && b == 0 && c == 0) {
      this.tipcDestName = null;
    } else {
      this.tipcDestName = new int[] {a, b, c};
    }
  }

  int[] getTIPCDestName() {
    int[] addr = tipcDestName;
    tipcDestName = null;
    return addr;
  }

  int getAncillaryReceiveBufferSize() {
    return ancillaryReceiveBuffer.capacity();
  }

  void setAncillaryReceiveBufferSize(int size) {
    if (size == ancillaryReceiveBuffer.capacity()) {
      return;
    } else if (size <= 0) {
      this.ancillaryReceiveBuffer = EMPTY_BUFFER;
    } else {
      setAncillaryReceiveBufferSize0(Math.max(256, Math.min(MIN_ANCBUF_LEN, size)));
    }
  }

  void setAncillaryReceiveBufferSize0(int size) {
    this.ancillaryReceiveBuffer = ByteBuffer.allocateDirect(size);
  }

  public void ensureAncillaryReceiveBufferSize(int minSize) {
    if (minSize <= 0) {
      return;
    }
    if (ancillaryReceiveBuffer.capacity() < minSize) {
      setAncillaryReceiveBufferSize(minSize);
    }
  }

  // called from native code
  void receiveFileDescriptors(int[] fds) throws IOException {
    if (fds == null || fds.length == 0) {
      return;
    }
    final int fdsLength = fds.length;
    FileDescriptor[] descriptors = new FileDescriptor[fdsLength];
    for (int i = 0; i < fdsLength; i++) {
      final FileDescriptor fdesc = new FileDescriptor();
      NativeUnixSocket.initFD(fdesc, fds[i]);
      descriptors[i] = fdesc;

      openReceivedFileDescriptors.put(fdesc, fds[i]);

      final Closeable cleanup = new Closeable() {

        @Override
        public void close() throws IOException {
          openReceivedFileDescriptors.remove(fdesc);
        }
      };

      try {
        NativeUnixSocket.attachCloseable(fdesc, cleanup);
      } catch (SocketException e) {
        // ignore (cannot attach)
      }
    }

    this.receivedFileDescriptors.add(descriptors);
  }

  void clearReceivedFileDescriptors() {
    receivedFileDescriptors.clear();
  }

  FileDescriptor[] getReceivedFileDescriptors() {
    if (receivedFileDescriptors.isEmpty()) {
      return NO_FILE_DESCRIPTORS;
    }
    List<FileDescriptor[]> copy = new ArrayList<>(receivedFileDescriptors);
    if (copy.isEmpty()) {
      return NO_FILE_DESCRIPTORS;
    }
    receivedFileDescriptors.removeAll(copy);
    int count = 0;
    for (FileDescriptor[] fds : copy) {
      count += fds.length;
    }
    if (count == 0) {
      return NO_FILE_DESCRIPTORS;
    }
    FileDescriptor[] oneArray = new FileDescriptor[count];
    int offset = 0;
    for (FileDescriptor[] fds : copy) {
      System.arraycopy(fds, 0, oneArray, offset, fds.length);
      offset += fds.length;
    }
    return oneArray;
  }

  void setOutboundFileDescriptors(int[] fds) {
    this.pendingFileDescriptors = (fds == null || fds.length == 0) ? null : fds;
  }

  boolean hasOutboundFileDescriptors() {
    return this.pendingFileDescriptors != null;
  }

  void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    final int[] fds;
    if (fdescs == null || fdescs.length == 0) {
      fds = null;
    } else {
      final int numFdescs = fdescs.length;
      fds = new int[numFdescs];
      for (int i = 0; i < numFdescs; i++) {
        FileDescriptor fdesc = fdescs[i];
        fds[i] = NativeUnixSocket.getFD(fdesc);
      }
    }
    this.setOutboundFileDescriptors(fds);
  }

  @Override
  public void close() {
    synchronized (openReceivedFileDescriptors) {
      for (FileDescriptor desc : openReceivedFileDescriptors.keySet()) {
        if (desc.valid()) {
          try {
            NativeUnixSocket.close(desc);
          } catch (Exception e) {
            // ignore
          }
        }
      }
    }
  }
}
